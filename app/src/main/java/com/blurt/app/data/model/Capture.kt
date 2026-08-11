package com.blurt.app.data.model

import android.net.Uri
import java.time.Instant

/**
 * A single saved blurt.
 *
 * [content] holds the text (for TEXT/IDEA), the URL (for LINK), or an optional
 * caption (for IMAGE). [imageUri] is the on-device file for IMAGE captures;
 * [imageUrl] is the backend URL used to display the image on other devices.
 */
data class Capture(
    val id: Long,
    /** The authenticated user this capture belongs to (null only for pre-auth legacy rows). */
    val ownerId: String?,
    /** Backend document id (null before the first sync assigns one). */
    val remoteId: String?,
    val content: String,
    val type: CaptureType,
    val imageUri: Uri?,
    val imageUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Best image source for display: the local file when present, else the backend URL. */
    val imageForDisplay: Uri?
        get() = imageUri ?: imageUrl?.let(Uri::parse)
}
