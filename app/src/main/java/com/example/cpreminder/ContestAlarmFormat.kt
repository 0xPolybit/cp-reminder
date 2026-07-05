package com.example.cpreminder

import android.content.Context

/** Formats a contest reminder's lead time, either as a short list-row label or a full ring-screen subtitle. */
object ContestAlarmFormat {
    private fun unitsLabel(context: Context, hoursBefore: Int, minutesBefore: Int): String? {
        val resources = context.resources
        val parts = mutableListOf<String>()
        if (hoursBefore > 0) {
            parts += resources.getQuantityString(R.plurals.hours, hoursBefore, hoursBefore)
        }
        if (minutesBefore > 0) {
            parts += resources.getQuantityString(R.plurals.minutes, minutesBefore, minutesBefore)
        }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    /** Short label for a reminder row nested under a contest, e.g. "15 minutes before start". */
    fun rowLabel(context: Context, hoursBefore: Int, minutesBefore: Int): String {
        val units = unitsLabel(context, hoursBefore, minutesBefore)
            ?: return context.getString(R.string.contest_reminder_at_start)
        return context.getString(R.string.contest_reminder_before_start, units)
    }

    /** Full subtitle for the ring screen, e.g. "15 minutes before Codeforces Round 999 starts". */
    fun ringSubtitle(context: Context, hoursBefore: Int, minutesBefore: Int, contestName: String): String {
        val units = unitsLabel(context, hoursBefore, minutesBefore)
            ?: return context.getString(R.string.contest_alarm_subtitle_at_start, contestName)
        return context.getString(R.string.contest_alarm_subtitle_before, units, contestName)
    }
}
