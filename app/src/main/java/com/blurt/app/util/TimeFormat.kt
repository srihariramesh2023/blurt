package com.blurt.app.util

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

    /** Today's date line for the Home header, e.g. "Monday, August 10". */
    fun todayLabel(): String = today.format(LocalDate.now())

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
}
