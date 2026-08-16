package com.blurt.app.data

import com.blurt.app.data.local.CaptureDao
import com.blurt.app.data.local.CaptureEntity
import com.blurt.app.data.local.SyncState
import com.blurt.app.data.local.toDomain
import com.blurt.app.data.model.Capture
import com.blurt.app.data.model.CaptureCategory
import com.blurt.app.data.model.CaptureIntent
import com.blurt.app.data.model.CaptureType
import com.blurt.app.data.model.Recurrence
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

    /** Archived blurts only — browsed from Library → Archived. */
    fun observeArchived(ownerId: String): Flow<List<Capture>> =
        dao.observeArchived(ownerId).map { list -> list.map(CaptureEntity::toDomain) }

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
        intent: CaptureIntent? = null,
        reminderAt: Long? = null,
        isImportant: Boolean = false,
        recurrence: Recurrence = Recurrence.NONE,
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
                intent = intent?.name,
                reminderAt = reminderAt,
                recurrence = recurrence.takeIf { it != Recurrence.NONE }?.name,
                isImportant = isImportant,
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

    /** Captures the analyzer hasn't read yet — links are classified by rule, never by AI. */
    suspend fun getUnanalyzed(ownerId: String): List<Capture> =
        dao.getUnanalyzed(ownerId).map(CaptureEntity::toDomain)

    /** Assigns the AI analysis (category + intent) and re-queues the row for sync. */
    suspend fun setAnalysis(id: Long, ownerId: String, category: CaptureCategory, intent: CaptureIntent?) {
        dao.setAnalysis(id, ownerId, category.name, intent?.name, System.currentTimeMillis())
    }

    /** Marks/unmarks a blurt as important and re-queues the row for sync. */
    suspend fun setImportant(id: Long, ownerId: String, important: Boolean) {
        dao.setImportant(id, ownerId, important, System.currentTimeMillis())
    }

    /** Archives/unarchives a blurt and re-queues the row for sync. */
    suspend fun setArchived(id: Long, ownerId: String, archived: Boolean) {
        dao.setArchived(id, ownerId, archived, System.currentTimeMillis())
    }

    /** Marks a blurt done (or reopens it) and re-queues the row for sync. */
    suspend fun setCompleted(id: Long, ownerId: String, completed: Boolean) {
        val at = if (completed) System.currentTimeMillis() else null
        dao.setCompletedAt(id, ownerId, at, System.currentTimeMillis())
    }

    /** Moves a reminder's firing time (snooze) and re-queues the row for sync. */
    suspend fun rescheduleReminder(id: Long, ownerId: String, reminderAt: Long) {
        dao.setReminderAt(id, ownerId, reminderAt, System.currentTimeMillis())
    }

    /** Future reminders of the user — the boot receiver reschedules these. */
    suspend fun getUpcomingReminders(ownerId: String): List<Capture> =
        dao.getUpcomingReminders(ownerId, System.currentTimeMillis()).map(CaptureEntity::toDomain)

    /**
     * Recurring reminders (past or future) — the boot receiver advances past
     * occurrences to the next future one so the chain survives a reboot.
     */
    suspend fun getRecurringReminders(ownerId: String): List<Capture> =
        dao.getRecurringReminders(ownerId).map(CaptureEntity::toDomain)

    /**
     * One-shot reminders that already fired but are still alive — the boot
     * receiver re-arms their auto-delete alarm (cleared on reboot).
     */
    suspend fun getExpiredOneTimeReminders(ownerId: String): List<Capture> {
        val now = System.currentTimeMillis()
        return dao.getExpiredOneTimeReminders(ownerId, now, now - EXPIRED_LOOKBACK_MS)
            .map(CaptureEntity::toDomain)
    }

    private companion object {
        /** How far back an un-deleted one-shot reminder can be and still clean itself up. */
        const val EXPIRED_LOOKBACK_MS = 7 * 24 * 60 * 60_000L
    }
}
