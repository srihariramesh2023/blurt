package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
import com.blurt.app.data.model.Recurrence

/**
 * What the AI understood from one distinct blurt: the text of that blurt
 * (when a long capture splits into several), an intent, a topic, an optional
 * point in time, whether it repeats, and whether the user called it out as
 * important.
 */
data class CaptureAnalysis(
    /** What the user meant — note, task, idea or reminder. */
    val intent: CaptureIntent = CaptureIntent.NOTE,
    /** The topic from the fixed [CaptureCategory] list. */
    val category: CaptureCategory = CaptureCategory.OTHER,
    /** Epoch millis when the blurt mentioned a concrete time; null otherwise. */
    val reminderAt: Long?,
    /** True when phrasing like \"don't forget\" / \"important\" marks the blurt. */
    val important: Boolean = false,
    /**
     * The extracted sub-blurt text when a long capture splits into several
     * distinct ideas; null when the whole input is a single blurt.
     */
    val content: String? = null,
    /** How the reminder repeats — NONE for a one-shot, DAILY/WEEKLY otherwise. */
    val recurrence: Recurrence = Recurrence.NONE,
)

/**
 * What a companion capture round-trip produced: a short spoken line the
 * assistant says back, whether anything should be saved, and the structured
 * analyses (empty when nothing is worth keeping).
 */
data class ConversationResult(
    /** The assistant's spoken line; null when the backend only classified. */
    val reply: String?,
    /** False when the user said not to save (or nothing is worth keeping). */
    val save: Boolean,
    /** The blurts worth keeping; empty when [save] is false. */
    val analyses: List<CaptureAnalysis>,
)

/**
 * Reads a blurt and assigns it an intent + category (and optionally a time
 * and a recurrence). A capture may hold several distinct ideas, so the
 * analyzer returns one [CaptureAnalysis] per blurt. A null result — or a
 * thrown exception — means \"classification unavailable right now\", and the
 * caller saves the blurt unclassified instead of failing.
 */
interface CaptureAnalyzer {

    /** Returns null when the analyzer can't run (offline, no key, quota, …). */
    suspend fun analyze(content: String, nowEpochMillis: Long): List<CaptureAnalysis>?

    /**
     * Like [analyze], but also asks the backend for a short spoken reply and
     * a save/discard decision — the companion mode that powers "Talk to
     * Blurt". The default keeps the classic behavior: classify only, save
     * everything, no reply. Providers that support the companion contract
     * (Groq) override this with a single combined call. Returns null when
     * the analyzer can't run at all.
     */
    suspend fun analyzeWithReply(content: String, nowEpochMillis: Long): ConversationResult? {
        val analyses = analyze(content, nowEpochMillis) ?: return null
        return ConversationResult(reply = null, save = true, analyses = analyses)
    }
}
