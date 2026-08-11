package com.blurt.app.data.model

import java.time.Instant

/**
 * A single saved blurt.
 *
 * [content] holds the text (for TEXT/IDEA) or the URL (for LINK).
 */
data class Capture(
    val id: Long,
    /** The authenticated user this capture belongs to (null only for pre-auth legacy rows). */
    val ownerId: String?,
    /** Backend document id (null before the first sync assigns one). */
    val remoteId: String?,
    val content: String,
    val type: CaptureType,
    val createdAt: Instant,
    val updatedAt: Instant,
)
