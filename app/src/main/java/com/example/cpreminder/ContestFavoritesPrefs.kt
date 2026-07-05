package com.example.cpreminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A favorited contest, snapshotted at favorite-time, plus its configured reminders. */
data class FavoriteContest(val contest: Contest, val reminders: List<ContestReminder>)

/** Local storage for favorited contests and their per-contest reminders. */
object ContestFavoritesPrefs {
    private const val PREFS_NAME = "cp_reminder_prefs"
    private const val KEY_FAVORITES = "contest_favorites"

    /** Default lead time auto-applied when a contest is favorited. */
    private const val DEFAULT_MINUTES_BEFORE = 15

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFavorites(context: Context): List<FavoriteContest> {
        val raw = prefs(context).getString(KEY_FAVORITES, null) ?: return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (e: Exception) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            try {
                parseFavorite(array.getJSONObject(i))
            } catch (e: Exception) {
                null
            }
        }
    }

    fun getFavorite(context: Context, contestId: Int): FavoriteContest? =
        getFavorites(context).find { it.contest.id == contestId }

    /** Favorites [contest] with a default 15-minutes-before reminder, returning that reminder. */
    fun addFavorite(context: Context, contest: Contest): ContestReminder {
        val defaultReminder = ContestReminder(System.currentTimeMillis(), contest.id, 0, DEFAULT_MINUTES_BEFORE)
        val favorite = FavoriteContest(contest, listOf(defaultReminder))
        val favorites = getFavorites(context).filterNot { it.contest.id == contest.id } + favorite
        saveFavorites(context, favorites)
        return defaultReminder
    }

    fun removeFavorite(context: Context, contestId: Int) {
        saveFavorites(context, getFavorites(context).filterNot { it.contest.id == contestId })
    }

    /** Adds a reminder to an already-favorited contest. Returns null if [contestId] isn't favorited. */
    fun addReminder(context: Context, contestId: Int, hoursBefore: Int, minutesBefore: Int): ContestReminder? {
        val favorites = getFavorites(context)
        val favorite = favorites.find { it.contest.id == contestId } ?: return null
        val reminder = ContestReminder(System.currentTimeMillis(), contestId, hoursBefore, minutesBefore)
        val updated = favorite.copy(reminders = favorite.reminders + reminder)
        saveFavorites(context, favorites.map { if (it.contest.id == contestId) updated else it })
        return reminder
    }

    fun removeReminder(context: Context, contestId: Int, reminderId: Long) {
        val favorites = getFavorites(context)
        val favorite = favorites.find { it.contest.id == contestId } ?: return
        val updated = favorite.copy(reminders = favorite.reminders.filterNot { it.id == reminderId })
        saveFavorites(context, favorites.map { if (it.contest.id == contestId) updated else it })
    }

    /** Removes all favorited contests and their reminders. Does not cancel any scheduled OS alarms. */
    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_FAVORITES).apply()
    }

    /** Drops favorites for contests no longer in [upcoming] (i.e. already started) and cancels their alarms. */
    fun pruneStarted(context: Context, upcoming: List<Contest>) {
        val upcomingIds = upcoming.map { it.id }.toSet()
        val favorites = getFavorites(context)
        val stale = favorites.filterNot { upcomingIds.contains(it.contest.id) }
        if (stale.isEmpty()) return

        stale.forEach { favorite ->
            favorite.reminders.forEach { ContestAlarmScheduler.cancel(context, favorite.contest.id, it.id) }
        }
        saveFavorites(context, favorites.filter { upcomingIds.contains(it.contest.id) })
    }

    private fun parseFavorite(obj: JSONObject): FavoriteContest {
        val contestObj = obj.getJSONObject("contest")
        val contest = Contest(
            id = contestObj.getInt("id"),
            name = contestObj.getString("name"),
            startTimeSeconds = contestObj.getLong("startTimeSeconds"),
            durationSeconds = contestObj.getLong("durationSeconds")
        )
        val remindersArray = obj.getJSONArray("reminders")
        val reminders = (0 until remindersArray.length()).map { i ->
            val r = remindersArray.getJSONObject(i)
            ContestReminder(
                id = r.getLong("id"),
                contestId = contest.id,
                hoursBefore = r.getInt("hoursBefore"),
                minutesBefore = r.getInt("minutesBefore")
            )
        }
        return FavoriteContest(contest, reminders)
    }

    private fun saveFavorites(context: Context, favorites: List<FavoriteContest>) {
        val array = JSONArray()
        favorites.forEach { favorite ->
            val contestObj = JSONObject()
                .put("id", favorite.contest.id)
                .put("name", favorite.contest.name)
                .put("startTimeSeconds", favorite.contest.startTimeSeconds)
                .put("durationSeconds", favorite.contest.durationSeconds)
            val remindersArray = JSONArray()
            favorite.reminders.forEach { reminder ->
                remindersArray.put(
                    JSONObject()
                        .put("id", reminder.id)
                        .put("hoursBefore", reminder.hoursBefore)
                        .put("minutesBefore", reminder.minutesBefore)
                )
            }
            array.put(JSONObject().put("contest", contestObj).put("reminders", remindersArray))
        }
        prefs(context).edit().putString(KEY_FAVORITES, array.toString()).apply()
    }
}
