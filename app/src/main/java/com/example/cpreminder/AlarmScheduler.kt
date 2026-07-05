package com.example.cpreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules and cancels the OS-level alarms backing each [AlarmEntry]. Each
 * entry recurs daily; [AlarmReceiver] re-arms the next day's occurrence
 * whenever a (non-snooze) alarm fires.
 *
 * Uses [AlarmManager.setAlarmClock]. On API 31+ this still requires the user
 * to grant the "Alarms & reminders" special app access
 * ([canScheduleExactAlarms] / `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`); every
 * scheduling call is a no-op until that's granted, rather than crashing.
 */
object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_HOURS_BEFORE = "hours_before"
    const val EXTRA_MINUTES_BEFORE = "minutes_before"
    const val EXTRA_IS_SNOOZE = "is_snooze"

    /** Discriminates streak vs. contest alarms; shared by [AlarmReceiver], [AlarmRingService], [AlarmRingActivity]. */
    const val EXTRA_KIND = "kind"
    const val KIND_STREAK = "streak"
    const val KIND_CONTEST = "contest"
    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_CONTEST_ID = "contest_id"
    const val EXTRA_CONTEST_NAME = "contest_name"
    const val EXTRA_CONTEST_START_SECONDS = "contest_start_seconds"

    val SNOOZE_DURATION_MILLIS = java.time.Duration.ofMinutes(10).toMillis()

    private const val BASE_OFFSET = 0
    private const val SNOOZE_OFFSET = 1

    /** (Re)schedules every stored alarm's next occurrence. Safe to call repeatedly. */
    fun rescheduleAll(context: Context) {
        AlarmPrefs.getAlarms(context).forEach { scheduleNext(context, it) }
    }

    /** Schedules [alarm]'s next daily occurrence (today if still upcoming, otherwise tomorrow). */
    fun scheduleNext(context: Context, alarm: AlarmEntry) {
        schedule(context, alarm, nextTriggerMillis(alarm.hoursBefore, alarm.minutesBefore), isSnooze = false)
    }

    /**
     * Schedules a one-off snooze for [alarm] ten minutes from now.
     * Returns false without scheduling anything if that would fall past the
     * end of the local day.
     */
    fun scheduleSnooze(context: Context, alarm: AlarmEntry): Boolean {
        val zone = ZoneId.systemDefault()
        val endOfDayMillis = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val snoozeTarget = System.currentTimeMillis() + SNOOZE_DURATION_MILLIS
        if (snoozeTarget >= endOfDayMillis) return false

        schedule(context, alarm, snoozeTarget, isSnooze = true)
        return true
    }

    /** Cancels both the recurring and any pending snooze alarm for [id]. */
    fun cancel(context: Context, id: Long) {
        cancelPending(context, requestCode(id, BASE_OFFSET))
        cancelPending(context, requestCode(id, SNOOZE_OFFSET))
        AlarmRingService.stopIfRinging(context, KIND_STREAK, id)
    }

    /** Whether this app is currently allowed to schedule exact alarms. Always true below API 31. */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    private fun schedule(context: Context, alarm: AlarmEntry, triggerAtMillis: Long, isSnooze: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        if (!canScheduleExactAlarms(context)) return
        val pendingIntent = buildPendingIntent(context, alarm, isSnooze)
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
            pendingIntent
        )
    }

    private fun buildPendingIntent(context: Context, alarm: AlarmEntry, isSnooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_KIND, KIND_STREAK)
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_HOURS_BEFORE, alarm.hoursBefore)
            putExtra(EXTRA_MINUTES_BEFORE, alarm.minutesBefore)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
        }
        val code = requestCode(alarm.id, if (isSnooze) SNOOZE_OFFSET else BASE_OFFSET)
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

    /** Derives a stable, distinct request code per alarm id and alarm/snooze variant. */
    private fun requestCode(id: Long, offset: Int): Int = baseRequestCode(id, offset)

    /**
     * Well-distributed 29-bit hash of ([id], [offset]), used as the low bits of a
     * [PendingIntent] request code. [ContestAlarmScheduler] ORs in bit 29 to keep
     * its codes provably disjoint from these — PendingIntent identity ignores
     * extras, so both "kinds" target the same [AlarmReceiver] component and must
     * never collide. Mixing the full 64-bit id (rather than a plain modulo) avoids
     * the periodic collisions a naive `id % N` scheme would hit for alarms added
     * at regular time intervals apart.
     */
    internal fun baseRequestCode(id: Long, offset: Int): Int {
        // Standard SplitMix64 constants. Written via ULong + toLong() since these hex
        // patterns have their top bit set and so overflow a plain Long hex literal.
        var z = (id * 4 + offset) + 0x9E3779B97F4A7C15UL.toLong()
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9UL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBUL.toLong()
        z = z xor (z ushr 31)
        return (z ushr 33).toInt() and 0x1FFFFFFF
    }

    /** Next epoch-millis instant that is [hoursBefore]h [minutesBefore]m before a local midnight. */
    private fun nextTriggerMillis(hoursBefore: Int, minutesBefore: Int): Long {
        val zone = ZoneId.systemDefault()
        val leadSeconds = hoursBefore * 3_600L + minutesBefore * 60L
        val now = ZonedDateTime.now(zone)
        var candidate = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).minusSeconds(leadSeconds)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }
}
