package com.blurt.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.blurt.app.MainActivity
import com.blurt.app.R

/**
 * Fires when a reminder alarm goes off: posts a high-importance notification
 * carrying the blurt text. Tapping it opens Blurt straight on that blurt.
 *
 * The channel is created here (not at app start) so the notification works
 * even if the app process was killed since the reminder was scheduled.
 */
class BlurtReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val captureId = intent.getLongExtra(EXTRA_CAPTURE_ID, -1L)
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: return
        if (captureId < 0) return

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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("You've got a Blurt")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(captureId.toInt(), notification)
    }

    companion object {
        const val EXTRA_CAPTURE_ID = "blurt.reminder.captureId"
        const val EXTRA_CONTENT = "blurt.reminder.content"
        private const val CHANNEL_ID = "blurt_reminders"
    }
}
