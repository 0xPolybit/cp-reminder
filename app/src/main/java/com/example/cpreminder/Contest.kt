package com.example.cpreminder

/** An upcoming CodeForces contest. */
data class Contest(
    val id: Int,
    val name: String,
    val startTimeSeconds: Long,
    val durationSeconds: Long
)
