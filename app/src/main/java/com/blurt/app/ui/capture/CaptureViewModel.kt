package com.blurt.app.ui.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blurt.app.BlurtApp
import com.blurt.app.ai.CaptureAnalysis
import com.blurt.app.ai.CaptureAnalysisParser
import com.blurt.app.ai.CaptureAnalyzer
import com.blurt.app.auth.AuthState
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.model.CaptureType
import com.blurt.app.data.model.Recurrence
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
    initialText: String?,
) : ViewModel() {

    private val _content = MutableStateFlow(initialText ?: "")
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

    /** The reminder that was really scheduled, if any — drives the confirm toast. */
    private val _savedReminderAt = MutableStateFlow<Long?>(null)
    val savedReminderAt: StateFlow<Long?> = _savedReminderAt.asStateFlow()

    /** How many blurts the last save created — the toast says \"3 blurts saved\". */
    private val _savedCount = MutableStateFlow(1)
    val savedCount: StateFlow<Int> = _savedCount.asStateFlow()

    /** The full analysis + time waiting on the user's decision. */
    data class PendingReminder(val analysis: CaptureAnalysis, val at: Long)

    /** One blurt's worth of save data — what actually hits the database. */
    private data class SaveBlurt(
        val content: String,
        val category: com.blurt.app.data.model.CaptureCategory?,
        val intent: com.blurt.app.data.model.CaptureIntent?,
        val reminderAt: Long?,
        val important: Boolean,
        val recurrence: Recurrence,
    )

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
                saveCapture(uid, content, CaptureType.LINK, null, null, null, false, Recurrence.NONE)
            }
            return
        }

        viewModelScope.launch {
            _analyzing.value = true
            val analyses = runCatching {
                analyzer?.analyze(content, System.currentTimeMillis())
            }.getOrNull().orEmpty()
                // No key / offline / analyzer failure → the text's own words
                // still decide times and recurrences ("tomorrow at 9pm" must
                // still ask for a reminder, even with zero AI).
                .ifEmpty { listOfNotNull(CaptureAnalysisParser.localFallback(content)) }
            _analyzing.value = false
            val first = analyses.firstOrNull()
            when {
                // Nothing understood — not even a time — save as-is, no reminder.
                analyses.isEmpty() -> saveCapture(uid, content, CaptureType.TEXT, null, null, null, false, Recurrence.NONE)

                // One blurt with a concrete future time → ask before saving.
                analyses.size == 1 && first!!.reminderAt != null && first.reminderAt > System.currentTimeMillis() ->
                    _pendingReminder.value = PendingReminder(first, first.reminderAt)

                // A single understood blurt, or several distinct ideas — each
                // idea becomes its own blurt so nothing gets forgotten. The
                // sheet is skipped: recurring times are the whole point.
                else -> saveBlurts(uid, content, analyses)
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
                repository.create(
                    ownerId = uid,
                    type = CaptureType.TEXT,
                    content = content,
                    category = pending.analysis.category,
                    intent = pending.analysis.intent,
                    reminderAt = pending.at,
                    isImportant = pending.analysis.important,
                    recurrence = pending.analysis.recurrence,
                )
            }.getOrElse {
                _error.value = "Couldn't save. Try again."
                return@launch
            }
            reminderScheduler?.schedule(id, content, pending.at)
            _savedReminderAt.value = pending.at
            _savedCount.value = 1
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
            saveCapture(uid, content, CaptureType.TEXT, pending.analysis.category, pending.analysis.intent, null, pending.analysis.important, Recurrence.NONE)
            if (notificationsBlocked) {
                _notice.value = "Notifications are off — saved without a reminder."
            }
        }
    }

    fun onSavedHandled() {
        _saved.value = null
        _savedReminderAt.value = null
        _savedCount.value = 1
    }

    private suspend fun saveCapture(
        uid: String,
        content: String,
        type: CaptureType,
        category: com.blurt.app.data.model.CaptureCategory?,
        intent: com.blurt.app.data.model.CaptureIntent?,
        reminderAt: Long?,
        important: Boolean,
        recurrence: Recurrence,
    ) {
        val id = runCatching {
            repository.create(uid, type, content, category, intent, reminderAt, important, recurrence)
        }.getOrElse {
            _error.value = "Couldn't save. Try again."
            return
        }
        if (reminderAt != null) reminderScheduler?.schedule(id, content, reminderAt)
        _savedReminderAt.value = reminderAt
        _savedCount.value = 1
        _saved.value = id
    }

    /**
     * Saves every distinct blurt the analyzer found. A long capture with
     * several ideas becomes several blurts; each with a future reminder
     * (including recurring ones) gets its alarm scheduled right away.
     */
    private suspend fun saveBlurts(uid: String, fallbackContent: String, analyses: List<CaptureAnalysis>) {
        val seen = mutableSetOf<String>()
        val blurts = analyses.mapNotNull { a ->
            val text = a.content?.takeIf { it.isNotBlank() } ?: fallbackContent
            if (!seen.add(text.lowercase().trim())) return@mapNotNull null
            SaveBlurt(text, a.category, a.intent, a.reminderAt, a.important, a.recurrence)
        }
        val ids = mutableListOf<Long>()
        var firstReminderAt: Long? = null
        for (blurt in blurts) {
            val id = runCatching {
                repository.create(
                    ownerId = uid,
                    type = CaptureType.TEXT,
                    content = blurt.content,
                    category = blurt.category,
                    intent = blurt.intent,
                    reminderAt = blurt.reminderAt,
                    isImportant = blurt.important,
                    recurrence = blurt.recurrence,
                )
            }.getOrElse {
                _error.value = "Couldn't save. Try again."
                return
            }
            ids += id
            if (blurt.reminderAt != null) {
                reminderScheduler?.schedule(id, blurt.content, blurt.reminderAt)
                if (firstReminderAt == null) firstReminderAt = blurt.reminderAt
            }
        }
        _savedReminderAt.value = firstReminderAt
        _savedCount.value = ids.size
        _saved.value = ids.first()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                val handle = createSavedStateHandle()
                CaptureViewModel(
                    repository = app.container.captureRepository,
                    analyzer = app.container.captureAnalyzer,
                    reminderScheduler = app.container.reminderScheduler,
                    authState = app.container.authRepository.authState,
                    initialText = handle.get<String>("text"),
                )
            }
        }
    }
}
