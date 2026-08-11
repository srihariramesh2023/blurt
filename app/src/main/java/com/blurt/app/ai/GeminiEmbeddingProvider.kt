package com.blurt.app.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini embedding provider over the plain REST API. The Android SDK doesn't
 * ship embedding support, so we POST directly to the public endpoint — no
 * extra dependencies, and the free tier (API key from AI Studio) is plenty
 * for a personal note library.
 *
 * Model: gemini-embedding-001 (3072 dims, 100 texts per batch) — the current
 * free-tier embedding model; the old text-embedding-004 was shut down in
 * January 2026.
 */
class GeminiEmbeddingProvider(
    private val apiKey: String,
    private val packageName: String,
    private val certSha1: String,
) : EmbeddingProvider {

    override suspend fun embed(texts: List<String>): List<FloatArray>? = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        try {
            val vectors = mutableListOf<FloatArray>()
            // batchEmbedContents accepts up to 100 requests per call.
            texts.chunked(BATCH_SIZE).forEach { chunk ->
                vectors += postBatch(chunk, taskType = "RETRIEVAL_DOCUMENT")
            }
            vectors
        } catch (_: Exception) {
            null
        }
    }

    /** One vector for the query text, embedded with the query task type. */
    suspend fun embedQuery(text: String): FloatArray? = withContext(Dispatchers.IO) {
        try {
            postBatch(listOf(text), taskType = "RETRIEVAL_QUERY").firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun postBatch(texts: List<String>, taskType: String): List<FloatArray> {
        val body = JSONObject()
            .put(
                "requests",
                JSONArray().also { arr ->
                    texts.forEach { text ->
                        arr.put(
                            JSONObject()
                                .put("model", "models/$MODEL")
                                .put(
                                    "content",
                                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text))),
                                )
                                .put("taskType", taskType)
                        )
                    }
                },
            )

        val connection = URL("$ENDPOINT/$MODEL:batchEmbedContents").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", apiKey)
            // Standard Android-app key restriction headers: when the key is
            // limited to this package + signing-certificate SHA-1 in Google
            // Cloud, Google validates these against the restriction. Derived
            // from the APK's own signer at runtime, so debug and release
            // builds each present the cert they were actually signed with.
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
            return parseEmbeddings(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEmbeddings(raw: String): List<FloatArray> {
        val root = JSONObject(raw)
        val list = root.optJSONArray("embeddings") ?: JSONArray()
        val out = mutableListOf<FloatArray>()
        for (i in 0 until list.length()) {
            val values = list.getJSONObject(i).getJSONArray("values")
            out += FloatArray(values.length()) { values.getDouble(it).toFloat() }
        }
        return out
    }

    companion object {
        const val MODEL = "gemini-embedding-001"
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val BATCH_SIZE = 100
    }
}
