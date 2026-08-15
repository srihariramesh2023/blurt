package com.blurt.app.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.blurt.app.BlurtApp
import com.blurt.app.ai.CaptureAnalysis
import com.blurt.app.ai.CaptureAnalyzer
import com.blurt.app.auth.AuthState
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.model.CaptureType
import com.blurt.app.notifications.ReminderScheduler
import com.blurt.app.util.isHttpUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the voice flow is right now. */
enum class VoicePhase {
    /** The big orb, waiting. */
    IDLE,

    /** Orb is live; the transcript grows as the user speaks. */
    LISTENING,

    /** Speech finished; Blurt is organizing (the board's checklist). */
    ANALYZING,

    /** The AI failed to classify — Try Again / Save as Note / Type instead. */
    ERROR,

    /** \"Save Blurt / Edit\" — the lightweight confirmation. */
    CONFIRM,

    /** Saved; the screen shows a brief checkmark before leaving. */
    SAVED,
}

/**
 * The V2 capture loop: speak → Blurt understands → save.
 *
 * Live transcription comes from [SpeechRecognizer] (partial results stream in
 * while the user talks). While speaking, the transcript is re-read by the AI
 * on a debounce so \"Looks like a reminder · Health\" chips appear before the
 * user even stops. On stop, one final analysis drives the confirm state —
 * intent, category, a reminder time if one was mentioned, and an importance
 * hint. Every failure path (offline, no key, no speech service) still lands
 * on the confirm state so nothing the user said is ever lost.
 */
