package com.blurt.app.data.model

/**
 * The fixed set of topics an AI-classified blurt can belong to. Kept small
 * and stable so the Library can show one clean filter chip per category —
 * the AI only ever picks from this list, it never invents new ones.
 */
enum class CaptureCategory(val label: String) {
    WORK("Work"),
    PERSONAL("Personal"),
    HEALTH("Health"),
    FINANCE("Finance"),
    TRAVEL("Travel"),
    IDEAS("Ideas"),
    LEARNING("Learning"),
    HOME("Home"),
    FITNESS("Fitness"),
    SOCIAL("Social"),
    SHOPPING("Shopping"),
    OTHER("Other");

    companion object {
        /** Resolves a stored enum name; unknown values degrade to [OTHER]. */
        fun fromName(name: String?): CaptureCategory =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}
