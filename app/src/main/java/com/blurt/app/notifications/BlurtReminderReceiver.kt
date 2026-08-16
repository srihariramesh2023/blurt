package com.blurt.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.blurt.app.BlurtApp
import com.blurt.app.MainActivity
import com.blurt.app.R
import com.blurt.app.auth.AuthState
import com.blurt.app.data.model.Recurrence
import com.blurt.app.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when a reminder alarm goes off: posts a high-importance notification
 * carrying the blurt text, plus two quick actions:
 *
 * - **Snooze** — defers the reminder (10 minutes), updates the blurt's
 *   reminder time (so it stays consistent across devices and re-fires),
 *   and re-posts the notification with the new time.
 * - **Done** — marks the blurt completed (cancels its alarm so it can never
 *   re-fire, persists the completion, and syncs) and dismisses the shade.
 *
 * Tapping the notification body opens Blurt straight on that blurt.
 *
 * The channel is created here (not at app start) so the notification works
 * even if the app process was killed since the reminder was scheduled.
 */
class BlurtReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val captureId = intent.getLongExtra(EXTRA_CAPTURE_ID, -1L)
        android.util.Log.d(TAG, "onReceive action=${intent.action} id=$captureId")
        if (captureId < 0) return

        try {
            when (intent.action) {
                ACTION_SNOOZE -> snooze(context, captureId)
                ACTION_COMPLETE -> complete(context, captureId)
                ACTION_AUTO_DELETE -> autoDelete(context, captureId)
                else -> onReminderFired(context, captureId, intent.getStringExtra(EXTRA_CONTENT).orEmpty())
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "receiver failed", t)
        }
    }

    /**
     * A one-shot reminder nobody acted on: delete the blurt for good (the
     * user asked for one-time reminders to clean themselves up after their
     * time passes), cancel its alarm so it can never re-fire, and dismiss the
     * notification. Tombstoned locally — sync removes it everywhere.
     */
    private fun autoDelete(context: Context, captureId: Long) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as BlurtApp
                val uid = (app.container.authRepository.authState.value as? AuthState.SignedIn)?.user?.uid
                if (uid != null) {
                    app.container.captureRepository.delete(captureId, uid)
                }
                app.container.reminderScheduler.cancel(captureId)
            } finally {
                dismiss(context, captureId)
                pendingResult.finish()
            }
        }
    }

    /**
     * A reminder alarm went off. For a recurring reminder this is also the
     * heartbeat of the chain: advance to the next occurrence (same time of
     * day / same weekday), persist it, and re-arm the alarm — so "every day"
     * and "every Wednesday" keep firing without the user touching anything.
     */
    private fun onReminderFired(context: Context, captureId: Long, content: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as BlurtApp
                val uid = (app.container.authRepository.authState.value as? AuthState.SignedIn)?.user?.uid
                var text = content
                var recurrence: Recurrence? = null
                var fireTime = System.currentTimeMillis()
                if (uid != null) {
                    val capture = app.container.captureRepository.observeById(captureId, uid).first()
                    if (capture != null) {
                        text = capture.content
                        recurrence = capture.recurrence
                        fireTime = capture.reminderAt?.toEpochMilli() ?: System.currentTimeMillis()
                        if (recurrence != Recurrence.NONE && capture.completedAt == null) {
                            val nextAt = nextRecurringOccurrence(fireTime, recurrence)
                            app.container.captureRepository.rescheduleReminder(captureId, uid, nextAt)
                            app.container.reminderScheduler.schedule(captureId, capture.content, nextAt)
                        } else if (recurrence == Recurrence.NONE && capture.completedAt == null) {
                            // One-shot, nobody touched: clean itself up a
                            // little while after it fires.
                            app.container.reminderScheduler.scheduleAutoDelete(
                                captureId,
                                fireTime + AUTO_DELETE_AFTER_MS,
                            )
                        }
                    }
                }
                postReminder(context, captureId, text, fireTime = fireTime, recurrence = recurrence)
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "reminder fire failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Snooze: defer 10 minutes, persist the new time, re-post with it. */
    private fun snooze(context: Context, captureId: Long) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as BlurtApp
                val uid = (app.container.authRepository.authState.value as? AuthState.SignedIn)?.user?.uid
                if (uid != null) {
                    val capture = app.container.captureRepository.observeById(captureId, uid).first()
                    if (capture != null && capture.completedAt == null) {
                        val newAt = System.currentTimeMillis() + SNOOZE_MS
                        app.container.captureRepository.rescheduleReminder(captureId, uid, newAt)
                        app.container.reminderScheduler.schedule(captureId, capture.content, newAt)
                        // Snooze defers the reminder — the auto-delete follows
                        // the deferred fire, not the original one.
                        app.container.reminderScheduler.cancelAutoDelete(captureId)
                        postReminder(context, captureId, capture.content, snoozedUntil = newAt)
                        return@launch
                    }
                }
                // Blurt gone, signed out, or already done — drop the alert.
                app.container.reminderScheduler.cancel(captureId)
                dismiss(context, captureId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Done: persist completion, cancel the alarm so it can never re-fire. */
    private fun complete(context: Context, captureId: Long) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as BlurtApp
                val uid = (app.container.authRepository.authState.value as? AuthState.SignedIn)?.user?.uid
                if (uid != null) {
                    app.container.captureRepository.setCompleted(captureId, uid, completed = true)
                }
                // Done is an action taken — the blurt is kept as a completed
                // record; no auto-delete.
                app.container.reminderScheduler.cancel(captureId)
                app.container.reminderScheduler.cancelAutoDelete(captureId)
            } finally {
                dismiss(context, captureId)
                pendingResult.finish()
            }
        }
    }

    private fun dismiss(context: Context, captureId: Long) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(captureId.toInt())
    }

    private fun postReminder(
        context: Context,
        captureId: Long,
        content: String,
        snoozedUntil: Long? = null,
        fireTime: Long = System.currentTimeMillis(),
        recurrence: Recurrence? = null,
    ) {
        android.util.Log.d(TAG, "postReminder id=$captureId content=$content")
        if (content.isBlank()) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Blurt reminders",
                NotificationManager.IMPORTANCE_HIGH, // heads-up banner + sound
            ).apply { description = "Reminders you set on your blurts" }
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_CAPTURE_ID, captureId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            captureId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            captureId.toInt() + ACTION_OFFSET,
            Intent(context, BlurtReminderReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_CAPTURE_ID, captureId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val doneIntent = PendingIntent.getBroadcast(
            context,
            captureId.toInt() + 2 * ACTION_OFFSET,
            Intent(context, BlurtReminderReceiver::class.java)
                .setAction(ACTION_COMPLETE)
                .putExtra(EXTRA_CAPTURE_ID, captureId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = when {
            snoozedUntil != null -> "Snoozed until ${TimeFormat.full(snoozedUntil)}"
            recurrence == Recurrence.DAILY -> "Every day · ${TimeFormat.dayTime(fireTime)}"
            recurrence == Recurrence.WEEKLY -> "Every ${TimeFormat.weekday(fireTime)} · ${TimeFormat.dayTime(fireTime)}"
            else -> "You've got a Blurt"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_snooze, "Snooze 10 min", snoozeIntent)
            .addAction(R.drawable.ic_done, "Done", doneIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(captureId.toInt(), notification)
    }        companion object {
        private const val TAG = "BlurtReminderReceiver"
        const val EXTRA_CAPTURE_ID = "blurt.reminder.captureId"
        const val EXTRA_CONTENT = "blurt.reminder.content"
        private const val ACTION_SNOOZE = "blurt.reminder.snooze"
        private const val ACTION_COMPLETE = "blurt.reminder.complete"
        const val ACTION_AUTO_DELETE = "blurt.reminder.autoDelete"
        private const val SNOOZE_MS = 10 * 60_000L
        private const val CHANNEL_ID = "blurt_reminders"

        /** How long a one-shot reminder lingers after firing before auto-delete. */
        const val AUTO_DELETE_AFTER_MS = 6 * 60 * 60_000L

        /** Keeps the action request codes distinct from the alarm's (captureId). */
        private const val ACTION_OFFSET = 1_000_000
    }
}
