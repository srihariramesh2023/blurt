package com.blurt.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached Gemini embedding for one capture, so blurts are embedded once and
 * reused across searches. Vectors are stored as a compact BLOB via
 * [EmbeddingConverter]. Rows are scoped by [ownerId] like captures, so one
 * user's vectors can never leak into another user's search.
 */
@Entity(
    tableName = "capture_embeddings",
    indices = [Index("ownerId")],
)
data class EmbeddingEntity(
    @PrimaryKey val captureId: Long,
    val ownerId: String,
    /** The 3072-dim vector for gemini-embedding-001, serialized as floats. */
    val embedding: FloatArray,
    /** Model name the vector came from; mismatches trigger re-embedding. */
    val model: String,
    val updatedAt: Long,
)
