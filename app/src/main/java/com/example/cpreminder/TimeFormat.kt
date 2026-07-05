package com.example.cpreminder

import android.content.Context
import java.time.Instant

/** Formats a past epoch-seconds instant as a coarse "X ago" label (largest applicable unit). */
object TimeFormat {
    fun agoLabel(context: Context, epochSeconds: Long): String {
        val diff = (Instant.now().epochSecond - epochSeconds).coerceAtLeast(0)
        val resources = context.resources
        val quantityPart = when {
            diff >= 365L * 86_400 -> {
                val years = (diff / (365L * 86_400)).toInt()
                resources.getQuantityString(R.plurals.years, years, years)
            }
            diff >= 30L * 86_400 -> {
                val months = (diff / (30L * 86_400)).toInt()
                resources.getQuantityString(R.plurals.months, months, months)
            }
            diff >= 86_400 -> {
                val days = (diff / 86_400).toInt()
                resources.getQuantityString(R.plurals.days, days, days)
            }
            diff >= 3_600 -> {
                val hours = (diff / 3_600).toInt()
                resources.getQuantityString(R.plurals.hours, hours, hours)
            }
            diff >= 60 -> {
                val minutes = (diff / 60).toInt()
                resources.getQuantityString(R.plurals.minutes, minutes, minutes)
            }
            else -> context.getString(R.string.duration_under_minute)
        }
        return context.getString(R.string.last_submission_ago, quantityPart)
    }
}
