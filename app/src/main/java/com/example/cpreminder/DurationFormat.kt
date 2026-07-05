package com.example.cpreminder

import android.content.Context

/** Formats a non-negative duration (in seconds) into a short label like "2d 3h", "1h 5m", "5m". */
object DurationFormat {
    fun shortLabel(context: Context, totalSeconds: Long): String {
        var remaining = totalSeconds.coerceAtLeast(0)
        val days = remaining / 86_400
        remaining %= 86_400
        val hours = remaining / 3_600
        remaining %= 3_600
        val minutes = remaining / 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> context.getString(R.string.duration_under_minute)
        }
    }
}
