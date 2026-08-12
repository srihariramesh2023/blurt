package com.blurt.app.data.model

import java.time.Instant

/**
 * A single saved blurt.
 *
 * [content] holds the text (for TEXT/IDEA) or the URL (for LINK).
 * [category] is the AI-assigned topic from the fixed [CaptureCategory] list
 * (null until the analyzer has run on it — e.g. saved offline).
 * [reminderAt] is when a priority Blurt notification was scheduled for, or
 * null when no time was detected or the user declined.
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
    val reminderAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
