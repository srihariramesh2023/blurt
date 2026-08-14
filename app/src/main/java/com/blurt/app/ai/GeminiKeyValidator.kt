package com.blurt.app.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of probing a Gemini key against the API. */
enum class GeminiKeyStatus {
    /** The key authenticated successfully. */
    VALID,

    /** Google rejected the key outright (bad key, restricted elsewhere). */
    INVALID,

    /** Couldn't reach Google or the server errored — the key may be fine. */
    UNREACHABLE,
}

/**
 * Live check for a user-pasted Gemini key: a single authenticated GET against
 * the models-list endpoint, which costs nothing and needs no tokens. When the
 * key is restricted to this app (package + signing cert), the same
 * X-Android-Package / X-Android-Cert headers the analyzers send are included
 * so a restricted key validates exactly like it will in production.
 */
class GeminiKeyValidator(
    private val packageName: String = "",
    private val certSha1: String = "",
) {

    suspend fun validate(apiKey: String): GeminiKeyStatus = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext GeminiKeyStatus.UNREACHABLE
        val code = runCatching { probe(apiKey) }.getOrDefault(-1)
        statusFor(code)
    }

    /** Pure mapping — every HTTP outcome lands in exactly one bucket. */
    internal fun statusFor(code: Int): GeminiKeyStatus = when {
        code in 200..299 -> GeminiKeyStatus.VALID
        code == 400 || code == 401 || code == 403 -> GeminiKeyStatus.INVALID
        else -> GeminiKeyStatus.UNREACHABLE
    }

    private fun probe(apiKey: String): Int {
        val connection = URL(MODELS_ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("x-goog-api-key", apiKey)
            if (packageName.isNotBlank() && certSha1.isNotBlank()) {
                connection.setRequestProperty("X-Android-Package", packageName)
                connection.setRequestProperty("X-Android-Cert", certSha1)
            }
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MODELS_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
