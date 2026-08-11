package com.blurt.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Owns the image files attached to image blurts.
 *
 * The system photo picker grants only *temporary* read access to the picked
 * [content://][Uri]s — the grant is revoked on reboot, so a note that stores
 * the picked URI directly shows a broken image after the phone restarts.
 *
 * To make image blurts survive restarts we copy the bytes into the app's
 * private storage at save time and store the resulting [file://][Uri] in the
 * database instead.
 */
class ImageStore(private val context: Context) {

    private val imagesDir: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    /** Copies [source] into private storage and returns its local file URI. */
    fun storeImage(source: Uri): Uri {
        val mime = context.contentResolver.getType(source)
        val target = File(imagesDir, "blurt_${UUID.randomUUID()}.${extensionFor(mime)}")
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Cannot open image source: $source")
        input.use { sourceStream ->
            target.outputStream().use { sourceStream.copyTo(it) }
        }
        return Uri.fromFile(target)
    }

    /** Deletes a stored image file, but only if it is one of ours. */
    fun deleteImage(uri: Uri?) {
        if (uri?.scheme != "file") return
        val file = uri.path?.let(::File) ?: return
        val root = imagesDir.absolutePath
        if (file.absolutePath.startsWith("$root${File.separator}")) {
            // A just-written file can be briefly locked (e.g. by antivirus on
            // some platforms); retry a few times before giving up.
            repeat(5) {
                if (file.delete() || !file.exists()) return
                Thread.sleep(100)
            }
        }
    }

    private fun extensionFor(mime: String?): String = when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        "image/avif" -> "avif"
        else -> "img"
    }
}
