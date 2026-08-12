package com.blurt.app.ai

import com.blurt.app.data.model.CaptureCategory

/** What the AI understood from a blurt: a topic plus an optional point in time. */
data class CaptureAnalysis(
    /** The topic from the fixed [CaptureCategory] list. */
    val category: CaptureCategory,
    /** Epoch millis when the blurt mentioned a concrete time; null otherwise. */
    val reminderAt: Long?,
)

/**
 * Reads a blurt and assigns it a category (and optionally a time). The
 * implementation is swappable (Gemini today). A null result — or a thrown
 * exception — means "classification unavailable right now", and the caller
 * saves the blurt uncategorized instead of failing.
 */
interface CaptureAnalyzer {

    /** Returns null when the analyzer can't run (offline, no key, quota, …). */
    suspend fun analyze(content: String, nowEpochMillis: Long): CaptureAnalysis?
}
