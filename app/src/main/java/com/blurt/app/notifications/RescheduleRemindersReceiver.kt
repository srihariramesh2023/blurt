package com.blurt.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blurt.app.BlurtApp
import com.blurt.app.auth.AuthState
import com.blurt.app.data.model.Recurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarms are cleared on reboot, so every future reminder stored in the local
 * database is re-scheduled when the device comes back (and on app update).
 * Reminders are re-armed from source of truth (Room), never from a stale
 * snapshot, so a reboot can't silently kill one.
 */
class RescheduleRemindersReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val app = context.applicationContext as BlurtApp
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            val uid = (app.container.authRepository.authState.value as? AuthState.SignedIn)
                ?.user?.uid ?: return@launch
            // Recurring reminders re-arm from any state: a past occurrence
            // (device was off) advances to the next future one, so the
            // daily/weekly chain survives a reboot.
            app.container.captureRepository.getRecurringReminders(uid).forEach { reminder ->
                val anchoredAt = reminder.reminderAt?.toEpochMilli() ?: System.currentTimeMillis()
                val at = nextRecurringOccurrence(anchoredAt, reminder.recurrence)
                app.container.captureRepository.rescheduleReminder(reminder.id, uid, at)
                app.container.reminderScheduler.schedule(reminder.id, reminder.content, at)
            }
            // One-shot (and already-future recurring) reminders, as before.
            app.container.captureRepository.getUpcomingReminders(uid).forEach { reminder ->
                if (reminder.recurrence == Recurrence.NONE) {
                    val at = reminder.reminderAt?.toEpochMilli() ?: return@forEach
                    app.container.reminderScheduler.schedule(reminder.id, reminder.content, at)
                }
            }
            // One-shot reminders that already fired — re-arm their auto-delete
            // (the alarm was cleared on reboot) so they still clean themselves up.
            app.container.captureRepository.getExpiredOneTimeReminders(uid).forEach { reminder ->
                val firedAt = reminder.reminderAt?.toEpochMilli() ?: return@forEach
                app.container.reminderScheduler.scheduleAutoDelete(
                    reminder.id,
                    firedAt + BlurtReminderReceiver.AUTO_DELETE_AFTER_MS,
                )
            }
        }
    }
}
