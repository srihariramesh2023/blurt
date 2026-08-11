package com.blurt.app.ai

import com.blurt.app.data.local.CaptureDao
import com.blurt.app.data.local.CaptureEntity
import com.blurt.app.data.local.EmbeddingDao
import com.blurt.app.data.local.EmbeddingEntity
import kotlin.math.sqrt

/**
 * Meaning-based search on top of cached Gemini vectors.
 *
 * The first search lazily embeds every active capture of the user (one batched
 * call), stores the vectors locally, and re-embeds only what changed. After
 * that, a search embeds the query and ranks by cosine similarity — blurts are
 * findable by meaning, not just by the words they contain.
 *
 * Every failure path returns null (not an empty list) so the caller can fall
 * back to keyword search: no key configured, network down, quota exceeded.
 */
class SemanticSearchEngine(
    private val dao: CaptureDao,
    private val embeddingDao: EmbeddingDao,
    private val provider: EmbeddingProvider?,
) {

    suspend fun search(query: String, ownerId: String): List<CaptureEntity>? {
        if (provider == null || query.isBlank()) return null
        return try {
            // 1. Backfill any captures that were never embedded or were edited
            //    since their last vector was computed.
            val missing = embeddingDao.getMissing(ownerId, GeminiEmbeddingProvider.MODEL)
            if (missing.isNotEmpty()) {
                val vectors = provider.embed(missing.map { it.content }) ?: return null
                embeddingDao.upsertAll(
                    missing.zip(vectors).map { (m, v) ->
                        EmbeddingEntity(
                            captureId = m.id,
                            ownerId = m.ownerId,
                            embedding = v,
                            // Store the content version this vector represents, so
                            // staleness is an exact comparison — no clock ties.
                            model = GeminiEmbeddingProvider.MODEL,
                            updatedAt = m.updatedAt,
                        )
                    }
                )
            }

            // 2. Embed the query with the retrieval-query task type.
            val queryVector = (provider as? GeminiEmbeddingProvider)
                ?.embedQuery(query)
                ?: provider.embed(listOf(query))?.firstOrNull()
            if (queryVector == null) return null

            // 3. Rank active captures by cosine similarity to the query.
            val active = dao.getAllActive(ownerId).associateBy { it.id }
            embeddingDao.getAllForOwner(ownerId)
                .asSequence()
                .mapNotNull { e ->
                    val capture = active[e.captureId] ?: return@mapNotNull null
                    // Skip vectors older than the capture's last edit — the
                    // content changed but the backfill hasn't run yet.
                    if (e.updatedAt < capture.updatedAt) return@mapNotNull null
                    capture to cosine(e.embedding, queryVector)
                }
                .filter { it.second > 0f }
                .sortedWith(
                    compareByDescending<Pair<CaptureEntity, Float>> { it.second }
                        .thenByDescending { it.first.updatedAt }
                )
                .take(RESULT_LIMIT)
                .map { it.first }
                .toList()
        } catch (_: Exception) {
            null
        }
    }

    /** Drops all cached vectors for a user (called on sign-out). */
    suspend fun clearForUser(ownerId: String) {
        embeddingDao.deleteForOwner(ownerId)
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return dot / (sqrt(normA) * sqrt(normB))
    }

    private companion object {
        const val RESULT_LIMIT = 30
    }
}
