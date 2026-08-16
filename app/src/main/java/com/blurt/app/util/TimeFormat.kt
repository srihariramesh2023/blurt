package com.blurt.app.util

import com.blurt.app.data.model.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Compact relative timestamps for lists, and a full timestamp for detail views.
 */
object TimeFormat {

    private val monthDay: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val full: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault())
    private val today: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    private val dayTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val weekday: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())

    /** Today's date line for the Home header, e.g. "Monday, August 10". */
    fun todayLabel(): String = today.format(LocalDate.now())

    /**
     * The board's list subtitle: "Today, 5:00 PM", "Tomorrow", "Yesterday",
     * or "MMM d" for older items.
     */
    fun dayTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val todayDate = LocalDate.now(ZoneId.systemDefault())
        val time = dayTime.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        return when (date) {
            todayDate -> "Today, $time"
            todayDate.minusDays(1) -> "Yesterday, $time"
            todayDate.plusDays(1) -> "Tomorrow, $time"
            else -> monthDay.format(date)
        }
    }

    fun relative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - epochMillis
        return when {
            diff < 60_000L -> "just now"
            diff < 3_600_000L -> "${diff / 60_000L}m ago"
            diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
            diff < 172_800_000L -> "yesterday"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000L}d ago"
            else -> monthDay.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        }
    }

    fun full(epochMillis: Long): String =
        full.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    /** The weekday name of an instant, e.g. "Wednesday". */
    fun weekday(epochMillis: Long): String =
        weekday.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    /**
     * The human label for a repeating reminder: "Every day" or
     * "Every Wednesday" (anchored on the next occurrence's weekday); null
     * for one-shot reminders.
     */
    fun recurrenceLabel(recurrence: Recurrence, atMillis: Long?): String? = when (recurrence) {
        Recurrence.NONE -> null
        Recurrence.DAILY -> "Every day"
        Recurrence.WEEKLY -> atMillis?.let { "Every ${weekday(it)}" } ?: "Every week"
    }

    /**
     * A compact countdown for a future instant, e.g. "in 4 minutes",
     * "in 2 hours", "tomorrow", "in 3 days". Falls back to the date.
     */
    fun inDuration(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = epochMillis - now
        return when {
            diff <= 0 -> "now"
            diff < 60_000L -> "in under a minute"
            diff < 3_600_000L -> "in ${diff / 60_000L} minutes"
            diff < 86_400_000L -> "in ${diff / 3_600_000L} hours"
            diff < 172_800_000L -> "tomorrow"
            diff < 7 * 86_400_000L -> "in ${diff / 86_400_000L} days"
            else -> monthDay.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
        }
    }
}
