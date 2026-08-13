package com.blurt.app.data.model

import java.time.Instant

/**
 * A single saved blurt.
 *
 * [content] holds the text (for TEXT/IDEA) or the URL (for LINK).
 * [category] is the AI-assigned topic from the fixed [CaptureCategory] list
 * (null until the analyzer has run on it — e.g. saved offline).
 * [intent] is what the user meant — note, task, idea, reminder — from the
 * fixed [CaptureIntent] list (null until classified).
 * [reminderAt] is when a priority Blurt notification was scheduled for, or
 * null when no time was detected or the user declined.
 * [isImportant] marks blurts the user (or the AI, from phrasing like "don't
 * forget") called out as important; [isArchived] hides a blurt from the main
 * lists while keeping it browsable in Library → Archived; [completedAt] is
 * when a reminder blurt was marked done (cancels its alarm and removes it
 * from the Reminders collection).
 */
data class Capture(
    val id: Long,
    /** The authenticated user this capture belongs to (null only for pre-auth legacy rows). */
    val ownerId: String?,
    /** Backend document id (null before the first sync assigns one). */
    val remoteId: String?,
    val content: String,
    val type: CaptureType,
    val category: CaptureCategory? = null,
    val intent: CaptureIntent? = null,
    val reminderAt: Instant? = null,
    val isImportant: Boolean = false,
    val isArchived: Boolean = false,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
