package com.example.cpreminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local storage for the user's streak reminders. Scheduling the actual OS
 * alarms is handled elsewhere; this only persists the user's chosen lead
 * times.
 */
object AlarmPrefs {
    private const val PREFS_NAME = "cp_reminder_prefs"
    private const val KEY_ALARMS = "alarms"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns all saved alarms, ordered earliest-in-the-day first. */
    fun getAlarms(context: Context): List<AlarmEntry> {
        val raw = prefs(context).getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val alarms = (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                AlarmEntry(
                    id = obj.getLong("id"),
                    hoursBefore = obj.getInt("hoursBefore"),
                    minutesBefore = obj.getInt("minutesBefore")
                )
            }
            alarms.sortedWith(
                compareByDescending<AlarmEntry> { it.hoursBefore }
                    .thenByDescending { it.minutesBefore }
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addAlarm(context: Context, hoursBefore: Int, minutesBefore: Int): AlarmEntry {
        val entry = AlarmEntry(System.currentTimeMillis(), hoursBefore, minutesBefore)
        saveAlarms(context, getAlarms(context) + entry)
        return entry
    }

    fun removeAlarm(context: Context, id: Long) {
        saveAlarms(context, getAlarms(context).filterNot { it.id == id })
    }

    /** Removes all stored streak reminders. Does not cancel any scheduled OS alarms. */
    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_ALARMS).apply()
    }

    private fun saveAlarms(context: Context, alarms: List<AlarmEntry>) {
        val array = JSONArray()
        alarms.forEach { alarm ->
            array.put(
                JSONObject()
                    .put("id", alarm.id)
                    .put("hoursBefore", alarm.hoursBefore)
                    .put("minutesBefore", alarm.minutesBefore)
            )
        }
        prefs(context).edit().putString(KEY_ALARMS, array.toString()).apply()
    }
}
