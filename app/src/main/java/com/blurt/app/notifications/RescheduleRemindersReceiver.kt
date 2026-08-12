package com.blurt.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blurt.app.BlurtApp
import com.blurt.app.auth.AuthState
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
            val reminders = app.container.captureRepository.getUpcomingReminders(uid)
            reminders.forEach { reminder ->
                val at = reminder.reminderAt?.toEpochMilli() ?: return@forEach
                app.container.reminderScheduler.schedule(reminder.id, reminder.content, at)
            }
        }
    }
}
