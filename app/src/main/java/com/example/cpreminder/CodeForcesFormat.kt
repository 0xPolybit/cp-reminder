package com.example.cpreminder

/** Formatting helpers for raw CodeForces API values. */
object CodeForcesFormat {
    /** Capitalizes each word of a rank string, e.g. "international grandmaster" -> "International Grandmaster". */
    fun formatRank(rank: String): String =
        rank.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}
