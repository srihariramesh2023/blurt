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
    /** AI-assigned intent (enum name); null until classified. */
    val intent: String? = null,
    /** When a reminder notification was scheduled on some device; null otherwise. */
    val reminderAt: Long? = null,
    /** User-marked important; the gold star travels across devices. */
    val isImportant: Boolean = false,
    /** Hidden from main lists on every device, browsable in Library → Archived. */
    val isArchived: Boolean = false,
    /** When a reminder blurt was marked done (null = not done). */
    val completedAt: Long? = null,
    /** Deletes are explicit tombstones so other devices can detect them. */
    val deleted: Boolean = false,
)
