package com.blurt.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.blurt.app.data.model.Recurrence
import java.time.Instant
import java.time.ZoneId

/**
 * Schedules a one-shot alarm that fires a priority Blurt notification at the
 * right moment. Exact alarms when the OS allows (pre-Android-14 and after the
 * user grants exact-alarm access), a close-enough window otherwise — a
 * reminder being a couple of minutes late is fine; a reminder never firing
 * isn't. The request code is the capture id, so each blurt owns exactly one
 * pending alarm and [cancel] removes it cleanly.
 */
class ReminderScheduler(private val context: Context) {

    fun schedule(captureId: Long, content: String, atMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = reminderPendingIntent(captureId, content)
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canBeExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        }
    }

    fun cancel(captureId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(reminderPendingIntent(captureId, ""))
        // A deleted/completed blurt loses its heads-up too — never leave a
        // stray nudge for a blurt that no longer exists.
        alarmManager.cancel(headsUpPendingIntent(captureId, ""))
    }

    /**
     * Arms the follow-up "heads-up" nudge: a separate alarm that fires a
     * short while before the real reminder. Its own request code keeps it
     * apart from the reminder alarm and the auto-delete cleanup, so three
     * alarms can coexist for one blurt without colliding.
     */
    fun scheduleHeadsUp(captureId: Long, content: String, atMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = headsUpPendingIntent(captureId, content)
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canBeExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
        }
    }

    fun cancelHeadsUp(captureId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(headsUpPendingIntent(captureId, ""))
    }

    /**
     * Schedules the one-time cleanup of a one-shot reminder: at [atMillis]
     * the blurt is auto-deleted (the reminder already fired and nobody acted
     * on it — the user asked not to have to clean these up by hand). A past
     * time fires immediately, which is exactly what a reboot catch-up needs.
     */
    fun scheduleAutoDelete(captureId: Long, atMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, autoDeletePendingIntent(captureId))
    }

    /** Cancels a pending auto-delete (snooze defers it, Done keeps the blurt). */
    fun cancelAutoDelete(captureId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(autoDeletePendingIntent(captureId))
    }

    private fun reminderPendingIntent(captureId: Long, content: String): PendingIntent {
        val intent = Intent(context, BlurtReminderReceiver::class.java)
            .putExtra(BlurtReminderReceiver.EXTRA_CAPTURE_ID, captureId)
            .putExtra(BlurtReminderReceiver.EXTRA_CONTENT, content)
        return PendingIntent.getBroadcast(
            context,
            captureId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun headsUpPendingIntent(captureId: Long, content: String): PendingIntent {
        val intent = Intent(context, BlurtReminderReceiver::class.java)
            .setAction(BlurtReminderReceiver.ACTION_HEADS_UP)
            .putExtra(BlurtReminderReceiver.EXTRA_CAPTURE_ID, captureId)
            .putExtra(BlurtReminderReceiver.EXTRA_CONTENT, content)
        return PendingIntent.getBroadcast(
            context,
            captureId.toInt() + HEADS_UP_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun autoDeletePendingIntent(captureId: Long): PendingIntent {
        val intent = Intent(context, BlurtReminderReceiver::class.java)
            .setAction(BlurtReminderReceiver.ACTION_AUTO_DELETE)
            .putExtra(BlurtReminderReceiver.EXTRA_CAPTURE_ID, captureId)
        return PendingIntent.getBroadcast(
            context,
            captureId.toInt() + AUTO_DELETE_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** Keeps the auto-delete alarm's request code apart from the reminder's. */
        private const val AUTO_DELETE_OFFSET = 3_000_000
        /** Keeps the heads-up nudge's request code apart from the others. */
        private const val HEADS_UP_OFFSET = 5_000_000
    }
}

/**
 * The next fire time of a repeating reminder, advanced from its anchor (the
 * last scheduled instant) by one day / one week in the device's local zone,
 * so wall-clock time and weekday stay put. Loops forward past any occurrence
 * already in the past (e.g. the device was off for several days): the chain
 * resumes at the next real future moment instead of dying.
 */
fun nextRecurringOccurrence(anchorEpochMillis: Long, recurrence: Recurrence): Long {
    if (recurrence == Recurrence.NONE) return anchorEpochMillis
    val now = System.currentTimeMillis()
    // The anchor itself is still ahead (or happening right now) — keep it.
    // Only a past occurrence (device was off) advances to the next future one.
    if (anchorEpochMillis >= now) return anchorEpochMillis
    val stepDays = if (recurrence == Recurrence.DAILY) 1L else 7L
    var next = Instant.ofEpochMilli(anchorEpochMillis).atZone(ZoneId.systemDefault())
    do {
        next = next.plusDays(stepDays)
    } while (next.toInstant().toEpochMilli() < now)
    return next.toInstant().toEpochMilli()
}
