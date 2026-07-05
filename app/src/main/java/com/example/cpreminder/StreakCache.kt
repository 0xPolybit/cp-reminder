package com.example.cpreminder

import android.content.Context
import org.json.JSONObject

/** Local cache of the last successfully fetched streak result, shown instantly on next launch. */
object StreakCache {
    private const val PREFS_NAME = "cp_reminder_prefs"
    private const val KEY_STREAK_CACHE = "streak_cache"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, data: CodeForcesApi.StreakResult.Success) {
        val json = JSONObject()
            .put("safe", data.safe)
            .put("currentStreak", data.currentStreak)
            .put("maxStreak", data.maxStreak)
            .put("lastSubmissionEpochSeconds", data.lastSubmissionEpochSeconds ?: -1L)
            .put("deadlineEpochSeconds", data.deadlineEpochSeconds)
        prefs(context).edit().putString(KEY_STREAK_CACHE, json.toString()).apply()
    }

    fun load(context: Context): CodeForcesApi.StreakResult.Success? {
        val raw = prefs(context).getString(KEY_STREAK_CACHE, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val lastSubmission = json.optLong("lastSubmissionEpochSeconds", -1L).takeIf { it >= 0L }
            CodeForcesApi.StreakResult.Success(
                safe = json.getBoolean("safe"),
                currentStreak = json.getInt("currentStreak"),
                maxStreak = json.getInt("maxStreak"),
                lastSubmissionEpochSeconds = lastSubmission,
                deadlineEpochSeconds = json.getLong("deadlineEpochSeconds")
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Clears the cached streak result. */
    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_STREAK_CACHE).apply()
    }
}
