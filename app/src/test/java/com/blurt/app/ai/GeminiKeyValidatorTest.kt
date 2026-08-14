package com.blurt.app.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The live Gemini key probe: every HTTP outcome lands in exactly one bucket. */
class GeminiKeyValidatorTest {

    private val validator = GeminiKeyValidator()

    @Test
    fun acceptedKeyIsValid() {
        assertEquals(GeminiKeyStatus.VALID, validator.statusFor(200))
        assertEquals(GeminiKeyStatus.VALID, validator.statusFor(299))
    }

    @Test
    fun rejectedKeyIsInvalid() {
        // Gemini returns 400 (API_KEY_INVALID) and 403 (key blocked / wrong
        // Android-app restriction) for unusable keys; 401 as a defensive net.
        assertEquals(GeminiKeyStatus.INVALID, validator.statusFor(400))
        assertEquals(GeminiKeyStatus.INVALID, validator.statusFor(401))
        assertEquals(GeminiKeyStatus.INVALID, validator.statusFor(403))
    }

    @Test
    fun everythingElseIsUnreachable() {
        assertEquals(GeminiKeyStatus.UNREACHABLE, validator.statusFor(429))
        assertEquals(GeminiKeyStatus.UNREACHABLE, validator.statusFor(500))
        assertEquals(GeminiKeyStatus.UNREACHABLE, validator.statusFor(0))
        assertEquals(GeminiKeyStatus.UNREACHABLE, validator.statusFor(-1))
    }

    @Test
    fun blankKeyNeverHitsTheNetwork() = runTest {
        assertEquals(GeminiKeyStatus.UNREACHABLE, validator.validate(""))
        assertEquals(GeminiKeyStatus.UNREACHABLE, validator.validate("   "))
    }
}
