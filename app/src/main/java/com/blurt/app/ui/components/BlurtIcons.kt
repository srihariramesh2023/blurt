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

    /** Sun glyph for the Light theme option. */
    val Sun: ImageVector by lazy {
        icon(
            name = "Sun",
            pathData = "M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5z" +
                "M2 13h2v-2H2v2zm18 0h2v-2h-2v2zM11 2h2v2h-2V2zm0 18h2v2h-2v-2zM4.22 19.78l1.42 1.42 1.41-1.41-1.42-1.42-1.41 1.41zM17.66 4.93l1.41-1.41-1.42-1.42-1.41 1.41 1.42 1.42zM4.22 4.22L2.8 5.64l1.41 1.41 1.42-1.42-1.41-1.41zM17.66 17.66l1.42 1.41 1.41-1.41-1.42-1.42-1.41 1.42z",
        )
    }

    /** Moon glyph for the Dark theme option. */
    val Moon: ImageVector by lazy {
        icon(
            name = "Moon",
            pathData = "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z",
        )
    }

    /** Monitor glyph for the System theme option. */
    val Monitor: ImageVector by lazy {
        icon(
            name = "Monitor",
            pathData = "M20 3H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h6v2h-2v2h8v-2h-2v-2h6c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 14H4V5h16v12z",
        )
    }

    /** Sliders glyph for search filters. */
    val Tune: ImageVector by lazy {
        icon(
            name = "Tune",
            pathData = "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
        )
    }

    /** Microphone — the heart of V2. */
    val Mic: ImageVector by lazy {
        icon(
            name = "Mic",
            pathData = "M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z" +
                "M17.91 11c-.49 0-.9.36-.98.85C16.52 14.2 14.47 16 12 16s-4.52-1.8-4.93-4.15c-.08-.49-.49-.85-.98-.85" +
                "-.61 0-1.09.54-1 1.14.49 3 2.89 5.35 5.91 5.78V20c0 .55.45 1 1 1s1-.45 1-1v-2.08c3.02-.43" +
                "5.42-2.78 5.91-5.78.1-.6-.39-1.14-1-1.14z",
        )
    }

    /** Filled star — the important marker. */
    val Star: ImageVector by lazy {
        icon(
            name = "Star",
            pathData = "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z",
        )
    }

    /** Outline star — "not important yet". */
    val StarOutline: ImageVector by lazy {
        icon(
            name = "StarOutline",
            pathData = "M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24z" +
                "M12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z",
        )
    }

    /** Archive box — move a blurt out of the way. */
    val Archive: ImageVector by lazy {
        icon(
            name = "Archive",
            pathData = "M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z",
        )
    }

    /** Bell — a reminder is scheduled on this blurt. */
    val Bell: ImageVector by lazy {
        icon(
            name = "Bell",
            pathData = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z",
        )
    }

    /** Keyboard — the quiet typed-input fallback. */
    val Keyboard: ImageVector by lazy {
        icon(
            name = "Keyboard",
            pathData = "M20 5H4c-1.1 0-1.99.9-1.99 2L2 17c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2z" +
                "M11 8h2v2h-2V8zm0 3h2v2h-2v-2zM8 8h2v2H8V8zm0 3h2v2H8v-2zm-1 2H5v-2h2v2zm0-3H5V8h2v2z" +
                "M16 17H8v-2h8v2zm0-4h-2v-2h2v2zm0-3h-2V8h2v2zm3 3h-2v-2h2v2zm0-3h-2V8h2v2z",
        )
    }

    /** Square stop — end a recording. */
    val Stop: ImageVector by lazy {
        icon(
            name = "Stop",
            pathData = "M6 6h12v12H6z",
        )
    }

    /** Key — the BYOK / AI provider entry in the account menu. */
    val Key: ImageVector by lazy {
        icon(
            name = "Key",
            pathData = "M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4h-10.35zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z",
        )
    }

    /** Bolt — Groq, the fast classification provider. */
    val Bolt: ImageVector by lazy {
        icon(
            name = "Bolt",
            pathData = "M11 21h-1l1-7H7.5c-.58 0-.57-.32-.38-.66.19-.34.05-.08.07-.12C8.48 10.94 " +
                "10.42 7.54 13 3h1l-1 7h3.5c.49 0 .56.33.47.51l-.07.15C12.96 17.55 11 21 11 21z",
        )
    }

    /** Sparkle — Gemini, the fallback classifier + embedding provider. */
    val Sparkle: ImageVector by lazy {
        icon(
            name = "Sparkle",
            pathData = "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9z " +
                "M11.5 4.5L9 9 4.5 11.5 9 14l2.5 4.5L14 14l4.5-2.5L14 9l-2.5-4.5z " +
                "M19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z",
        )
    }

    /** Check — save confirmation. */
    val Check: ImageVector by lazy {
        icon(
            name = "Check",
            pathData = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
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
}
