package com.blurt.app.data.sync

import com.blurt.app.data.model.CaptureType

/** The wire shape of a capture as stored on the backend. */
data class RemoteCapture(
    val remoteId: String,
    val content: String,
    val type: CaptureType,
    val createdAt: Long,
    val updatedAt: Long,
    /** AI-assigned topic (enum name); null until classified. */
    val category: String? = null,
    /** When a reminder notification was scheduled on some device; null otherwise. */
    val reminderAt: Long? = null,
    /** Deletes are explicit tombstones so other devices can detect them. */
    val deleted: Boolean = false,
)
