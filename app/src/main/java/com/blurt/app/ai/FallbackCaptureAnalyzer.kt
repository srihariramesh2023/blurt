package com.blurt.app.ai

/**
 * Tries [primary] first; when it fails (rate limit, outage, quota — anything
 * that yields null or throws), falls back to [secondary]. This is how Groq
 * carries Gemini as a safety net: the fast, quota-generous provider answers
 * normally, and a Groq outage silently rolls to Gemini without the user
 * noticing — the caller only ever sees "analysis or null".
 */
class FallbackCaptureAnalyzer(
    private val primary: CaptureAnalyzer,
    private val secondary: CaptureAnalyzer,
) : CaptureAnalyzer {

    override suspend fun analyze(content: String, nowEpochMillis: Long): List<CaptureAnalysis>? {
        val first = runCatching { primary.analyze(content, nowEpochMillis) }.getOrNull()
        if (first != null) return first
        android.util.Log.w(TAG, "primary (${primary::class.simpleName}) failed; falling back to ${secondary::class.simpleName}")
        return runCatching { secondary.analyze(content, nowEpochMillis) }.getOrNull()
    }

    /**
     * Companion mode follows the same chain: Groq answers with a reply and a
     * save decision; if Groq can't, Gemini's default classifies only (no
     * reply — a silent save, exactly as before).
     */
    override suspend fun analyzeWithReply(content: String, nowEpochMillis: Long): ConversationResult? {
        val first = runCatching { primary.analyzeWithReply(content, nowEpochMillis) }.getOrNull()
        if (first != null) return first
        android.util.Log.w(TAG, "primary (${primary::class.simpleName}) failed; falling back to ${secondary::class.simpleName}")
        return runCatching { secondary.analyzeWithReply(content, nowEpochMillis) }.getOrNull()
    }

    private companion object {
        const val TAG = "BlurtFallback"
    }
}
