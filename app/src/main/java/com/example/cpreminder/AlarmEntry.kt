package com.example.cpreminder

/**
 * A user-configured reminder, expressed as a lead time before the end of the
 * local day (e.g. "2 hours 30 minutes before end of day").
 */
data class AlarmEntry(
    val id: Long,
    val hoursBefore: Int,
    val minutesBefore: Int
)
