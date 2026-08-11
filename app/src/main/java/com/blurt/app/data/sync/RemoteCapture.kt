package com.blurt.app.data.sync

import com.blurt.app.data.model.CaptureType

/** The wire format of a capture stored in the Realtime Database. */
data class RemoteCapture(
    val remoteId: String,
    val content: String,
    val type: CaptureType,
    val createdAt: Long,
    val updatedAt: Long,
    /** Deletes are explicit tombstones so other devices can detect them. */
    val deleted: Boolean = false,
)
