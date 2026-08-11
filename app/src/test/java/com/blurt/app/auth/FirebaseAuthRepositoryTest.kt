package com.blurt.app.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * In unit tests there is no google-services.json and no Firebase app, so the
 * repository must degrade to a clear, non-crashing "not configured" state —
 * exactly what local builds and CI experience without the config file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseAuthRepositoryTest {

    private fun repository(): FirebaseAuthRepository =
        FirebaseAuthRepository(RuntimeEnvironment.getApplication())

    @Test
    fun unconfigured_isReportedAndStateIsSignedOut() {
        val repo = repository()

        assertFalse(repo.isConfigured)
        assertEquals(AuthState.SignedOut, repo.authState.value)
    }

    @Test
    fun unconfigured_startSignInReturnsNull() {
        assertNull(repository().startSignIn())
    }

    @Test
    fun unconfigured_completeSignInReturnsFriendlyError() = runTest {
        val result = repository().completeSignIn(android.app.Activity.RESULT_OK, null)

        assertTrue(result is SignInResult.Error)
        val message = (result as SignInResult.Error).message
        assertTrue(message.contains("sign-in isn't set up"))
        // Never leak a raw backend error to the user.
        assertFalse(message.contains("Firebase"))
    }
}
