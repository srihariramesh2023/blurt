package com.blurt.app.data.model

/**
 * The kind of content a blurt holds.
 */
enum class CaptureType(val label: String) {
    TEXT("Text"),
    IDEA("Idea"),
    LINK("Link");

    companion object {
        /** Resolves a nav-route slug like "text" or "link" to a type, defaulting to TEXT. */
        fun fromRoute(route: String?): CaptureType =
            entries.firstOrNull { it.name.equals(route, ignoreCase = true) } ?: TEXT
    }
}
