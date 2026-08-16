package com.blurt.app.data.model

/**
 * How a reminder repeats. [NONE] is a one-shot; [DAILY] fires every day at
 * the reminder's time-of-day; [WEEKLY] fires every week on the same weekday
 * at the reminder's time-of-day.
 */
enum class Recurrence {
    NONE,
    DAILY,
    WEEKLY;

    companion object {
        /** Stored enum name → value; anything unknown (or null) degrades to NONE. */
        fun fromName(name: String?): Recurrence =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}
