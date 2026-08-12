package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent

/**
 * What the AI understood from a blurt: an intent, a topic, an optional point
 * in time, and whether the user called it out as important.
 */
data class CaptureAnalysis(
    /** What the user meant — note, task, idea or reminder. */
    val intent: CaptureIntent,
    /** The topic from the fixed [CaptureCategory] list. */
    val category: CaptureCategory,
    /** Epoch millis when the blurt mentioned a concrete time; null otherwise. */
    val reminderAt: Long?,
    /** True when phrasing like \"don't forget\" / \"important\" marks the blurt. */
    val important: Boolean = false,
)

/**
 * Reads a blurt and assigns it an intent + category (and optionally a time).
 * The implementation is swappable (Gemini today). A null result — or a thrown
 * exception — means \"classification unavailable right now\", and the caller
 * saves the blurt unclassified instead of failing.
 */
interface CaptureAnalyzer {

    /** Returns null when the analyzer can't run (offline, no key, quota, …). */
    suspend fun analyze(content: String, nowEpochMillis: Long): CaptureAnalysis?
}
