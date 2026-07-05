package com.example.cpreminder

/** A user-configured reminder for a favorited contest, expressed as a lead time before it starts. */
data class ContestReminder(
    val id: Long,
    val contestId: Int,
    val hoursBefore: Int,
    val minutesBefore: Int
)
