package com.blurt.app.auth

/**
 * The authentication state of the app.
 *
 * [Loading] is shown only while the persisted session is being restored, so a
 * returning signed-in user never sees the login screen flash.
 */
sealed interface AuthState {
    /** Restoring the persisted session (Firebase restores it asynchronously). */
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val user: AuthUser) : AuthState
}
