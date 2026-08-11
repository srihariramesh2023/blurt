package com.blurt.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(embeddings: List<EmbeddingEntity>)

    /** Embeddings for the owner's active (non-deleted) captures. */
    @Query(
        "SELECT e.* FROM capture_embeddings e " +
            "INNER JOIN captures c ON c.id = e.captureId " +
            "WHERE e.ownerId = :ownerId AND c.deletedAt IS NULL"
    )
    suspend fun getAllForOwner(ownerId: String): List<EmbeddingEntity>

    /**
     * Embeddings that are missing or stale (wrong model or content edited
     * after the vector was computed).
     */
    @Query(
        "SELECT c.id, c.ownerId, c.content, c.updatedAt FROM captures c " +
            "LEFT JOIN capture_embeddings e ON e.captureId = c.id " +
            "WHERE c.ownerId = :ownerId AND c.deletedAt IS NULL " +
            "AND (e.captureId IS NULL OR e.model != :model OR e.updatedAt < c.updatedAt)"
    )
    suspend fun getMissing(ownerId: String, model: String): List<MissingEmbedding>

    /** Drops vectors when their capture is hard-deleted after backend sync. */
    @Query("DELETE FROM capture_embeddings WHERE captureId = :captureId")
    suspend fun deleteByCaptureId(captureId: Long)

    /** Drops every vector for a user on sign-out, so no data lingers. */
    @Query("DELETE FROM capture_embeddings WHERE ownerId = :ownerId")
    suspend fun deleteForOwner(ownerId: String)
}

/** A capture that still needs a fresh vector (content edited or never embedded). */
data class MissingEmbedding(
    val id: Long,
    val ownerId: String,
    val content: String,
    val updatedAt: Long,
)
