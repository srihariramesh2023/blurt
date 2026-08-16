package com.blurt.app.data.sync

import com.blurt.app.auth.AuthState
import com.blurt.app.data.local.CaptureDao
import com.blurt.app.data.local.CaptureEntity
import com.blurt.app.data.local.SyncState
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bidirectional sync between the local Room database and the backend.
 *
 * - Captures created or edited locally are PENDING and are pushed to the
 *   backend, then marked SYNCED.
 * - Deletes are tombstones locally; once the backend delete is confirmed the
 *   row is removed, so deletions propagate to other devices.
 * - Remote changes (from other devices of the same user) are merged in, and a
 *   remote delete removes the local copy.
 * - Conflicts are last-write-wins by `updatedAt`, except that a local PENDING
 *   row always wins (it is a not-yet-uploaded local edit).
 *
 * Runs only while a user is signed in and the backend is configured; with no
 * Firebase config the app stays purely local, exactly as before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngine(
    private val scope: CoroutineScope,
    private val dao: CaptureDao,
    private val remote: CaptureRemote,
    private val authState: StateFlow<AuthState>,
) {
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            authState.flatMapLatest { state ->
                val uid = (state as? AuthState.SignedIn)?.user?.uid
                if (uid == null || !remote.isConfigured) flowOf(Unit)
                else syncLoop(uid)
            }.collect { }
        }
    }

    private fun syncLoop(uid: String): Flow<Unit> = flow {
        // All DB and remote changes funnel through here, processed serially.
        // The mutex (not flatMapLatest) prevents a new trigger from cancelling
        // an in-flight upload mid-write, and state is re-queried inside the
        // lock so nothing is processed twice or from a stale snapshot.
        val mutex = Mutex()
        combine(
            dao.observePendingUploads(uid),
            dao.observePendingDeletes(uid),
            remote.observeAll(uid),
        ) { uploads, deletes, remoteDocs -> Triple(uploads, deletes, remoteDocs) }
            .collect { (_, _, remoteDocs) ->
                // The mutex (not flatMapLatest) prevents a new trigger from
                // cancelling an in-flight upload mid-write, and state is
                // re-queried inside the lock so nothing is processed twice.
                mutex.withLock { processOnce(uid, remoteDocs) }
            }
    }

    /**
     * One sync pass: upload pending rows, drain tombstones, merge remote
     * changes. Internal so tests can drive it deterministically.
     */
    internal suspend fun processOnce(uid: String, remoteDocs: List<RemoteCapture>) {
        dao.getPendingUploads(uid).forEach { upload(uid, it) }
        // Merge before draining deletes: while a tombstone still exists it
        // blocks a stale remote doc from resurrecting the row. Once the
        // remote delete is confirmed the row is hard-deleted for good.
        mergeRemote(uid, remoteDocs)
        dao.getPendingDeletes(uid).forEach { drainDelete(uid, it) }
    }

    private suspend fun upload(uid: String, capture: CaptureEntity) {
        val remoteId = capture.remoteId ?: UUID.randomUUID().toString().also {
            dao.assignRemoteId(capture.id, it)
        }
        try {
            remote.uploadCapture(
                uid,
                RemoteCapture(
                    remoteId = remoteId,
                    content = capture.content,
                    type = capture.type,
                    category = capture.category,
                    intent = capture.intent,
                    reminderAt = capture.reminderAt,
                    recurrence = capture.recurrence,
                    isImportant = capture.isImportant,
                    isArchived = capture.isArchived,
                    completedAt = capture.completedAt,
                    createdAt = capture.createdAt,
                    updatedAt = capture.updatedAt,
                ),
            )
            dao.markSynced(remoteId)
        } catch (e: Exception) {
            // Row stays PENDING; a later trigger retries it.
        }
    }

    private suspend fun drainDelete(uid: String, tombstone: CaptureEntity) {
        val remoteId = tombstone.remoteId
        if (remoteId != null) {
            try {
                remote.deleteCapture(uid, remoteId)
            } catch (e: Exception) {
                return // stay a tombstone; retry on the next pass
            }
        }
        dao.hardDelete(tombstone.id)
    }

    private suspend fun mergeRemote(uid: String, remoteDocs: List<RemoteCapture>) {
        // Tombstones included: a row the user deleted locally must never be
        // mistaken for "missing" and re-inserted from a stale remote snapshot.
        val localByRemoteId = dao.getAllIncludingDeleted(uid).associateBy { it.remoteId }

        for (remoteCapture in remoteDocs) {
            val local = localByRemoteId[remoteCapture.remoteId]
            when {
                // A local tombstone means the user deleted it — the local
                // delete wins, exactly like a PENDING local edit does.
                local?.deletedAt != null -> Unit

                // Deletions are explicit tombstones from another device.
                remoteCapture.deleted -> {
                    if (local != null && local.syncState != SyncState.PENDING) {
                        dao.hardDelete(local.id)
                    }
                }

                local == null -> dao.insert(remoteCapture.toEntity(uid))
                local.syncState == SyncState.PENDING -> Unit // local edit wins; the upload will overwrite the remote
                remoteCapture.updatedAt > local.updatedAt -> {
                    android.util.Log.w(
                        TAG,
                        "OVERWRITE id=${local.id} remote.updatedAt=${remoteCapture.updatedAt} > local.updatedAt=${local.updatedAt} " +
                            "remote.intent=${remoteCapture.intent}",
                    )
                    dao.update(
                    local.copy(
                        content = remoteCapture.content,
                        type = remoteCapture.type,
                        category = remoteCapture.category,
                        intent = remoteCapture.intent,
                        reminderAt = remoteCapture.reminderAt,
                        recurrence = remoteCapture.recurrence,
                        isImportant = remoteCapture.isImportant,
                        isArchived = remoteCapture.isArchived,
                        completedAt = remoteCapture.completedAt,
                        updatedAt = remoteCapture.updatedAt,
                        syncState = SyncState.SYNCED,
                    )
                )
                }
            }
        }
    }

    private companion object {
        const val TAG = "BlurtSync"
    }
}

private fun RemoteCapture.toEntity(ownerId: String): CaptureEntity = CaptureEntity(
    ownerId = ownerId,
    remoteId = remoteId,
    syncState = SyncState.SYNCED,
    content = content,
    type = type,
    category = category,
    intent = intent,
    reminderAt = reminderAt,
    recurrence = recurrence,
    isImportant = isImportant,
    isArchived = isArchived,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
