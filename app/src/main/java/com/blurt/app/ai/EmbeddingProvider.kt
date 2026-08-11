package com.blurt.app.ai

/**
 * Turns text into vectors. The implementation is swappable (Gemini today,
 * something on-device later) and everything above it only cares about the
 * vector contract.
 */
interface EmbeddingProvider {

    /**
     * Returns one vector per input text, in the same order. A [null] result
     * (or a thrown exception) means "semantic search unavailable right now" —
     * callers fall back to plain keyword search instead of failing.
     */
    suspend fun embed(texts: List<String>): List<FloatArray>?
}
