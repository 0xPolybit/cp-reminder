package com.example.cpreminder

import android.content.Context

/**
 * Thin wrapper around SharedPreferences for persisting the verified CodeForces
 * username locally on the device.
 */
object UserPrefs {
    private const val PREFS_NAME = "cp_reminder_prefs"
    private const val KEY_USERNAME = "cf_username"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUsername(context: Context, username: String) {
        prefs(context).edit().putString(KEY_USERNAME, username).apply()
    }

    fun getUsername(context: Context): String? =
        prefs(context).getString(KEY_USERNAME, null)

    fun hasUsername(context: Context): Boolean =
        !getUsername(context).isNullOrBlank()

    fun clearUsername(context: Context) {
        prefs(context).edit().remove(KEY_USERNAME).apply()
    }
}
