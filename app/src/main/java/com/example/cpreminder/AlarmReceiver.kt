package com.example.cpreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Fires when a scheduled streak or contest alarm (base or snoozed) goes off.
 * Streak alarms re-arm tomorrow's occurrence; contest alarms are one-time.
 * Either way, starts [AlarmRingService] to actually ring — unless it's a
 * streak alarm and the user has already solved a problem today, in which
 * case there's nothing to remind them about.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND) ?: AlarmScheduler.KIND_STREAK
        when (kind) {
            AlarmScheduler.KIND_CONTEST -> handleContestAlarm(context, intent)
            else -> handleStreakAlarm(context, intent)
        }
    }

    private fun handleStreakAlarm(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (id == -1L) return
        val hours = intent.getIntExtra(AlarmScheduler.EXTRA_HOURS_BEFORE, 0)
        val minutes = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTES_BEFORE, 0)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)

        // The user may have deleted this reminder since it was scheduled.
        val alarm = AlarmPrefs.getAlarms(context).find { it.id == id } ?: return

        if (!isSnooze) {
            AlarmScheduler.scheduleNext(context, alarm)
        }

        val handle = UserPrefs.getUsername(context)
        if (handle.isNullOrBlank()) return

        // Checking "already solved today" needs a network call, which can't run on
        // onReceive's own thread; goAsync() keeps the receiver alive long enough for
        // a background thread to finish it before deciding whether to ring.
        val pendingResult = goAsync()
        Thread {
            try {
                // If we can't confirm they've solved it (network failure, etc.), ring
                // anyway — the alarm is a safety net and shouldn't go silent because a
                // check failed.
                val alreadySolvedToday = CodeForcesApi.hasAcceptedToday(handle) == true
                if (!alreadySolvedToday) {
                    val serviceIntent = Intent(context, AlarmRingService::class.java).apply {
                        putExtra(AlarmScheduler.EXTRA_KIND, AlarmScheduler.KIND_STREAK)
                        putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                        putExtra(AlarmScheduler.EXTRA_HOURS_BEFORE, hours)
                        putExtra(AlarmScheduler.EXTRA_MINUTES_BEFORE, minutes)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun handleContestAlarm(context: Context, intent: Intent) {
        val contestId = intent.getIntExtra(AlarmScheduler.EXTRA_CONTEST_ID, -1)
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (contestId == -1 || reminderId == -1L) return

        // The user may have unfavorited the contest or deleted this reminder since it was scheduled.
        val favorite = ContestFavoritesPrefs.getFavorite(context, contestId) ?: return
        if (favorite.reminders.none { it.id == reminderId }) return

        val serviceIntent = Intent(context, AlarmRingService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_KIND, AlarmScheduler.KIND_CONTEST)
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmScheduler.EXTRA_CONTEST_ID, contestId)
            putExtra(AlarmScheduler.EXTRA_CONTEST_NAME, favorite.contest.name)
            putExtra(AlarmScheduler.EXTRA_CONTEST_START_SECONDS, favorite.contest.startTimeSeconds)
            putExtra(AlarmScheduler.EXTRA_HOURS_BEFORE, intent.getIntExtra(AlarmScheduler.EXTRA_HOURS_BEFORE, 0))
            putExtra(AlarmScheduler.EXTRA_MINUTES_BEFORE, intent.getIntExtra(AlarmScheduler.EXTRA_MINUTES_BEFORE, 0))
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
