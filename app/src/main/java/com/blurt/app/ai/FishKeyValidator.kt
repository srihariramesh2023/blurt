package com.blurt.app.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Result of probing a Fish Audio key against the API. */
enum class FishKeyStatus {
    /** The key authenticated successfully. */
    VALID,

    /** Fish rejected the key outright (401/403). */
    INVALID,

    /** Couldn't reach Fish or the server errored — the key may be fine. */
    UNREACHABLE,
}

/**
 * Live check for a user-pasted Fish Audio key: a tiny one-word synthesis
 * request (about a credit) against the free-tier TTS API. The mapping from
 * HTTP code to status is pure and unit-tested; the call stays off the main
 * thread.
 */
class FishKeyValidator {

    suspend fun validate(apiKey: String): FishKeyStatus = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext FishKeyStatus.UNREACHABLE
        val code = runCatching { probe(apiKey) }.getOrDefault(-1)
        statusFor(code)
    }

    /** Pure mapping — every HTTP outcome lands in exactly one bucket. */
    internal fun statusFor(code: Int): FishKeyStatus = when {
        code in 200..299 -> FishKeyStatus.VALID
        code == 401 || code == 403 -> FishKeyStatus.INVALID
        else -> FishKeyStatus.UNREACHABLE
    }

    private fun probe(apiKey: String): Int {
        val body = JSONObject()
            .put("text", "hi")
            .put("reference_id", FISH_VOICE_ID)
            .put("format", "pcm")
            .put("sample_rate", 24_000)
            .put("latency", "low")
        val connection = URL(FISH_ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("model", FISH_MODEL)
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val FISH_ENDPOINT = "https://api.fish.audio/v1/tts"
        const val FISH_MODEL = "s2.1-pro-free"
        const val FISH_VOICE_ID = "bf322df2096a46f18c579d0baa36f41d"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 10_000
    }
}