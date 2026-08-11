package com.blurt.app.ui.login

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blurt.app.BlurtApp
import com.blurt.app.auth.AuthRepository
import com.blurt.app.auth.SignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * The Google account chooser intent, or null when sign-in isn't
     * configured on this build (missing google-services.json).
     */
    fun signInIntent(): Intent? {
        _error.value = null
        return authRepository.startSignIn()?.also { _isSigningIn.value = true }
            ?: run {
                _error.value = "Blurt sign-in isn't set up on this build yet."
                null
            }
    }

    /** Called with the chooser's result; exchanges it for a Firebase session. */
    fun onSignInResult(resultCode: Int, data: Intent?) {
        viewModelScope.launch {
            val result = authRepository.completeSignIn(resultCode, data)
            when (result) {
                is SignInResult.Success -> Unit // authState flips to SignedIn; the gate switches screens
                is SignInResult.Cancelled -> _error.value = null // quiet — the user chose not to
                is SignInResult.Error -> _error.value = result.message
            }
            _isSigningIn.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                LoginViewModel(app.container.authRepository)
            }
        }
    }
}
