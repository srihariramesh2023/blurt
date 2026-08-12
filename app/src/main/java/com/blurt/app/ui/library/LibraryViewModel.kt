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
import com.blurt.app.data.model.CaptureIntent
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

/** The main browse collections — generated automatically, never by hand. */
enum class LibraryCollection(val label: String) {
    ALL("All"),
    REMINDERS("Reminders"),
    TASKS("Tasks"),
    IDEAS("Ideas"),
    IMPORTANT("Important"),
    ARCHIVED("Archived"),
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: CaptureRepository,
    private val reminderScheduler: ReminderScheduler?,
    private val authState: StateFlow<AuthState>,
) : ViewModel() {

    /** Every non-archived capture of the signed-in user, newest first. */
    val allCaptures: StateFlow<List<Capture>> = authState
        .flatMapLatest { state ->
            val uid = (state as? AuthState.SignedIn)?.user?.uid
            if (uid == null) flowOf(emptyList())
            else repository.observeAll(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The archive — browsed separately so it never clutters the main lists. */
    val archivedCaptures: StateFlow<List<Capture>> = authState
        .flatMapLatest { state ->
            val uid = (state as? AuthState.SignedIn)?.user?.uid
            if (uid == null) flowOf(emptyList())
            else repository.observeArchived(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCollection = MutableStateFlow(LibraryCollection.ALL)
    val selectedCollection: StateFlow<LibraryCollection> = _selectedCollection.asStateFlow()

    private val _selectedCategory = MutableStateFlow<CaptureCategory?>(null)
    val selectedCategory: StateFlow<CaptureCategory?> = _selectedCategory.asStateFlow()

    /** The visible list — collection + category filters applied. */
    val captures: StateFlow<List<Capture>> = combine(
        allCaptures,
        archivedCaptures,
        _selectedCollection,
        _selectedCategory,
    ) { all, archived, collection, category ->
        val source = if (collection == LibraryCollection.ARCHIVED) archived else all
        source
            .filter { capture ->
                when (collection) {
                    LibraryCollection.ALL -> true
                    LibraryCollection.REMINDERS ->
                        capture.intent == CaptureIntent.REMINDER || capture.reminderAt != null
                    LibraryCollection.TASKS -> capture.intent == CaptureIntent.TASK
                    LibraryCollection.IDEAS ->
                        capture.intent == CaptureIntent.IDEA || capture.category == CaptureCategory.IDEAS
                    LibraryCollection.IMPORTANT -> capture.isImportant
                    LibraryCollection.ARCHIVED -> true
                }
            }
            .filter { capture -> category == null || capture.category == category }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCollection(collection: LibraryCollection) {
        // Switching into the archive clears the category chip — categories are
        // a property of the live lists, and the archive is its own view.
        if (collection == LibraryCollection.ARCHIVED) _selectedCategory.value = null
        _selectedCollection.value = collection
    }

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

    /** Moves a blurt into (or out of) the archive. */
    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            val uid = (authState.value as? AuthState.SignedIn)?.user?.uid ?: return@launch
            repository.setArchived(id, uid, archived)
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
