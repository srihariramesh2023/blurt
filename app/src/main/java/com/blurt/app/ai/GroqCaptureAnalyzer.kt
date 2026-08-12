package com.blurt.app.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Groq capture analyzer over the OpenAI-compatible chat completions API —
 * the same classification prompt as the Gemini analyzer, but served by
 * Groq's fast inference with a much larger free daily quota (~14k requests),
 * so the voice-flow's per-save analysis and the background backfill never
 * come close to a daily cap.
 *
 * JSON mode (`response_format: json_object`) keeps the model honest; the
 * inner JSON is parsed by the shared [CaptureAnalysisParser] so both
 * providers produce identical behavior.
 */
class GroqCaptureAnalyzer(
    private val apiKey: String,
    private val model: String,
) : CaptureAnalyzer {

    override suspend fun analyze(content: String, nowEpochMillis: Long): CaptureAnalysis? =
        withContext(Dispatchers.IO) {
            if (content.isBlank()) return@withContext null
            try {
                val raw = post(content, nowEpochMillis)
                val jsonText = extractContent(raw) ?: return@withContext null
                CaptureAnalysisParser.parse(jsonText)
            } catch (_: Exception) {
                null
            }
        }

    private fun post(content: String, nowEpochMillis: Long): String {
        val prompt = AnalysisPrompt.build(content, nowEpochMillis)

        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", prompt)
                ),
            )
            .put("temperature", 0.0)
            .put("max_tokens", 4096)
            .put(
                "response_format",
                JSONObject().put("type", "json_object"),
            )

        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Groq $code: ${raw.take(200)}")
            }
            return raw
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

        /**
         * Unwraps the Groq chat-completions envelope to the inner JSON text.
         * Pure and unit-tested — the network and the parsing stay separate.
         */
        fun extractContent(raw: String): String? {
            return try {
                val root = JSONObject(raw)
                val choices = root.optJSONArray("choices") ?: return null
                if (choices.length() == 0) return null
                val content = choices.getJSONObject(0).optJSONObject("message")?.optString("content")
                    ?: return null
                content.trim().ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }
    }
}
