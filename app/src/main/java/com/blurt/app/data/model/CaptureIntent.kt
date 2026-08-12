package com.blurt.app.data.model

/**
 * What the user meant when they blurted something — the intent layer of V2.
 * Kept small and stable so the Library can show one clean collection per
 * intent; the AI only ever picks from this list, it never invents new ones.
 */
enum class CaptureIntent(val label: String) {
    /** A passing thought / note to remember. */
    NOTE("Note"),

    /** Something to do — a task or errand. */
    TASK("Task"),

    /** A thought worth keeping — an idea or suggestion. */
    IDEA("Idea"),

    /** Explicitly a reminder ("remind me to…"), even without a concrete time. */
    REMINDER("Reminder");

    companion object {
        /** Resolves a stored enum name; unknown values degrade to null (not classified). */
        fun fromName(name: String?): CaptureIntent? =
            entries.firstOrNull { it.name == name }
    }
}
