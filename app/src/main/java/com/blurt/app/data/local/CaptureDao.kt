package com.blurt.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    // Every user-facing query is scoped by ownerId so one authenticated user
    // can never read or mutate another user's captures, and excludes
    // tombstones (rows deleted locally but not yet removed from the backend).

    // id DESC breaks same-millisecond createdAt ties so ordering is deterministic.
    @Query("SELECT * FROM captures WHERE ownerId = :ownerId AND deletedAt IS NULL ORDER BY createdAt DESC, id DESC")
    fun observeForOwner(ownerId: String): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id AND ownerId = :ownerId AND deletedAt IS NULL")
    fun observeById(id: Long, ownerId: String): Flow<CaptureEntity?>

    @Query("SELECT * FROM captures WHERE id = :id AND ownerId = :ownerId AND deletedAt IS NULL")
    suspend fun getById(id: Long, ownerId: String): CaptureEntity?

    @Query(
        "SELECT * FROM captures WHERE ownerId = :ownerId AND deletedAt IS NULL " +
            "AND content LIKE '%' || :query || '%' " +
            "ESCAPE '\\' COLLATE NOCASE ORDER BY createdAt DESC, id DESC"
    )
    fun search(query: String, ownerId: String): Flow<List<CaptureEntity>>

    /** One-shot keyword search — the fallback when semantic search is down. */
    @Query(
        "SELECT * FROM captures WHERE ownerId = :ownerId AND deletedAt IS NULL " +
            "AND content LIKE '%' || :query || '%' " +
            "ESCAPE '\\' COLLATE NOCASE ORDER BY createdAt DESC, id DESC"
    )
    suspend fun searchOnce(query: String, ownerId: String): List<CaptureEntity>

    @Insert
    suspend fun insert(capture: CaptureEntity): Long

    @Update
    suspend fun update(capture: CaptureEntity)

    @Query("DELETE FROM captures WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteById(id: Long, ownerId: String)

    /**
     * Legacy captures created before authentication carry no owner; assign
     * them to the first user who signs in on this device so nothing is lost.
     * Idempotent — safe to call on every sign-in.
     */
    @Query("UPDATE captures SET ownerId = :ownerId WHERE ownerId IS NULL")
    suspend fun claimUnowned(ownerId: String)

    // --- sync ----------------------------------------------------------------

    @Query("SELECT * FROM captures WHERE ownerId = :ownerId AND syncState = 'PENDING' AND deletedAt IS NULL ORDER BY updatedAt ASC")
    fun observePendingUploads(ownerId: String): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE ownerId = :ownerId AND deletedAt IS NOT NULL ORDER BY deletedAt ASC")
    fun observePendingDeletes(ownerId: String): Flow<List<CaptureEntity>>

    /** One-shot variants used by the sync engine's retry loop. */
    @Query("SELECT * FROM captures WHERE ownerId = :ownerId AND syncState = 'PENDING' AND deletedAt IS NULL ORDER BY updatedAt ASC")
    suspend fun getPendingUploads(ownerId: String): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE ownerId = :ownerId AND deletedAt IS NOT NULL ORDER BY deletedAt ASC")
    suspend fun getPendingDeletes(ownerId: String): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE ownerId = :ownerId AND deletedAt IS NULL")
    suspend fun getAllActive(ownerId: String): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE remoteId = :remoteId AND ownerId = :ownerId AND deletedAt IS NULL LIMIT 1")
    suspend fun getByRemoteId(remoteId: String, ownerId: String): CaptureEntity?

    @Query("UPDATE captures SET remoteId = :remoteId WHERE id = :id")
    suspend fun assignRemoteId(id: Long, remoteId: String)

    @Query("UPDATE captures SET syncState = 'SYNCED' WHERE remoteId = :remoteId AND deletedAt IS NULL")
    suspend fun markSynced(remoteId: String)

    @Query("UPDATE captures SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun tombstone(id: Long, deletedAt: Long)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun hardDelete(id: Long)

    /** Raw lookup including tombstones — used by tests to inspect sync state. */
    @Query("SELECT * FROM captures WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingDeleted(id: Long): CaptureEntity?

    // --- AI categorization ------------------------------------------------

    /**
     * Captures the analyzer hasn't tagged yet, for lazy backfill. Links are
     * classified by rule (a URL is a Link), never by AI, so they're skipped.
     */
    @Query(
        "SELECT * FROM captures WHERE ownerId = :ownerId AND category IS NULL " +
            "AND type != 'LINK' AND deletedAt IS NULL ORDER BY createdAt ASC"
    )
    suspend fun getUncategorized(ownerId: String): List<CaptureEntity>

    /** Assigns an AI category; the row re-syncs so it reaches other devices. */
    @Query(
        "UPDATE captures SET category = :category, updatedAt = :updatedAt, " +
            "syncState = 'PENDING' WHERE id = :id AND ownerId = :ownerId"
    )
    suspend fun setCategory(id: Long, ownerId: String, category: String, updatedAt: Long)

    /** Future reminders of the user — used to reschedule after a reboot. */
    @Query(
        "SELECT * FROM captures WHERE ownerId = :ownerId AND deletedAt IS NULL " +
            "AND reminderAt IS NOT NULL AND reminderAt > :now ORDER BY reminderAt ASC"
    )
    suspend fun getUpcomingReminders(ownerId: String, now: Long): List<CaptureEntity>
}
