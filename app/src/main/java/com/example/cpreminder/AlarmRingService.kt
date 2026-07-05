package com.example.cpreminder

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * Foreground service that rings and vibrates for an active streak or contest
 * alarm, driven by a full-screen notification that launches
 * [AlarmRingActivity]. Stopped (via [Service.stopService]) when the user
 * snoozes or dismisses.
 */
class AlarmRingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A second alarm can arrive while one is already ringing on this same Service
        // instance; release the previous player/vibrator first so nothing leaks.
        stopRinging()

        val extras = intent?.extras
        val kind = extras?.getString(AlarmScheduler.EXTRA_KIND) ?: AlarmScheduler.KIND_STREAK
        val id = if (kind == AlarmScheduler.KIND_CONTEST) {
            extras?.getLong(AlarmScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        } else {
            extras?.getLong(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        }
        ringingKind = kind
        ringingId = id

        startForeground(NOTIFICATION_ID, buildNotification(extras))
        startRinging()
        return START_NOT_STICKY
    }

    private fun buildNotification(extras: Bundle?): Notification {
        val kind = extras?.getString(AlarmScheduler.EXTRA_KIND) ?: AlarmScheduler.KIND_STREAK

        val fullScreenIntent = Intent(this, AlarmRingActivity::class.java).apply {
            if (extras != null) putExtras(extras)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val text: String
        if (kind == AlarmScheduler.KIND_CONTEST) {
            val contestName = extras?.getString(AlarmScheduler.EXTRA_CONTEST_NAME).orEmpty()
            title = getString(R.string.contest_alarm_notification_title)
            text = getString(R.string.contest_alarm_notification_text, contestName)
        } else {
            title = getString(R.string.alarm_notification_title)
            text = getString(R.string.alarm_notification_text)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startRinging() {
        try {
            val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingService, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            mediaPlayer = null
        }

        vibrator = getSystemService(Vibrator::class.java)
        val pattern = longArrayOf(0, 500, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun stopRinging() {
        mediaPlayer?.apply {
            runCatching { stop() }
            release()
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        stopRinging()
        ringingKind = null
        ringingId = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "streak_alarm_channel"
        private const val NOTIFICATION_ID = 42

        @Volatile private var ringingKind: String? = null
        @Volatile private var ringingId: Long? = null

        /** Stops the service if it is currently ringing this exact [kind]/[id] alarm. No-op otherwise. */
        fun stopIfRinging(context: Context, kind: String, id: Long) {
            if (ringingKind == kind && ringingId == id) {
                context.stopService(Intent(context, AlarmRingService::class.java))
            }
        }
    }
}
