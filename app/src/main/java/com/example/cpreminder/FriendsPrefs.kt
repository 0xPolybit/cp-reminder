package com.example.cpreminder

import android.content.Context
import org.json.JSONArray

/** Local storage for the CodeForces handles the user has added as friends. */
object FriendsPrefs {
    private const val PREFS_NAME = "cp_reminder_prefs"
    private const val KEY_FRIENDS = "friends"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFriends(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_FRIENDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isFriend(context: Context, handle: String): Boolean =
        getFriends(context).any { it.equals(handle, ignoreCase = true) }

    /** Adds [handle] if not already present (case-insensitively). */
    fun addFriend(context: Context, handle: String) {
        val friends = getFriends(context)
        if (friends.any { it.equals(handle, ignoreCase = true) }) return
        saveFriends(context, friends + handle)
    }

    fun removeFriend(context: Context, handle: String) {
        saveFriends(context, getFriends(context).filterNot { it.equals(handle, ignoreCase = true) })
    }

    /** Removes all stored friends. */
    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_FRIENDS).apply()
    }

    private fun saveFriends(context: Context, friends: List<String>) {
        val array = JSONArray()
        friends.forEach { array.put(it) }
        prefs(context).edit().putString(KEY_FRIENDS, array.toString()).apply()
    }
}
