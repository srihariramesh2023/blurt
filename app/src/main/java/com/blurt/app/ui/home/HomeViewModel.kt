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
import com.blurt.app.notifications.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: CaptureRepository,
    private val reminderScheduler: ReminderScheduler?,
    private val authState: StateFlow<AuthState>,
) : ViewModel() {

    /** Most recent captures for the signed-in user, for the Home screen. */
    val recent: StateFlow<List<Capture>> = authState
        .flatMapLatest { state ->
            val uid = (state as? AuthState.SignedIn)?.user?.uid
            if (uid == null) flowOf(emptyList())
            else repository.observeAll(uid).map { it.take(RECENT_LIMIT) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tombstones the capture; the sync engine removes it from the backend. */
    fun delete(id: Long) {
        viewModelScope.launch {
            val uid = (authState.value as? AuthState.SignedIn)?.user?.uid ?: return@launch
            reminderScheduler?.cancel(id)
            repository.delete(id, uid)
        }
    }

    companion object {
        private const val RECENT_LIMIT = 8

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                HomeViewModel(
                    repository = app.container.captureRepository,
                    reminderScheduler = app.container.reminderScheduler,
                    authState = app.container.authRepository.authState,
                )
            }
        }
    }
}
