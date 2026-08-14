package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini capture analyzer over the plain REST API — same free-tier setup as
 * the embedding provider (API key from AI Studio, Android-app restriction
 * headers so a shared APK can't leak the key's use).
 *
 * One generateContent call per save assigns the fixed category and extracts a
 * concrete time ("tomorrow at 3pm") when one is mentioned. Response JSON
 * schema keeps the model honest: it can only emit a category from the fixed
 * list and an ISO-8601 timestamp (or null).
 */
class GeminiCaptureAnalyzer(
    /**
     * Resolves the API key at call time (never at construction), so a key the
     * user pastes in the app takes effect on the very next analysis without
     * rebuilding the container. Returning null skips the call entirely.
     */
    private val apiKeyProvider: () -> String?,
    private val packageName: String,
    private val certSha1: String,
) : CaptureAnalyzer {

    override suspend fun analyze(content: String, nowEpochMillis: Long): CaptureAnalysis? =
        withContext(Dispatchers.IO) {
            if (content.isBlank()) return@withContext null
            val apiKey = apiKeyProvider() ?: return@withContext null
            try {
                val raw = post(content, nowEpochMillis, apiKey)
                parseAnalysis(raw)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "analyze failed: ${e::class.simpleName}: ${e.message}")
                null
            }
        }

    private fun post(content: String, nowEpochMillis: Long, apiKey: String): String {
        val now = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowEpochMillis), ZoneId.systemDefault())
        val categories = CaptureCategory.entries.joinToString(", ") { it.name }
        val intents = CaptureIntent.entries.joinToString(", ") { it.name }
        val prompt = buildString {
            append("You are the Blurt capture analyzer. Read a user's blurt and decide what it is. ")
            append("Pick exactly one intent from this fixed list: $intents. ")
            append("NOTE is a passing thought or note to remember; TASK is something to do; ")
            append("IDEA is a thought worth keeping like a suggestion or concept; REMINDER is an ")
            append("explicit \"remind me\" type request (keep REMINDER even when no exact time is given). ")
            append("Also pick exactly one category from this fixed list: $categories. ")
            append("The current date and time is ${now} (the user's local time). ")
            append("If the blurt mentions a specific date or time (for example \"tomorrow at 3pm\", ")
            append("\"next Monday morning\", \"in 2 hours\", \"Friday at 6pm\"), return it as an ")
            append("ISO-8601 timestamp with timezone offset, resolved against the current time. ")
            append("Otherwise return null for reminderAt. Ignore vague references like \"later\" or ")
            append("\"someday\" — only concrete times count. Never invent a time that isn't mentioned. ")
            append("Set important to true only when the user emphasizes importance (\"don't forget\", ")
            append("\"important\", \"make sure\", \"remember this\", \"priority\"); otherwise false. ")
            append("Blurt: \"$content\"")
        }

        val schema = JSONObject()
            .put(
                "type", "OBJECT",
            )
            .put(
                "properties", JSONObject()
                    .put(
                        "intent", JSONObject()
                            .put("type", "STRING")
                            .put("enum", JSONArray().also { arr -> CaptureIntent.entries.forEach { arr.put(it.name) } })
                    )
                    .put(
                        "category", JSONObject()
                            .put("type", "STRING")
                            .put("enum", JSONArray().also { arr -> CaptureCategory.entries.forEach { arr.put(it.name) } })
                    )
                    .put("reminderAt", JSONObject().put("type", "STRING").put("nullable", true))
                    .put("important", JSONObject().put("type", "BOOLEAN"))
            )
            .put("required", JSONArray().put("intent").put("category").put("reminderAt").put("important"))

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", schema)
                    .put("temperature", 0.0)
                    // Generous on purpose: the flash model's internal thinking
                    // counts toward this budget, and a truncated JSON is
                    // treated as "no analysis" — quiet but wrong.
                    .put("maxOutputTokens", 4096),
            )

        val connection = URL("$ENDPOINT/$MODEL:generateContent").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", apiKey)
            // Same Android-app restriction headers as the embedding provider:
            // when the key is restricted to this package + signing cert, Google
            // validates these on every call.
            if (packageName.isNotBlank() && certSha1.isNotBlank()) {
                connection.setRequestProperty("X-Android-Package", packageName)
                connection.setRequestProperty("X-Android-Cert", certSha1)
            }
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Gemini $code: ${raw.take(200)}")
            }
            return raw
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "BlurtGemini"
        const val MODEL = "gemini-3.5-flash"
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

        /**
         * Extracts the analysis from a generateContent response. The inner
         * JSON is parsed by the shared [CaptureAnalysisParser]; this only
         * unwraps the Gemini envelope. Pure and unit-tested.
         */
        fun parseAnalysis(raw: String): CaptureAnalysis? {
            return try {
                val root = JSONObject(raw)
                val candidates = root.optJSONArray("candidates") ?: return null
                if (candidates.length() == 0) return null
                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    ?: return null
                if (parts.length() == 0) return null
                CaptureAnalysisParser.parse(parts.getJSONObject(0).optString("text"))
            } catch (_: Exception) {
                // Malformed/garbage responses are "no analysis" — never a crash.
                null
            }
        }
    }
}
