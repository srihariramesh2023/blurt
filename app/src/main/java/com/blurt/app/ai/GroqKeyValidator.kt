package com.blurt.app.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of probing a Groq key against the API. */
enum class GroqKeyStatus {
    /** The key authenticated successfully. */
    VALID,

    /** Groq rejected the key outright (401/403). */
    INVALID,

    /** Couldn't reach Groq or the server errored — the key may be fine. */
    UNREACHABLE,
}

/**
 * Live check for a user-pasted Groq key: a single authenticated GET against
 * the models endpoint, which is the cheapest call Groq offers and needs no
 * tokens. The mapping from HTTP code to status is pure and unit-tested; the
 * network call itself stays off the main thread.
 */
class GroqKeyValidator {

    suspend fun validate(apiKey: String): GroqKeyStatus = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext GroqKeyStatus.UNREACHABLE
        val code = runCatching { probe(apiKey) }.getOrDefault(-1)
        statusFor(code)
    }

    /** Pure mapping — every HTTP outcome lands in exactly one bucket. */
    internal fun statusFor(code: Int): GroqKeyStatus = when {
        code in 200..299 -> GroqKeyStatus.VALID
        code == 401 || code == 403 -> GroqKeyStatus.INVALID
        else -> GroqKeyStatus.UNREACHABLE
    }

    private fun probe(apiKey: String): Int {
        val connection = URL(MODELS_ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MODELS_ENDPOINT = "https://api.groq.com/openai/v1/models"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
