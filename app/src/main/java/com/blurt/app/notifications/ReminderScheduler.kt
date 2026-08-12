package com.blurt.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

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
}
