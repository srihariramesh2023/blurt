package com.blurt.app.ai

import android.content.Context
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The BYOK store: encrypted round-trip + independent Groq/Gemini storage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiKeyStoreTest {

    // --- Encrypted storage round-trip ----------------------------------------

    /** A plain JCE key stands in for the Keystore one (Robolectric has no
     *  AndroidKeyStore provider; the Keystore path is device-verified). */
    private fun testStore(context: Context): AiKeyStore {
        val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return AiKeyStore(context, keyProvider = { key })
    }

    @Test
    fun saveAndReadBackTheKey() {
        val store = testStore(RuntimeEnvironment.getApplication())
        store.saveGroqKey("gsk_abcdef123456")
        assertEquals("gsk_abcdef123456", store.groqKey())
        store.clearGroqKey()
        assertNull(store.groqKey())
    }

    @Test
    fun ciphertextIsNotThePlaintextKey() {
        val context = RuntimeEnvironment.getApplication()
        val store = testStore(context)
        store.saveGroqKey("gsk_topsecret")
        val raw = context.getSharedPreferences("blurt_ai_keys", Context.MODE_PRIVATE)
            .getString("groq_api_key", null)
        assertNotNull(raw)
        // The stored blob must be base64(iv||ciphertext), never the raw key.
        assertEquals(false, raw!!.contains("gsk_topsecret"))
    }

    // --- Gemini encrypted storage --------------------------------------------

    @Test
    fun saveAndReadBackTheGeminiKey() {
        val store = testStore(RuntimeEnvironment.getApplication())
        store.saveGeminiKey("AIzaSyabcdef123456")
        assertEquals("AIzaSyabcdef123456", store.geminiKey())
        store.clearGeminiKey()
        assertNull(store.geminiKey())
    }

    @Test
    fun geminiCiphertextIsNotThePlaintextKey() {
        val context = RuntimeEnvironment.getApplication()
        val store = testStore(context)
        store.saveGeminiKey("AIzaSyTopSecretKey")
        val raw = context.getSharedPreferences("blurt_ai_keys", Context.MODE_PRIVATE)
            .getString("gemini_api_key", null)
        assertNotNull(raw)
        assertEquals(false, raw!!.contains("AIzaSyTopSecretKey"))
    }

    @Test
    fun groqAndGeminiKeysAreStoredIndependently() {
        val store = testStore(RuntimeEnvironment.getApplication())
        store.saveGroqKey("gsk_groq_only")
        store.saveGeminiKey("AIza_gemini_only")
        assertEquals("gsk_groq_only", store.groqKey())
        assertEquals("AIza_gemini_only", store.geminiKey())
        store.clearGeminiKey()
        // Clearing Gemini must not touch the Groq key.
        assertEquals("gsk_groq_only", store.groqKey())
        assertNull(store.geminiKey())
    }
}
