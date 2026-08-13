package com.blurt.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blurt.app.BlurtApp
import com.blurt.app.auth.AuthState
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureType
import com.blurt.app.notifications.ReminderScheduler
import com.blurt.app.util.isHttpUrl
import com.blurt.app.util.normalizedHttpUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(
    private val repository: CaptureRepository,
    private val reminderScheduler: ReminderScheduler?,
    private val authState: StateFlow<AuthState>,
    private val captureId: Long,
) : ViewModel() {

    val capture: StateFlow<Capture?> = authState
        .flatMapLatest { state ->
            val uid = (state as? AuthState.SignedIn)?.user?.uid
            if (uid == null) flowOf(null)
            else repository.observeById(captureId, uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _editText = MutableStateFlow("")
    val editText: StateFlow<String> = _editText.asStateFlow()

    private val _editError = MutableStateFlow<String?>(null)
    val editError: StateFlow<String?> = _editError.asStateFlow()

    /** Set to true once deleted; the screen observes it to navigate back. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** Set to true once archived; the screen pops back to the lists. */
    private val _archived = MutableStateFlow(false)
    val archived: StateFlow<Boolean> = _archived.asStateFlow()

    fun startEditing() {
        val current = capture.value
        _editText.value = current?.content.orEmpty()
        _editError.value = null
        _isEditing.value = true
    }

    fun onEditTextChange(text: String) {
        _editText.value = text
        _editError.value = null
    }

    fun cancelEditing() {
        _isEditing.value = false
    }

    fun saveEdit() {
        val type = capture.value?.type
        val raw = _editText.value
        val text = if (type == CaptureType.LINK) raw.normalizedHttpUrl() else raw.trim()
        if (text.isBlank()) {
            _editError.value = "Content can't be empty."
            return
        }
        if (type == CaptureType.LINK && !text.isHttpUrl()) {
            _editError.value = "That doesn't look like a valid link."
            return
        }
        viewModelScope.launch {
            val uid = currentUid() ?: return@launch
            repository.updateContent(captureId, uid, text)
            _isEditing.value = false
        }
    }

    fun delete() {
        viewModelScope.launch {
            val uid = currentUid() ?: return@launch
            // Drop any pending reminder alarm before the row is tombstoned.
            reminderScheduler?.cancel(captureId)
            repository.delete(captureId, uid)
            _deleted.value = true
        }
    }

    fun onDeletedHandled() {
        _deleted.value = false
    }

    /** Star/unstar the blurt; the gold star travels with it across devices. */
    fun toggleImportant() {
        val current = capture.value ?: return
        viewModelScope.launch {
            val uid = currentUid() ?: return@launch
            repository.setImportant(captureId, uid, !current.isImportant)
        }
    }

    /** Mark done / reopen — cancels the alarm on done, never re-fires. */
    fun toggleCompleted() {
        val current = capture.value ?: return
        val nowDone = current.completedAt == null
        viewModelScope.launch {
            val uid = currentUid() ?: return@launch
            repository.setCompleted(captureId, uid, nowDone)
            if (nowDone) reminderScheduler?.cancel(captureId)
        }
    }

    /** Move to Library → Archived and leave the screen. */
    fun archive() {
        viewModelScope.launch {
            val uid = currentUid() ?: return@launch
            repository.setArchived(captureId, uid, true)
            _archived.value = true
        }
    }

    fun onArchivedHandled() {
        _archived.value = false
    }

    private fun currentUid(): String? = (authState.value as? AuthState.SignedIn)?.user?.uid

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                val id = createSavedStateHandle()["id"] as Long? ?: 0L
                DetailViewModel(
                    repository = app.container.captureRepository,
                    reminderScheduler = app.container.reminderScheduler,
                    authState = app.container.authRepository.authState,
                    captureId = id,
                )
            }
        }
    }
}
