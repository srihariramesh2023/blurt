package com.blurt.app.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The live key probe: every HTTP outcome lands in exactly one bucket. */
class GroqKeyValidatorTest {

    private val validator = GroqKeyValidator()

    @Test
    fun acceptedKeyIsValid() {
        assertEquals(GroqKeyStatus.VALID, validator.statusFor(200))
        assertEquals(GroqKeyStatus.VALID, validator.statusFor(299))
    }

    @Test
    fun rejectedKeyIsInvalid() {
        assertEquals(GroqKeyStatus.INVALID, validator.statusFor(401))
        assertEquals(GroqKeyStatus.INVALID, validator.statusFor(403))
    }

    @Test
    fun everythingElseIsUnreachable() {
        assertEquals(GroqKeyStatus.UNREACHABLE, validator.statusFor(429))
        assertEquals(GroqKeyStatus.UNREACHABLE, validator.statusFor(500))
        assertEquals(GroqKeyStatus.UNREACHABLE, validator.statusFor(0))
        assertEquals(GroqKeyStatus.UNREACHABLE, validator.statusFor(-1))
    }

    @Test
    fun blankKeyNeverHitsTheNetwork() = runTest {
        assertEquals(GroqKeyStatus.UNREACHABLE, validator.validate(""))
        assertEquals(GroqKeyStatus.UNREACHABLE, validator.validate("   "))
    }
}
