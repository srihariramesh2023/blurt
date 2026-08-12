package com.blurt.app.data

import com.blurt.app.data.local.CaptureDao
import com.blurt.app.data.local.CaptureEntity
import com.blurt.app.data.local.SyncState
import com.blurt.app.data.local.toDomain
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureType
import com.blurt.app.util.escapeLikePattern
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for captures. UI flows react to Room changes
 * automatically, so new/edited/deleted blurts show up without refresh logic.
 *
 * Every operation is scoped by [ownerId] (the authenticated user's UID), so
 * data isolation is enforced at the database layer — one user can never read
 * or mutate another user's captures, even if the UI is bypassed.
 *
 * Captures are created with a sync state of PENDING and a device-generated
 * UUID document id; the sync engine pushes them to the backend and marks them
 * SYNCED. Deletes are tombstones until the backend delete is confirmed.
 */
class CaptureRepository(
    private val dao: CaptureDao,
) {

    fun observeAll(ownerId: String): Flow<List<Capture>> =
        dao.observeForOwner(ownerId).map { list -> list.map(CaptureEntity::toDomain) }

    fun observeById(id: Long, ownerId: String): Flow<Capture?> =
        dao.observeById(id, ownerId).map { it?.toDomain() }

    fun search(query: String, ownerId: String): Flow<List<Capture>> =
        dao.search(query.escapeLikePattern(), ownerId).map { list -> list.map(CaptureEntity::toDomain) }

    /** One-shot keyword search — the fallback when semantic search is down. */
    suspend fun searchOnce(query: String, ownerId: String): List<Capture> =
        dao.searchOnce(query.escapeLikePattern(), ownerId).map(CaptureEntity::toDomain)

    suspend fun create(
        ownerId: String,
        type: CaptureType,
        content: String,
        category: CaptureCategory? = null,
        reminderAt: Long? = null,
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            CaptureEntity(
                ownerId = ownerId,
                remoteId = UUID.randomUUID().toString(),
                syncState = SyncState.PENDING,
                content = content.trim(),
                type = type,
                category = category?.name,
                reminderAt = reminderAt,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun updateContent(id: Long, ownerId: String, content: String) {
        val entity = dao.getById(id, ownerId) ?: return
        dao.update(
            entity.copy(
                content = content.trim(),
                updatedAt = System.currentTimeMillis(),
                // Edits must re-sync, even if the row was already uploaded.
                syncState = SyncState.PENDING,
            )
        )
    }

    suspend fun delete(id: Long, ownerId: String) {
        val entity = dao.getById(id, ownerId) ?: return
        // Tombstone first: the row stays until the sync engine confirms the
        // backend delete, so the deletion reaches other devices.
        dao.tombstone(id, System.currentTimeMillis())
    }

    /** Assigns pre-auth legacy captures to the first user who signs in. */
    suspend fun claimUnowned(ownerId: String) {
        dao.claimUnowned(ownerId)
    }

    /** Captures the AI hasn't tagged yet — links are classified by rule, never by AI. */
    suspend fun getUncategorized(ownerId: String): List<Capture> =
        dao.getUncategorized(ownerId).map(CaptureEntity::toDomain)

    /** Assigns an AI category and re-queues the row for sync. */
    suspend fun setCategory(id: Long, ownerId: String, category: CaptureCategory) {
        dao.setCategory(id, ownerId, category.name, System.currentTimeMillis())
    }

    /** Future reminders of the user — the boot receiver reschedules these. */
    suspend fun getUpcomingReminders(ownerId: String): List<Capture> =
        dao.getUpcomingReminders(ownerId, System.currentTimeMillis()).map(CaptureEntity::toDomain)
}
