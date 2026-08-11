package com.blurt.app.data

import android.net.Uri
import com.blurt.app.data.local.CaptureDao
import com.blurt.app.data.local.CaptureEntity
import com.blurt.app.data.local.SyncState
import com.blurt.app.data.local.toDomain
import com.blurt.app.data.model.Capture
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
    private val imageStore: ImageStore,
) {

    fun observeAll(ownerId: String): Flow<List<Capture>> =
        dao.observeForOwner(ownerId).map { list -> list.map(CaptureEntity::toDomain) }

    fun observeById(id: Long, ownerId: String): Flow<Capture?> =
        dao.observeById(id, ownerId).map { it?.toDomain() }

    fun search(query: String, ownerId: String): Flow<List<Capture>> =
        dao.search(query.escapeLikePattern(), ownerId).map { list -> list.map(CaptureEntity::toDomain) }

    suspend fun create(ownerId: String, type: CaptureType, content: String, imageUri: Uri?): Long {
        val now = System.currentTimeMillis()
        // Copy the picked image into app-private storage so the note survives
        // reboots (photo-picker URI grants are temporary).
        val storedImageUri = imageUri?.let(imageStore::storeImage)
        return dao.insert(
            CaptureEntity(
                ownerId = ownerId,
                remoteId = UUID.randomUUID().toString(),
                syncState = SyncState.PENDING,
                content = content.trim(),
                type = type,
                imageUri = storedImageUri?.toString(),
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
        // backend delete, so the deletion reaches other devices. The local
        // image file is no longer needed and is cleaned up immediately.
        dao.tombstone(id, System.currentTimeMillis())
        imageStore.deleteImage(entity.imageUri?.let(Uri::parse))
    }

    /** Assigns pre-auth legacy captures to the first user who signs in. */
    suspend fun claimUnowned(ownerId: String) {
        dao.claimUnowned(ownerId)
    }
}
