package com.blurt.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blurt.app.BlurtApp
import com.blurt.app.auth.AuthState
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.model.Capture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    repository: CaptureRepository,
    authState: StateFlow<AuthState>,
) : ViewModel() {

    /** Most recent captures for the signed-in user, for the Home screen. */
    val recent: StateFlow<List<Capture>> = authState
        .flatMapLatest { state ->
            val uid = (state as? AuthState.SignedIn)?.user?.uid
            if (uid == null) flowOf(emptyList())
            else repository.observeAll(uid).map { it.take(RECENT_LIMIT) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        private const val RECENT_LIMIT = 8

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                HomeViewModel(
                    repository = app.container.captureRepository,
                    authState = app.container.authRepository.authState,
                )
            }
        }
    }
}