class VoiceViewModel(
    private val context: Context,
    private val repository: CaptureRepository,
    private val analyzer: CaptureAnalyzer?,
    private val reminderScheduler: ReminderScheduler?,
    private val authState: StateFlow<AuthState>,
) : ViewModel() {

    private val _phase = MutableStateFlow(VoicePhase.IDLE)
    val phase: StateFlow<VoicePhase> = _phase.asStateFlow()

    /** The live transcript — updates in real time while speaking. */
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    /** Full analysis once speech stops; null means \"save unclassified\". */
    private val _analysis = MutableStateFlow<CaptureAnalysis?>(null)
    val analysis: StateFlow<CaptureAnalysis?> = _analysis.asStateFlow()

    /** Progressive understanding shown *while* the user is still speaking. */
    private val _progressive = MutableStateFlow<CaptureAnalysis?>(null)
    val progressive: StateFlow<CaptureAnalysis?> = _progressive.asStateFlow()

    /** Mic input level 0..1, straight from the recognizer's onRmsChanged. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Non-fatal message (e.g. \"notifications are off\"). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** True when the mic permission must be requested before listening. */
    private val _needsMicPermission = MutableStateFlow(false)
    val needsMicPermission: StateFlow<Boolean> = _needsMicPermission.asStateFlow()

    /** Set to the new capture id once saved; the screen observes it. */
    private val _saved = MutableStateFlow<Long?>(null)
    val saved: StateFlow<Long?> = _saved.asStateFlow()

    /** Set when the user taps Edit — the screen navigates to the composer. */
    private val _editRequested = MutableStateFlow(false)
    val editRequested: StateFlow<Boolean> = _editRequested.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var progressiveJob: Job? = null

    fun onMicTapped() {
        _error.value = null
        _notice.value = null
        // isRecognitionAvailable() is unreliable on some OEM builds (it can
        // fail even when the Google speech service resolves fine), so resolve
        // the recognizer action ourselves — the same thing the API does, minus
        // the quirk.
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .addCategory(Intent.CATEGORY_DEFAULT)
        val available = context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        if (!available) {
            _error.value = "Voice isn't available on this device — type instead."
            return
        }
        _needsMicPermission.value = true // the screen requests + calls back
    }

    fun onMicPermissionResult(granted: Boolean) {
        _needsMicPermission.value = false
        if (granted) startListening()
        else _error.value = "Microphone access is needed to speak to Blurt."
    }

    private fun startListening() {
        _progressive.value = null
        _transcript.value = ""
        _level.value = 0f
        _phase.value = VoicePhase.LISTENING

        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        speech.setRecognitionListener(listener)
        recognizer?.destroy()
        recognizer = speech

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        speech.startListening(intent)
    }

    /** User tapped stop — ask the recognizer for its final result. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Discard the recording and go back to the idle mic. */
    fun cancel() {
        recognizer?.cancel()
        reset()
    }

    /** \"Save Blurt\" on the confirm state — includes the reminder if detected. */
    fun save() {
        saveWithReminder(keepReminder = true)
    }

    /** Quiet secondary action when a reminder was detected. */
    fun saveWithoutReminder() {
        saveWithReminder(keepReminder = false)
    }

    private fun saveWithReminder(keepReminder: Boolean) {
        val content = _transcript.value.trim()
        if (content.isBlank() || _phase.value != VoicePhase.CONFIRM) return
        val uid = (authState.value as? AuthState.SignedIn)?.user?.uid
        if (uid == null) {
            _error.value = "You need to be signed in to save."
            return
        }
        val analysis = _analysis.value
        val reminderAt = if (keepReminder) analysis?.reminderAt?.takeIf { it > System.currentTimeMillis() } else null
        val type = if (content.isHttpUrl()) CaptureType.LINK else CaptureType.TEXT

        viewModelScope.launch {
            val id = runCatching {
                repository.create(
                    ownerId = uid,
                    type = type,
                    content = content,
                    category = analysis?.category,
                    intent = analysis?.intent,
                    reminderAt = reminderAt,
                    isImportant = analysis?.important ?: false,
                )
            }.getOrElse {
                _error.value = "Couldn't save. Try again."
                return@launch
            }
            if (reminderAt != null) reminderScheduler?.schedule(id, content, reminderAt)
            _phase.value = VoicePhase.SAVED
            _saved.value = id
        }
    }

    fun onSavedHandled() {
        _saved.value = null
    }

    /** \"Edit\" — hand the transcript to the composer, pre-filled. */
    fun requestEdit() {
        _editRequested.value = true
    }

    fun onEditHandled() {
        _editRequested.value = false
        reset()
    }

    fun clearError() {
        _error.value = null
    }

    private fun reset() {
        _phase.value = VoicePhase.IDLE
        _transcript.value = ""
        _analysis.value = null
        _progressive.value = null
        _level.value = 0f
        _error.value = null
        _notice.value = null
    }

    private fun onTranscript(text: String) {
        _transcript.value = text
        // Analyze while speaking: debounce, so a quiet moment mid-sentence
        // produces a \"looks like…\" chip without hammering the API.
        progressiveJob?.cancel()
        if (analyzer != null && text.split(Regex("\\s+")).size >= PROGRESSIVE_MIN_WORDS) {
            progressiveJob = viewModelScope.launch {
                delay(PROGRESSIVE_DEBOUNCE_MS)
                val analysis = runCatching {
                    analyzer.analyze(text, System.currentTimeMillis())
                }.getOrNull()
                if (_phase.value == VoicePhase.LISTENING) _progressive.value = analysis
            }
        }
    }

    private fun onSpeechEnded(finalText: String) {
        progressiveJob?.cancel()
        _level.value = 0f
        val text = finalText.trim()
        if (text.isBlank()) {
            reset()
            _error.value = "I didn't catch that — try again."
            return
        }
        _transcript.value = text
        _phase.value = VoicePhase.ANALYZING
        viewModelScope.launch {
            val result = runCatching {
                analyzer?.analyze(text, System.currentTimeMillis())
            }
            if (analyzer != null && result.isFailure) {
                // The board's error state — a snag while classifying, with
                // Try Again / Save as Note as the escape hatches.
                _analysis.value = null
                _progressive.value = null
                _phase.value = VoicePhase.ERROR
            } else {
                _analysis.value = result.getOrNull()
                _progressive.value = null
                _phase.value = VoicePhase.CONFIRM
            }
        }
    }

    /** The board's error state: \"Try Again\" re-runs classification. */
    fun retry() {
        val text = _transcript.value.trim()
        if (text.isBlank()) return
        _phase.value = VoicePhase.ANALYZING
        viewModelScope.launch {
            val result = runCatching {
                analyzer?.analyze(text, System.currentTimeMillis())
            }
            if (analyzer != null && result.isFailure) {
                _phase.value = VoicePhase.ERROR
            } else {
                _analysis.value = result.getOrNull()
                _progressive.value = null
                _phase.value = VoicePhase.CONFIRM
            }
        }
    }

    /** The board's error state: \"Save as Note\" keeps the recording safe. */
    fun saveAsNote() {
        val content = _transcript.value.trim()
        if (content.isBlank()) return
        val uid = (authState.value as? AuthState.SignedIn)?.user?.uid
        if (uid == null) {
            _error.value = "You need to be signed in to save."
            return
        }
        val type = if (content.isHttpUrl()) CaptureType.LINK else CaptureType.TEXT
        viewModelScope.launch {
            val id = runCatching {
                repository.create(
                    ownerId = uid,
                    type = type,
                    content = content,
                    category = null,
                    intent = null,
                    reminderAt = null,
                    isImportant = false,
                )
            }.getOrElse {
                _error.value = "Couldn't save. Try again."
                return@launch
            }
            _analysis.value = null
            _phase.value = VoicePhase.SAVED
            _saved.value = id
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // The recognizer reports 0..~10; normalize into a visual level.
            _level.value = (rmsdB / 10f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    reset()
                    _error.value = "I didn't catch that — try again."
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    reset()
                    _error.value = "Microphone access is needed to speak to Blurt."
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    reset()
                    _error.value = "The speech service is offline right now — try again."
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    reset()
                    _error.value = "Another app is using the mic — try again in a moment."
                }
                else -> {
                    reset()
                    _error.value = "Couldn't start listening. Try again."
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            onSpeechEnded(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) onTranscript(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun onCleared() {
        progressiveJob?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        private const val PROGRESSIVE_DEBOUNCE_MS = 900L
        private const val PROGRESSIVE_MIN_WORDS = 3

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                VoiceViewModel(
                    context = app,
                    repository = app.container.captureRepository,
                    analyzer = app.container.captureAnalyzer,
                    reminderScheduler = app.container.reminderScheduler,
                    authState = app.container.authRepository.authState,
                )
            }
        }
    }
}
