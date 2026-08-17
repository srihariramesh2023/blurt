package com.blurt.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted storage for user-supplied AI API keys (BYOK): a Groq key for
 * capture classification and a Gemini key for classification fallback plus
 * semantic-search embeddings. Each key lives in the Android Keystore — it
 * never leaves the device and cannot be exported — while the ciphertext sits
 * in ordinary SharedPreferences.
 *
 * Encryption is AES-256-GCM with a fresh random IV per write; a stored blob
 * is `base64(iv || ciphertext)`. A corrupt or undecryptable blob (device
 * restore across Keystore boundaries, tampering) degrades to "no key" rather
 * than crashing, so the app stays fully usable — just unclassified.
 */
class AiKeyStore(
    context: Context,
    /**
     * Where the AES key comes from. Production uses a key generated inside the
     * Android Keystore (never exportable); tests inject a plain JCE key so the
     * encryption round-trip is testable without an emulator.
     */
    private val keyProvider: () -> SecretKey = { androidKeystoreKey() },
) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Groq (capture classification) --------------------------------------

    /** The user's Groq key, or null when none is saved (or undecryptable). */
    fun groqKey(): String? = readKey(PREF_GROQ_KEY)

    fun saveGroqKey(key: String) = writeKey(PREF_GROQ_KEY, key)

    fun clearGroqKey() = clearKey(PREF_GROQ_KEY)

    // --- Gemini (classification fallback + semantic search) ------------------

    /** The user's Gemini key, or null when none is saved (or undecryptable). */
    fun geminiKey(): String? = readKey(PREF_GEMINI_KEY)

    fun saveGeminiKey(key: String) = writeKey(PREF_GEMINI_KEY, key)

    fun clearGeminiKey() = clearKey(PREF_GEMINI_KEY)

    // --- Fish (companion voice) ----------------------------------------------

    /** The user's Fish Audio key, or null when none is saved (or undecryptable). */
    fun fishKey(): String? = readKey(PREF_FISH_KEY)

    fun saveFishKey(key: String) = writeKey(PREF_FISH_KEY, key)

    fun clearFishKey() = clearKey(PREF_FISH_KEY)

    // --- Shared encrypted read/write -----------------------------------------

    private fun readKey(pref: String): String? {
        val blob = prefs.getString(pref, null) ?: return null
        return try {
            val decoded = Base64.decode(blob, Base64.NO_WRAP)
            val iv = decoded.copyOfRange(0, IV_LENGTH)
            val data = decoded.copyOfRange(IV_LENGTH, decoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8).ifBlank { null }
        } catch (e: Exception) {
            // Keystore entry lost or blob corrupted — treat as "no key".
            null
        }
    }

    private fun writeKey(pref: String, key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            clearKey(pref)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(pref, blob).apply()
    }

    private fun clearKey(pref: String) {
        prefs.edit().remove(pref).apply()
    }

    companion object {
        /** A key generated inside the Android Keystore — never exportable. */
        private fun androidKeystoreKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            return generator.generateKey()
        }

        private const val PREFS_NAME = "blurt_ai_keys"
        private const val PREF_GROQ_KEY = "groq_api_key"
        private const val PREF_GEMINI_KEY = "gemini_api_key"
        private const val PREF_FISH_KEY = "fish_api_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "blurt_groq_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
    }
}
