package com.blurt.app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Google Sign-In backed by Firebase Auth.
 *
 * Session persistence is handled entirely by Firebase: the token is stored
 * securely and restored on launch, and [authState] is driven by an ongoing
 * [FirebaseAuth.addAuthStateListener] rather than a one-shot check.
 *
 * Configuration: Firebase is enabled when `app/google-services.json` exists
 * (applied by the google-services Gradle plugin). Without it the repository
 * degrades to a clear "not configured" state instead of crashing, so local
 * builds and CI without the file stay green.
 */
class FirebaseAuthRepository(context: Context) : AuthRepository {

    private val appContext = context.applicationContext

    override val isConfigured: Boolean =
        FirebaseApp.getApps(appContext).isNotEmpty()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val auth: FirebaseAuth?
        get() = if (isConfigured) FirebaseAuth.getInstance() else null

    init {
        if (isConfigured) {
            // The listener fires immediately with the current session, so the
            // initial Loading state resolves in the same frame.
            auth?.addAuthStateListener(::onAuthStateChanged)
        } else {
            _authState.value = AuthState.SignedOut
        }
    }

    override fun startSignIn(): Intent? {
        val clientId = webClientId() ?: return null
        if (!isConfigured) return null
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(appContext, options).signInIntent
    }

    override suspend fun completeSignIn(resultCode: Int, data: Intent?): SignInResult {
        if (!isConfigured) {
            return SignInResult.Error("Blurt sign-in isn't set up on this build yet.")
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            // User dismissed the account chooser.
            return SignInResult.Cancelled
        }
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken == null) {
                SignInResult.Error("Google didn't return a sign-in token. Try again.")
            } else {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth?.signInWithCredential(credential)?.await()
                SignInResult.Success
            }
        } catch (e: ApiException) {
            when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> SignInResult.Cancelled
                GoogleSignInStatusCodes.SIGN_IN_FAILED ->
                    SignInResult.Error("Couldn't reach Google. Check your connection and try again.")

                else -> SignInResult.Error("Couldn't sign in. Please try again.")
            }
        } catch (e: Exception) {
            SignInResult.Error("Couldn't sign in. Check your connection and try again.")
        }
    }

    override suspend fun signOut() {
        auth?.signOut()
        // Also clear the GoogleSignIn client's remembered account, otherwise
        // the next sign-in silently re-attaches the previous account instead
        // of showing the account chooser.
        if (isConfigured) {
            val clientId = webClientId() ?: return
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build()
            runCatching { GoogleSignIn.getClient(appContext, options).signOut().await() }
        }
    }

    private fun onAuthStateChanged(firebaseAuth: FirebaseAuth) {
        val user = firebaseAuth.currentUser
        _authState.value = user?.toAuthUser()?.let { AuthState.SignedIn(it) }
            ?: AuthState.SignedOut
    }

    /**
     * The OAuth web client ID the google-services plugin generates into app
     * resources. Read by name so the code compiles whether or not
     * google-services.json is present.
     */
    private fun webClientId(): String? {
        val resources = appContext.resources
        val id = resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        return if (id != 0) resources.getString(id) else null
    }
}

private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
    uid = uid,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl?.toString(),
)
