package com.blurt.app.data.sync

import kotlinx.coroutines.flow.Flow

/**
 * The backend boundary for cross-device sync. Implemented by
 * [RtdbCaptureRemote]; tests substitute an in-memory fake.
 *
 * All operations are scoped by the user's UID, and the Realtime Database
 * security rules enforce that same scoping server-side.
 */
interface CaptureRemote {

    /** False when Firebase isn't configured — the sync engine then no-ops. */
    val isConfigured: Boolean

    /** Writes (merges) a capture document under `users/{uid}/captures/{remoteId}`. */
    suspend fun uploadCapture(uid: String, capture: RemoteCapture)

    /**
     * Marks `users/{uid}/captures/{remoteId}` as deleted. The document stays
     * as a tombstone so other devices can detect and apply the deletion.
     * Idempotent.
     */
    suspend fun deleteCapture(uid: String, remoteId: String)

    /** Live stream of the user's capture documents. */
    fun observeAll(uid: String): Flow<List<RemoteCapture>>
}
