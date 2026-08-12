package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
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
    private val apiKey: String,
    private val packageName: String,
    private val certSha1: String,
) : CaptureAnalyzer {

    override suspend fun analyze(content: String, nowEpochMillis: Long): CaptureAnalysis? =
        withContext(Dispatchers.IO) {
            if (content.isBlank()) return@withContext null
            try {
                val raw = post(content, nowEpochMillis)
                parseAnalysis(raw)
            } catch (_: Exception) {
                null
            }
        }

    private fun post(content: String, nowEpochMillis: Long): String {
        val now = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowEpochMillis), ZoneId.systemDefault())
        val categories = CaptureCategory.entries.joinToString(", ") { it.name }
        val prompt = buildString {
            append("You are the Blurt capture analyzer. Classify a user's blurt into exactly one ")
            append("category from this fixed list: $categories. ")
            append("The current date and time is ${now} (the user's local time). ")
            append("If the blurt mentions a specific date or time (for example \"tomorrow at 3pm\", ")
            append("\"next Monday morning\", \"in 2 hours\", \"Friday at 6pm\"), return it as an ")
            append("ISO-8601 timestamp with timezone offset, resolved against the current time. ")
            append("Otherwise return null for reminderAt. Ignore vague references like \"later\" or ")
            append("\"someday\" — only concrete times count. Never invent a time that isn't mentioned. ")
            append("Blurt: \"$content\"")
        }

        val schema = JSONObject()
            .put(
                "type", "OBJECT",
            )
            .put(
                "properties", JSONObject()
                    .put(
                        "category", JSONObject()
                            .put("type", "STRING")
                            .put("enum", JSONArray().also { arr -> CaptureCategory.entries.forEach { arr.put(it.name) } })
                    )
                    .put("reminderAt", JSONObject().put("type", "STRING").put("nullable", true))
            )
            .put("required", JSONArray().put("category").put("reminderAt"))

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
                    .put("maxOutputTokens", 1024),
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
        const val MODEL = "gemini-3.5-flash"
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

        /**
         * Extracts the analysis from a generateContent response. Pure and
         * unit-tested — the network and the parsing stay separate.
         */
        fun parseAnalysis(raw: String): CaptureAnalysis? {
            return try {
                val root = JSONObject(raw)
                val candidates = root.optJSONArray("candidates") ?: return null
                if (candidates.length() == 0) return null
                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    ?: return null
                if (parts.length() == 0) return null
                val jsonText = parts.getJSONObject(0).optString("text").trim()
                val analysis = JSONObject(jsonText)
                val category = runCatching { CaptureCategory.valueOf(analysis.optString("category", "")) }
                    .getOrNull() ?: return null
                val reminder = analysis.optString("reminderAt", "null")
                val reminderAt = if (reminder.isBlank() || reminder == "null") null
                else parseIsoTime(reminder) ?: return null
                CaptureAnalysis(category = category, reminderAt = reminderAt)
            } catch (_: Exception) {
                // Malformed/garbage responses are "no analysis" — never a crash.
                null
            }
        }

        private fun parseIsoTime(value: String): Long? {
            val text = value.trim()
            return try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // A naive timestamp ("2026-08-12T15:00:00") is assumed to be
                // the device's local time.
                try {
                    java.time.LocalDateTime.parse(text)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }
}
