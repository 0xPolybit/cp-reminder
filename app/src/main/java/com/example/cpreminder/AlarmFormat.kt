package com.example.cpreminder

import android.content.Context

/** Formats an alarm's lead time (e.g. "2 hours 30 minutes before end of day"). */
object AlarmFormat {
    fun leadTimeLabel(context: Context, hoursBefore: Int, minutesBefore: Int): String {
        val resources = context.resources
        val parts = mutableListOf<String>()
        if (hoursBefore > 0) {
            parts += resources.getQuantityString(R.plurals.hours, hoursBefore, hoursBefore)
        }
        if (minutesBefore > 0) {
            parts += resources.getQuantityString(R.plurals.minutes, minutesBefore, minutesBefore)
        }
        if (parts.isEmpty()) return context.getString(R.string.alarm_at_end_of_day)
        return context.getString(R.string.alarm_before_end_of_day, parts.joinToString(" "))
    }
}
