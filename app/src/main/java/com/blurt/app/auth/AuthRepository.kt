package com.blurt.app.auth

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/** Outcome of a Google sign-in attempt, mapped to Blurt's tone. */
sealed interface SignInResult {
    data object Success : SignInResult

    /** The user dismissed the Google account chooser. Not an error. */
    data object Cancelled : SignInResult

    /** A user-presentable failure message — never a raw backend error. */
    data class Error(val message: String) : SignInResult
}

/**
 * Authentication boundary. UI observes [authState] (an ongoing listener, not a
 * one-shot check) and calls [startSignIn] / [completeSignIn] / [signOut].
 *
 * The Firebase implementation is backed by Firebase Auth + Google Sign-In.
 */
interface AuthRepository {

    /** Whether Google/Firebase sign-in is configured on this build. */
    val isConfigured: Boolean

    /** Persistent session state, updated live by the auth listener. */
    val authState: StateFlow<AuthState>

    /**
     * The intent that opens Google's account chooser. Returns null when
     * sign-in is not configured (missing google-services.json).
     */
    fun startSignIn(): Intent?

    /** Handles the chooser's result and exchanges the Google ID token for a Firebase session. */
    suspend fun completeSignIn(resultCode: Int, data: Intent?): SignInResult

    /** Ends the Firebase session; [authState] flips to [AuthState.SignedOut]. */
    suspend fun signOut()
}
