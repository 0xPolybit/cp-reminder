package com.example.cpreminder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatDelegate

/**
 * Application entry point. Forces the whole app into dark mode regardless of the
 * system setting, and sets up the notification channel used to ring streak alarms.
 */
class CPReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        createAlarmNotificationChannel()
    }

    private fun createAlarmNotificationChannel() {
        val channel = NotificationChannel(
            AlarmRingService.CHANNEL_ID,
            getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alarm_channel_description)
            enableVibration(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
