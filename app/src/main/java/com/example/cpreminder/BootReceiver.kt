package com.example.cpreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms all stored streak and contest alarms after a reboot, since AlarmManager alarms don't survive one. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.rescheduleAll(context)
            ContestAlarmScheduler.rescheduleAll(context)
        }
    }
}
