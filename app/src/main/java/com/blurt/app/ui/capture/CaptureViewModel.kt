package com.blurt.app.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blurt.app.BlurtApp
import com.blurt.app.ai.CaptureAnalyzer
import com.blurt.app.auth.AuthState
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureType
import com.blurt.app.notifications.ReminderScheduler
import com.blurt.app.util.isHttpUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The fast capture composer — no type selector. The user just types; Blurt
 * decides:
 *
 * - a URL is a Link (pure rules, no AI round-trip);
 * - everything else is read by the AI, which assigns one fixed category and
 *   optionally extracts a concrete time ("tomorrow at 3pm");
 * - if a time was mentioned, a confirm sheet asks whether to set a priority
 *   reminder — one tap either way, nothing is saved before the user decides.
 *
 * Every failure path (offline, no API key, rate limit) still saves the blurt
 * immediately, uncategorized and reminder-free. Save can never break.
 */
class CaptureViewModel(
    private val repository: CaptureRepository,
    private val analyzer: CaptureAnalyzer?,
    private val reminderScheduler: ReminderScheduler?,
    private val authState: StateFlow<AuthState>,
) : ViewModel() {

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    /** True while the AI reads the blurt (brief — the save button shows it). */
    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    /** A detected time the user hasn't confirmed yet; the sheet watches this. */
    private val _pendingReminder = MutableStateFlow<PendingReminder?>(null)
    val pendingReminder: StateFlow<PendingReminder?> = _pendingReminder.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Non-fatal message (e.g. "notifications are off") shown in a calm tone. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Set to the new capture id once saved; the screen observes it to pop back. */
    private val _saved = MutableStateFlow<Long?>(null)
    val saved: StateFlow<Long?> = _saved.asStateFlow()

    /** Category + time waiting on the user's decision. */
    data class PendingReminder(val category: CaptureCategory, val at: Long)

    fun onContentChange(text: String) {
        _content.value = text
        _error.value = null
        _notice.value = null
    }

    fun save() {
        val content = _content.value.trim()
        if (content.isBlank()) {
            _error.value = "Write something first."
            return
        }
        if (_analyzing.value || _pendingReminder.value != null) return
        val uid = (authState.value as? AuthState.SignedIn)?.user?.uid
        if (uid == null) {
            _error.value = "You need to be signed in to save."
            return
        }

        // Links are classified by rule: a URL is a Link blurt, no AI involved.
        if (content.isHttpUrl()) {
            viewModelScope.launch {
                saveCapture(uid, content, CaptureType.LINK, category = null, reminderAt = null)
            }
            return
        }

        viewModelScope.launch {
            _analyzing.value = true
            val analysis = runCatching {
                analyzer?.analyze(content, System.currentTimeMillis())
            }.getOrNull()
            _analyzing.value = false
            when {
                // Offline / no key / analyzer failure → save as-is, no reminder.
                analysis == null -> saveCapture(uid, content, CaptureType.TEXT, null, null)

                // A concrete future time → ask before saving.
                analysis.reminderAt != null && analysis.reminderAt > System.currentTimeMillis() ->
                    _pendingReminder.value = PendingReminder(analysis.category, analysis.reminderAt)

                // Categorized but no (or past) time → save immediately.
                else -> saveCapture(uid, content, CaptureType.TEXT, analysis.category, null)
            }
        }
    }

    /** "Remind me" on the confirm sheet: save with the category and schedule the notification. */
    fun confirmReminder() {
        val pending = _pendingReminder.value ?: return
        _pendingReminder.value = null
        val uid = (authState.value as? AuthState.SignedIn)?.user?.uid ?: return
        val content = _content.value.trim()
        viewModelScope.launch {
            val id = runCatching {
                repository.create(uid, CaptureType.TEXT, content, pending.category, pending.at)
            }.getOrElse {
                _error.value = "Couldn't save. Try again."
                return@launch
            }
            reminderScheduler?.schedule(id, content, pending.at)
            _saved.value = id
        }
    }

    /** "Just save" (or notifications are blocked): keep the category, drop the reminder. */
    fun dismissReminder(notificationsBlocked: Boolean = false) {
        val pending = _pendingReminder.value ?: return
        _pendingReminder.value = null
        val uid = (authState.value as? AuthState.SignedIn)?.user?.uid ?: return
        val content = _content.value.trim()
        viewModelScope.launch {
            saveCapture(uid, content, CaptureType.TEXT, pending.category, null)
            if (notificationsBlocked) {
                _notice.value = "Notifications are off — saved without a reminder."
            }
        }
    }

    fun onSavedHandled() {
        _saved.value = null
    }

    private suspend fun saveCapture(
        uid: String,
        content: String,
        type: CaptureType,
        category: CaptureCategory?,
        reminderAt: Long?,
    ) {
        val id = runCatching { repository.create(uid, type, content, category, reminderAt) }
            .getOrElse {
                _error.value = "Couldn't save. Try again."
                return
            }
        _saved.value = id
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                CaptureViewModel(
                    repository = app.container.captureRepository,
                    analyzer = app.container.captureAnalyzer,
                    reminderScheduler = app.container.reminderScheduler,
                    authState = app.container.authRepository.authState,
                )
            }
        }
    }
}
