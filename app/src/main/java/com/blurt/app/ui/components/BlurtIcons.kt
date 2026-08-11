package com.blurt.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.blurt.app.data.model.CaptureType

/**
 * Blurt's hand-drawn icon set — keeps the app dependency-light (no
 * material-icons-extended) and gives it a distinct visual identity.
 */
object BlurtIcons {

    /** Rounded speech bubble with a spark — the Blurt mark. */
    val BlurtMark: ImageVector by lazy {
        icon(
            name = "BlurtMark",
            pathData = "M12 3C6.48 3 2 6.58 2 11c0 2.52 1.35 4.76 3.42 6.23L4 21l4.05-1.5" +
                "c1.24.35 2.57.5 3.95.5 5.52 0 10-3.58 10-8s-4.48-8-10-8z" +
                "M12 8.5l1.35 2.65L16 12.5l-2.65 1.35L12 16.5l-1.35-2.65L8 12.5l2.65-1.35L12 8.5z",
        )
    }

    /** Quote glyph for text captures. */
    val Quote: ImageVector by lazy {
        icon(
            name = "Quote",
            pathData = "M6 17h3l2-4V7H5v6h3l-2 4zm8 0h3l2-4V7h-6v6h3l-2 4z",
        )
    }

    /** Lightbulb for idea captures. */
    val Idea: ImageVector by lazy {
        icon(
            name = "Idea",
            pathData = "M12 2C9.19 2 7 4.19 7 7c0 2.11 1.18 3.95 2.91 4.92L10 14h4l.09-2.08" +
                "C15.82 10.95 17 9.11 17 7c0-2.81-2.19-5-5-5zM9 16h6v1.5H9V16zm0 3.5h6V21H9v-1.5z",
        )
    }

    /** Chain link for link captures. */
    val Link: ImageVector by lazy {
        icon(
            name = "Link",
            pathData = "M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4" +
                "v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39" +
                "3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z",
        )
    }

    /** Photo glyph for image captures. */
    val Image: ImageVector by lazy {
        icon(
            name = "Image",
            pathData = "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0" +
                "2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z",
        )
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black), // tint replaces fill at draw time
        ).build()
}

/** Icon for a given capture type. */
fun typeIcon(type: CaptureType): ImageVector = when (type) {
    CaptureType.TEXT -> BlurtIcons.Quote
    CaptureType.IDEA -> BlurtIcons.Idea
    CaptureType.LINK -> BlurtIcons.Link
    CaptureType.IMAGE -> BlurtIcons.Image
}
