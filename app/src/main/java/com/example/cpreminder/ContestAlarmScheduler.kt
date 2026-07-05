package com.example.cpreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Schedules and cancels the one-time OS alarms backing each favorited
 * contest's [ContestReminder]s. Unlike streak alarms these don't recur —
 * a contest only starts once.
 */
object ContestAlarmScheduler {

    fun canScheduleExactAlarms(context: Context): Boolean = AlarmScheduler.canScheduleExactAlarms(context)

    /** Schedules [reminder] to ring its configured lead time before [contest] starts. */
    fun schedule(context: Context, contest: Contest, reminder: ContestReminder) {
        val triggerAtMillis = leadTriggerMillis(contest, reminder.hoursBefore, reminder.minutesBefore)
        scheduleAt(context, contest, reminder, triggerAtMillis, isSnooze = false)
    }

    /**
     * Schedules a one-off snooze for [reminder] ten minutes from now.
     * Returns false without scheduling anything if that would fall at or
     * after the contest's start.
     */
    fun scheduleSnooze(context: Context, contest: Contest, reminder: ContestReminder): Boolean {
        val snoozeTarget = System.currentTimeMillis() + AlarmScheduler.SNOOZE_DURATION_MILLIS
        if (snoozeTarget >= contest.startTimeSeconds * 1_000L) return false
        scheduleAt(context, contest, reminder, snoozeTarget, isSnooze = true)
        return true
    }

    /** Cancels both the base and any pending snooze alarm for [reminderId]. */
    fun cancel(context: Context, contestId: Int, reminderId: Long) {
        cancelPending(context, requestCode(reminderId, BASE_OFFSET))
        cancelPending(context, requestCode(reminderId, SNOOZE_OFFSET))
        AlarmRingService.stopIfRinging(context, AlarmScheduler.KIND_CONTEST, reminderId)
    }

    /** Re-schedules every favorited contest's still-future reminders. Safe to call repeatedly. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        ContestFavoritesPrefs.getFavorites(context).forEach { favorite ->
            favorite.reminders.forEach { reminder ->
                val triggerAtMillis = leadTriggerMillis(favorite.contest, reminder.hoursBefore, reminder.minutesBefore)
                if (triggerAtMillis > now) {
                    scheduleAt(context, favorite.contest, reminder, triggerAtMillis, isSnooze = false)
                }
            }
        }
    }

    private fun leadTriggerMillis(contest: Contest, hoursBefore: Int, minutesBefore: Int): Long =
        (contest.startTimeSeconds - hoursBefore * 3_600L - minutesBefore * 60L) * 1_000L

    private fun scheduleAt(
        context: Context,
        contest: Contest,
        reminder: ContestReminder,
        triggerAtMillis: Long,
        isSnooze: Boolean
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (!canScheduleExactAlarms(context)) return
        val pendingIntent = buildPendingIntent(context, contest, reminder, isSnooze)
        val showIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), pendingIntent)
    }

    private fun buildPendingIntent(
        context: Context,
        contest: Contest,
        reminder: ContestReminder,
        isSnooze: Boolean
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_KIND, AlarmScheduler.KIND_CONTEST)
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminder.id)
            putExtra(AlarmScheduler.EXTRA_CONTEST_ID, contest.id)
            putExtra(AlarmScheduler.EXTRA_CONTEST_NAME, contest.name)
            putExtra(AlarmScheduler.EXTRA_CONTEST_START_SECONDS, contest.startTimeSeconds)
            putExtra(AlarmScheduler.EXTRA_HOURS_BEFORE, reminder.hoursBefore)
            putExtra(AlarmScheduler.EXTRA_MINUTES_BEFORE, reminder.minutesBefore)
            putExtra(AlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
        }
        val code = requestCode(reminder.id, if (isSnooze) SNOOZE_OFFSET else BASE_OFFSET)
        return PendingIntent.getBroadcast(
            context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelPending(context: Context, code: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private const val BASE_OFFSET = 0
    private const val SNOOZE_OFFSET = 1

    // Bit 29 partitions contest codes into [2^29, 2^30), provably disjoint from
    // AlarmScheduler's streak codes in [0, 2^29) — see AlarmScheduler.baseRequestCode.
    private fun requestCode(id: Long, offset: Int): Int =
        AlarmScheduler.baseRequestCode(id, offset) or (1 shl 29)
}
