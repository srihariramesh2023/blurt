package com.blurt.app.ui.library

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
import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.notifications.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: CaptureRepository,
    private val reminderScheduler: ReminderScheduler?,
    private val authState: StateFlow<AuthState>,
) : ViewModel() {

    /** Every capture of the signed-in user, newest first (the chip set). */
    val allCaptures: StateFlow<List<Capture>> = authState
        .flatMapLatest { state ->
            val uid = (state as? AuthState.SignedIn)?.user?.uid
            if (uid == null) flowOf(emptyList())
            else repository.observeAll(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCategory = MutableStateFlow<CaptureCategory?>(null)
    val selectedCategory: StateFlow<CaptureCategory?> = _selectedCategory.asStateFlow()

    /** The visible list — filtered by the selected category chip (null = all). */
    val captures: StateFlow<List<Capture>> = combine(allCaptures, _selectedCategory) { all, selected ->
        if (selected == null) all else all.filter { it.category == selected }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(category: CaptureCategory?) {
        _selectedCategory.value = category
    }

    /** Tombstones the capture; the sync engine removes it from the backend. */
    fun delete(id: Long) {
        viewModelScope.launch {
            val uid = (authState.value as? AuthState.SignedIn)?.user?.uid ?: return@launch
            reminderScheduler?.cancel(id)
            repository.delete(id, uid)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                LibraryViewModel(
                    repository = app.container.captureRepository,
                    reminderScheduler = app.container.reminderScheduler,
                    authState = app.container.authRepository.authState,
                )
            }
        }
    }
}
