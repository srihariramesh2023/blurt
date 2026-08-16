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
import com.blurt.app.ai.CaptureAnalysisParser
import com.blurt.app.ai.CaptureAnalyzer
import com.blurt.app.ai.FollowUpAnswerParser
import com.blurt.app.auth.AuthState
import com.blurt.app.data.CaptureRepository
import com.blurt.app.data.model.CaptureType
import com.blurt.app.data.model.Recurrence
import com.blurt.app.notifications.ReminderScheduler
import com.blurt.app.ui.components.BlurtTts
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

    /**
     * The companion speaks back — a short line acknowledging what was said
     * and what Blurt did. Auto-advances when the utterance ends.
     */
    REPLYING,

    /**
     * The two-turn loop: after a reminder auto-saves, Blurt asks "want a
     * heads-up before?" and listens for the answer (yes → a nudge alarm;
     * no/anything else → straight to SAVED).
     */
    FOLLOWUP,

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
    private val tts: BlurtTts? = null,
) : ViewModel() {

    private val _phase = MutableStateFlow(VoicePhase.IDLE)
    val phase: StateFlow<VoicePhase> = _phase.asStateFlow()

    /** The live transcript — updates in real time while speaking. */
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    /** Full analysis once speech stops; null means \"save unclassified\". */
    private val _analysis = MutableStateFlow<CaptureAnalysis?>(null)
    val analysis: StateFlow<CaptureAnalysis?> = _analysis.asStateFlow()

    /**
     * All distinct blurts the analyzer found — usually one, several when a
     * long capture held multiple ideas. Each is saved as its own blurt so
     * nothing gets forgotten.
     */
    private var pendingAnalyses: List<CaptureAnalysis> = emptyList()

    /** How many blurts the last save created — the toast says \"3 blurts saved\". */
    private val _savedCount = MutableStateFlow(1)
    val savedCount: StateFlow<Int> = _savedCount.asStateFlow()

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

    /** What was actually saved — the reminder that really got scheduled, if any.
     *  Drives the confirmation message so "Save without reminder" never claims
     *  a reminder. */
    private val _savedReminderAt = MutableStateFlow<Long?>(null)
    val savedReminderAt: StateFlow<Long?> = _savedReminderAt.asStateFlow()

    /** Set when the user taps Edit — the screen navigates to the composer. */
    private val _editRequested = MutableStateFlow(false)
    val editRequested: StateFlow<Boolean> = _editRequested.asStateFlow()

    /** The companion's spoken line — shown on screen while it's spoken. */
    private val _reply = MutableStateFlow<String?>(null)
    val reply: StateFlow<String?> = _reply.asStateFlow()

    /** The companion's save decision, applied when its reply finishes. */
    private var pendingSave = true

    /** The follow-up question asked after a reminder auto-saves. */
    private val _followUpQuestion = MutableStateFlow<String?>(null)
    val followUpQuestion: StateFlow<String?> = _followUpQuestion.asStateFlow()

    /** The saved reminder a yes-answer would nudge before. */
    private var pendingHeadsUp: ReminderTarget? = null

    /** The capture that got saved while the follow-up was pending. */
    private var pendingSavedId: Long? = null

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
        // Warm the fallback engine now so a no-key reply doesn't pay its
        // cold start later.
        tts?.warmUp()
        beginRecognition()
    }

    /** The follow-up round: same mic, phase stays FOLLOWUP so the question
     *  stays on screen while the answer is heard. */
    private fun startFollowUpListening() {
        _progressive.value = null
        _transcript.value = ""
        _level.value = 0f
        beginRecognition()
    }

    private fun beginRecognition() {
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
        tts?.stop()
        recognizer?.cancel()
        if (_phase.value == VoicePhase.FOLLOWUP) {
            // The blurt already saved — only the optional heads-up is dropped.
            finishFollowUp(accepted = false)
        } else {
            reset()
        }
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
        // One blurt per distinct idea the analyzer found (usually one). If it
        // couldn't classify, the whole recording saves as a single note.
        val analyses = if (_analysis.value == null) {
            emptyList()
        } else {
            pendingAnalyses.ifEmpty { listOf(_analysis.value!!) }
        }
        val blurts = if (analyses.isEmpty()) {
            listOf(
                SaveBlurt(
                    content = content,
                    category = null,
                    intent = null,
                    reminderAt = null,
                    important = false,
                    recurrence = Recurrence.NONE,
                )
            )
        } else {
            analyses.map { a ->
                SaveBlurt(
                    content = a.content?.takeIf { it.isNotBlank() } ?: content,
                    category = a.category,
                    intent = a.intent,
                    reminderAt = if (keepReminder) {
                        a.reminderAt?.takeIf { it > System.currentTimeMillis() }
                    } else null,
                    important = a.important,
                    recurrence = a.recurrence,
                )
            }
        }
        persist(blurts)
    }

    /**
     * The companion path: the reply has been spoken, the save decision is
     * in. Save the blurts it found, or drop the transcript entirely when it
     * decided nothing was worth keeping.
     */
    private fun finishReply() {
        if (_phase.value != VoicePhase.REPLYING) return
        if (pendingSave && pendingAnalyses.isNotEmpty()) {
            autoSave()
        } else {
            // Nothing worth keeping — the transcript is dropped, not saved.
            reset()
        }
    }

    /** Tap-to-skip: stop the spoken reply and move on now. */
    fun skipReply() {
        tts?.stop()
        finishReply()
    }

    private fun autoSave() {
        val content = _transcript.value.trim()
        if (content.isBlank() || pendingAnalyses.isEmpty()) return
        val blurts = pendingAnalyses.map { a ->
            SaveBlurt(
                content = a.content?.takeIf { it.isNotBlank() } ?: content,
                category = a.category,
                intent = a.intent,
                reminderAt = a.reminderAt?.takeIf { it > System.currentTimeMillis() },
                important = a.important,
                recurrence = a.recurrence,
            )
        }
        // Two-turn loop: when a reminder actually saved, keep the mic open
        // and ask about a heads-up before the final SAVED screen.
        persist(blurts, followUp = true)
    }

    /**
     * The actual database writes — shared by the confirm and auto-save paths.
     * When [followUp] is set and a reminder really got scheduled, the flow
     * goes to FOLLOWUP (ask + listen) instead of straight to SAVED.
     */
    private fun persist(blurts: List<SaveBlurt>, followUp: Boolean = false) {
        val content = _transcript.value.trim()
        if (content.isBlank()) return
        val uid = (authState.value as? AuthState.SignedIn)?.user?.uid
        if (uid == null) {
            _error.value = "You need to be signed in to save."
            return
        }
        viewModelScope.launch {
            // Guard against the model returning the same fragment twice.
            val seen = mutableSetOf<String>()
            val distinct = blurts.filter { b -> seen.add(b.content.lowercase().trim()) }
            val ids = mutableListOf<Long>()
            var firstReminderAt: Long? = null
            var firstReminderId: Long? = null
            var firstReminderContent: String? = null
            for (blurt in distinct) {
                val id = runCatching {
                    repository.create(
                        ownerId = uid,
                        type = if (blurt.content.isHttpUrl()) CaptureType.LINK else CaptureType.TEXT,
                        content = blurt.content,
                        category = blurt.category,
                        intent = blurt.intent,
                        reminderAt = blurt.reminderAt,
                        isImportant = blurt.important,
                        recurrence = blurt.recurrence,
                    )
                }.getOrElse {
                    _error.value = "Couldn't save. Try again."
                    return@launch
                }
                ids += id
                if (blurt.reminderAt != null) {
                    reminderScheduler?.schedule(id, blurt.content, blurt.reminderAt)
                    if (firstReminderAt == null) {
                        firstReminderAt = blurt.reminderAt
                        firstReminderId = id
                        firstReminderContent = blurt.content
                    }
                }
            }
            _savedReminderAt.value = firstReminderAt
            _savedCount.value = ids.size
            val target = firstReminderId?.let { id ->
                firstReminderAt?.let { at ->
                    ReminderTarget(id, firstReminderContent.orEmpty(), at)
                }
            }
            if (followUp && target != null) {
                // Saved with a reminder — keep the conversation going.
                pendingSavedId = ids.first()
                askFollowUp(target)
            } else {
                _phase.value = VoicePhase.SAVED
                _saved.value = ids.first()
            }
        }
    }

    /** A saved reminder the follow-up might nudge before. */
    private data class ReminderTarget(
        val captureId: Long,
        val content: String,
        val reminderAt: Long,
    )

    /** One blurt's worth of save data — what actually hits the database. */
    private data class SaveBlurt(
        val content: String,
        val category: com.blurt.app.data.model.CaptureCategory?,
        val intent: com.blurt.app.data.model.CaptureIntent?,
        val reminderAt: Long?,
        val important: Boolean,
        val recurrence: Recurrence,
    )

    fun onSavedHandled() {
        _saved.value = null
    }

    /** "Done" after the saved toast — back to the resting orb (in-place flows). */
    fun dismissSaved() {
        _saved.value = null
        reset()
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
        pendingAnalyses = emptyList()
        pendingSave = true
        _savedCount.value = 1
        _progressive.value = null
        _reply.value = null
        _followUpQuestion.value = null
        pendingHeadsUp = null
        pendingSavedId = null
        _level.value = 0f
        _error.value = null
        _notice.value = null
        _savedReminderAt.value = null
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
                }.getOrNull()?.firstOrNull()
                if (_phase.value == VoicePhase.LISTENING) _progressive.value = analysis
            }
        }
    }

    private fun onSpeechEnded(finalText: String) {
        progressiveJob?.cancel()
        _level.value = 0f
        // The follow-up round: the answer is parsed locally — no AI call.
        if (_phase.value == VoicePhase.FOLLOWUP) {
            handleFollowUpAnswer(finalText)
            return
        }
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
                analyzer?.analyzeWithReply(text, System.currentTimeMillis())
            }.getOrNull()
            if (analyzer != null && result == null) {
                // The board's error state — a snag while classifying, with
                // Try Again / Save as Note as the escape hatches. Even here
                // the text can still carry a reminder, so the fallback keeps
                // it.
                val local = listOfNotNull(CaptureAnalysisParser.localFallback(text))
                if (local.isNotEmpty()) {
                    pendingAnalyses = local
                    _analysis.value = local.first()
                    _progressive.value = null
                    _phase.value = VoicePhase.CONFIRM
                } else {
                    _analysis.value = null
                    pendingAnalyses = emptyList()
                    _progressive.value = null
                    _phase.value = VoicePhase.ERROR
                }
            } else if (result != null && result.reply != null) {
                // Companion mode: one call answered with a spoken line and a
                // save decision. Speak the reply; when it finishes, auto-save
                // the blurts or drop the transcript entirely.
                pendingAnalyses = result.analyses
                pendingSave = result.save
                _analysis.value = result.analyses.firstOrNull()
                _progressive.value = null
                _reply.value = result.reply
                _phase.value = VoicePhase.REPLYING
                val t = tts
                if (t != null) {
                    t.speak(result.reply, ::finishReply)
                } else {
                    // No engine (shouldn't happen in the app) — don't stall.
                    viewModelScope.launch { delay(REPLY_PAUSE_MS); finishReply() }
                }
            } else {
                // No companion reply (Gemini fallback / no key) → the classic
                // review. The text's own words still decide times and
                // recurrences ("tomorrow at 9pm" must ask for a reminder even
                // with zero AI).
                val analyses = result?.analyses.orEmpty()
                    .ifEmpty { listOfNotNull(CaptureAnalysisParser.localFallback(text)) }
                pendingAnalyses = analyses
                _analysis.value = analyses.firstOrNull()
                _progressive.value = null
                _phase.value = VoicePhase.CONFIRM
            }
        }
    }

    /** Speak the follow-up question, then listen for the answer. */
    private fun askFollowUp(target: ReminderTarget) {
        pendingHeadsUp = target
        _followUpQuestion.value = FOLLOW_UP_QUESTION
        _transcript.value = ""
        _phase.value = VoicePhase.FOLLOWUP
        val t = tts
        if (t != null) {
            t.speak(FOLLOW_UP_QUESTION, ::startFollowUpListening)
        } else {
            // No engine (shouldn't happen) — don't stall on the question.
            viewModelScope.launch { delay(REPLY_PAUSE_MS); startFollowUpListening() }
        }
    }

    /**
     * The user answered the follow-up. "Yes" arms a heads-up nudge a few
     * minutes before the reminder; anything else — or silence — means no
     * nudge. Either way we land on the same SAVED screen.
     */
    private fun handleFollowUpAnswer(finalText: String) {
        val text = finalText.trim()
        val accepted = FollowUpAnswerParser.parse(text)
        finishFollowUp(accepted = accepted)
    }

    private fun finishFollowUp(accepted: Boolean) {
        if (_phase.value != VoicePhase.FOLLOWUP) return
        val target = pendingHeadsUp
        pendingHeadsUp = null
        _followUpQuestion.value = null
        if (accepted && target != null) {
            val nudgeAt = target.reminderAt - HEADS_UP_LEAD_MS
            reminderScheduler?.scheduleHeadsUp(target.captureId, target.content, nudgeAt)
            // One short confirm, then the same SAVED screen.
            val confirm = "Done — I'll nudge you before."
            _reply.value = confirm
            _phase.value = VoicePhase.REPLYING
            val t = tts
            if (t != null) {
                t.speak(confirm) { finishToSaved() }
            } else {
                viewModelScope.launch { delay(REPLY_PAUSE_MS); finishToSaved() }
            }
        } else {
            finishToSaved()
        }
    }

    private fun finishToSaved() {
        _reply.value = null
        _phase.value = VoicePhase.SAVED
        pendingSavedId?.let { _saved.value = it }
        pendingSavedId = null
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
                val local = listOfNotNull(CaptureAnalysisParser.localFallback(text))
                if (local.isNotEmpty()) {
                    pendingAnalyses = local
                    _analysis.value = local.first()
                    _progressive.value = null
                    _phase.value = VoicePhase.CONFIRM
                } else {
                    _phase.value = VoicePhase.ERROR
                }
            } else {
                val analyses = result.getOrNull().orEmpty()
                    .ifEmpty { listOfNotNull(CaptureAnalysisParser.localFallback(text)) }
                pendingAnalyses = analyses
                _analysis.value = analyses.firstOrNull()
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
            pendingAnalyses = emptyList()
            _savedReminderAt.value = null
            _savedCount.value = 1
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
            // The follow-up round never errors out loud — silence or a
            // recognizer hiccup just means "no heads-up" and the SAVED
            // screen appears as usual.
            if (_phase.value == VoicePhase.FOLLOWUP) {
                finishFollowUp(accepted = false)
                return
            }
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
        tts?.shutdown()
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        private const val PROGRESSIVE_DEBOUNCE_MS = 900L
        private const val PROGRESSIVE_MIN_WORDS = 3
        /** How long to hold the reply on screen when no TTS engine exists. */
        private const val REPLY_PAUSE_MS = 1_200L
        /** The two-turn question after a reminder saves. */
        private const val FOLLOW_UP_QUESTION = "Want me to remind you 15 minutes before?"
        /** How early the heads-up nudge fires. */
        private const val HEADS_UP_LEAD_MS = 15 * 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BlurtApp
                VoiceViewModel(
                    context = app,
                    repository = app.container.captureRepository,
                    analyzer = app.container.captureAnalyzer,
                    reminderScheduler = app.container.reminderScheduler,
                    authState = app.container.authRepository.authState,
                    tts = BlurtTts(
                        context = app,
                        geminiKeyProvider = { app.container.aiKeyStore.geminiKey() },
                        packageName = app.packageName,
                        certSha1 = app.container.signingCertSha1(app),
                    ),
                )
            }
        }
    }
}
