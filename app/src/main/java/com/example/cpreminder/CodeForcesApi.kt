package com.example.cpreminder

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Minimal client for the CodeForces public API, used to check whether a handle
 * corresponds to a real account.
 */
object CodeForcesApi {

    /** Outcome of a handle verification attempt. */
    sealed interface Result {
        /** The handle exists on CodeForces, with its canonical (correctly-cased) form. */
        data class Valid(val canonicalHandle: String) : Result

        /** The request succeeded but no such handle exists. */
        data object Invalid : Result

        /** The handle could not be checked (network/parse failure). */
        data object Error : Result
    }

    /**
     * Verifies [handle] against `user.info`. Performs blocking network I/O, so it
     * must be called off the main thread.
     */
    fun verifyHandle(handle: String): Result {
        val encoded = URLEncoder.encode(handle, "UTF-8")
        val url = URL("https://codeforces.com/api/user.info?handles=$encoded")
        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val code = connection.responseCode
            // CodeForces returns 200 for a known handle and 400 for an unknown one,
            // with a JSON body in both cases.
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: return Result.Error

            val json = JSONObject(body)
            when (json.optString("status")) {
                "OK" -> {
                    val canonical = json.optJSONArray("result")?.optJSONObject(0)?.optString("handle")
                    Result.Valid(if (canonical.isNullOrBlank()) handle else canonical)
                }
                "FAILED" -> Result.Invalid
                else -> Result.Error
            }
        } catch (e: Exception) {
            Result.Error
        } finally {
            connection?.disconnect()
        }
    }

    /** Outcome of a streak check. */
    sealed interface StreakResult {
        /**
         * Streak data computed from the user's submissions.
         *
         * @param safe whether an accepted submission was made today.
         * @param currentStreak number of consecutive days (ending today, or
         *   yesterday if today is still pending) with an accepted submission.
         * @param maxStreak longest run of consecutive days with an accepted
         *   submission found in the fetched submission history.
         * @param lastSubmissionEpochSeconds creation time of the most recent
         *   submission of any verdict, or null if the user has never submitted.
         * @param deadlineEpochSeconds local midnight tonight — when an unsafe
         *   streak would break.
         */
        data class Success(
            val safe: Boolean,
            val currentStreak: Int,
            val maxStreak: Int,
            val lastSubmissionEpochSeconds: Long?,
            val deadlineEpochSeconds: Long
        ) : StreakResult

        /** The streak could not be determined (network/parse failure). */
        data object Error : StreakResult
    }

    /**
     * Fetches the user's recent submissions and derives their streak in the
     * device's local time zone. Performs blocking network I/O, so it must be
     * called off the main thread.
     */
    fun getStreakStatus(handle: String): StreakResult {
        val encoded = URLEncoder.encode(handle, "UTF-8")
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val deadline = today.plusDays(1).atStartOfDay(zone).toEpochSecond()

        val acceptedDays = HashSet<LocalDate>()
        var lastSubmission: Long? = null

        // Paginate, same shape as countSubmissions, but capped well short of its 20-page
        // ceiling — enough submissions to cover any realistic streak length.
        val pageSize = 10_000
        var from = 1
        for (page in 0 until 5) {
            val body = httpGet(
                URL("https://codeforces.com/api/user.status?handle=$encoded&from=$from&count=$pageSize")
            ) ?: return StreakResult.Error

            val pageLength = try {
                val json = JSONObject(body)
                if (json.optString("status") != "OK") return StreakResult.Error
                val submissions = json.getJSONArray("result")
                for (i in 0 until submissions.length()) {
                    val submission = submissions.getJSONObject(i)
                    val createdAt = submission.optLong("creationTimeSeconds")
                    if (createdAt > 0L && (lastSubmission == null || createdAt > lastSubmission!!)) {
                        lastSubmission = createdAt
                    }
                    if (submission.optString("verdict") == "OK") {
                        val day = Instant.ofEpochSecond(createdAt).atZone(zone).toLocalDate()
                        acceptedDays.add(day)
                    }
                }
                submissions.length()
            } catch (e: Exception) {
                return StreakResult.Error
            }

            if (pageLength < pageSize) break
            from += pageSize
        }

        val safe = acceptedDays.contains(today)
        // The streak is still "alive" today even before today's solve, so anchor
        // the count at today when solved, otherwise at yesterday.
        val anchor = when {
            safe -> today
            acceptedDays.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> null
        }
        var streak = 0
        var day = anchor
        while (day != null && acceptedDays.contains(day)) {
            streak++
            day = day.minusDays(1)
        }

        var maxStreak = 0
        var runLength = 0
        var previous: LocalDate? = null
        for (acceptedDay in acceptedDays.sorted()) {
            runLength = if (previous != null && acceptedDay == previous.plusDays(1)) {
                runLength + 1
            } else {
                1
            }
            if (runLength > maxStreak) maxStreak = runLength
            previous = acceptedDay
        }
        maxStreak = maxOf(maxStreak, streak)

        return StreakResult.Success(
            safe = safe,
            currentStreak = streak,
            maxStreak = maxStreak,
            lastSubmissionEpochSeconds = lastSubmission,
            deadlineEpochSeconds = deadline
        )
    }

    /**
     * Whether [handle] has an accepted submission today (device-local day), based
     * on their most recent submissions. Much lighter than [getStreakStatus] since
     * it only needs to check "today", not walk a whole streak history. Returns
     * null if it couldn't be determined. Performs blocking network I/O, so it
     * must be called off the main thread.
     */
    fun hasAcceptedToday(handle: String): Boolean? {
        val encoded = URLEncoder.encode(handle, "UTF-8")
        val body = httpGet(URL("https://codeforces.com/api/user.status?handle=$encoded&from=1&count=100"))
            ?: return null

        return try {
            val json = JSONObject(body)
            if (json.optString("status") != "OK") return null

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val submissions = json.getJSONArray("result")

            for (i in 0 until submissions.length()) {
                val submission = submissions.getJSONObject(i)
                val day = Instant.ofEpochSecond(submission.optLong("creationTimeSeconds")).atZone(zone).toLocalDate()
                // Submissions come back newest-first, so once we're past today there's no point continuing.
                if (day.isBefore(today)) break
                if (day == today && submission.optString("verdict") == "OK") return true
            }
            false
        } catch (e: Exception) {
            null
        }
    }

    /** A CodeForces user's public profile info. */
    data class Profile(
        val handle: String,
        val avatarUrl: String?,
        val rank: String?,
        val rating: Int?,
        val maxRank: String?,
        val maxRating: Int?,
        val registrationTimeSeconds: Long,
        val lastOnlineTimeSeconds: Long,
        /** Total submission count, or null if it could not be determined. */
        val submissionCount: Int?
    )

    /** Outcome of a profile fetch. */
    sealed interface ProfileResult {
        data class Success(val profile: Profile) : ProfileResult
        data object Error : ProfileResult
    }

    /**
     * Fetches [handle]'s public profile (`user.info`) plus their total submission
     * count (`user.status`, paginated). Performs blocking network I/O, so it must
     * be called off the main thread.
     */
    fun getProfile(handle: String): ProfileResult {
        val encoded = URLEncoder.encode(handle, "UTF-8")
        val body = httpGet(URL("https://codeforces.com/api/user.info?handles=$encoded"))
            ?: return ProfileResult.Error

        return try {
            val json = JSONObject(body)
            if (json.optString("status") != "OK") return ProfileResult.Error
            val obj = json.getJSONArray("result").getJSONObject(0)
            ProfileResult.Success(parseProfile(obj, submissionCount = countSubmissions(handle)))
        } catch (e: Exception) {
            ProfileResult.Error
        }
    }

    /**
     * Fetches a lightweight profile (rating/rank, no submission count) for each of
     * [handles], skipping any that fail (e.g. a renamed or mistyped handle)
     * rather than failing the whole batch. Performs blocking network I/O, so it
     * must be called off the main thread.
     */
    fun getProfiles(handles: List<String>): List<Profile> = handles.mapNotNull { handle ->
        val encoded = URLEncoder.encode(handle, "UTF-8")
        val body = httpGet(URL("https://codeforces.com/api/user.info?handles=$encoded")) ?: return@mapNotNull null
        try {
            val json = JSONObject(body)
            if (json.optString("status") != "OK") return@mapNotNull null
            parseProfile(json.getJSONArray("result").getJSONObject(0), submissionCount = null)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseProfile(obj: JSONObject, submissionCount: Int?): Profile {
        val titlePhoto = obj.optString("titlePhoto", "")
        val avatar = obj.optString("avatar", "")
        val rawAvatar = titlePhoto.ifBlank { avatar }
        val avatarUrl = when {
            rawAvatar.isBlank() || rawAvatar.contains("no-avatar") -> null
            rawAvatar.startsWith("http") -> rawAvatar
            else -> "https:$rawAvatar"
        }

        return Profile(
            handle = obj.optString("handle", ""),
            avatarUrl = avatarUrl,
            rank = obj.optString("rank", "").ifBlank { null },
            rating = if (obj.has("rating")) obj.optInt("rating") else null,
            maxRank = obj.optString("maxRank", "").ifBlank { null },
            maxRating = if (obj.has("maxRating")) obj.optInt("maxRating") else null,
            registrationTimeSeconds = obj.optLong("registrationTimeSeconds", 0L),
            lastOnlineTimeSeconds = obj.optLong("lastOnlineTimeSeconds", 0L),
            submissionCount = submissionCount
        )
    }

    /** Counts a handle's total submissions by paging through `user.status`. Returns null on failure. */
    private fun countSubmissions(handle: String): Int? {
        val encoded = URLEncoder.encode(handle, "UTF-8")
        val pageSize = 10_000
        var from = 1
        var total = 0

        for (page in 0 until 20) {
            val body = httpGet(
                URL("https://codeforces.com/api/user.status?handle=$encoded&from=$from&count=$pageSize")
            ) ?: return null

            val pageLength = try {
                val json = JSONObject(body)
                if (json.optString("status") != "OK") return null
                json.getJSONArray("result").length()
            } catch (e: Exception) {
                return null
            }

            total += pageLength
            if (pageLength < pageSize) return total
            from += pageSize
        }
        return total
    }

    /** Outcome of an upcoming-contest list fetch. */
    sealed interface ContestListResult {
        data class Success(val contests: List<Contest>) : ContestListResult
        data object Error : ContestListResult
    }

    /**
     * Fetches contests that haven't started yet (`contest.list`), soonest first.
     * Performs blocking network I/O, so it must be called off the main thread.
     */
    fun getUpcomingContests(): ContestListResult {
        val body = httpGet(URL("https://codeforces.com/api/contest.list?gym=false")) ?: return ContestListResult.Error
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            return ContestListResult.Error
        }
        if (json.optString("status") != "OK") return ContestListResult.Error

        val array = try {
            json.getJSONArray("result")
        } catch (e: Exception) {
            return ContestListResult.Error
        }
        val contests = (0 until array.length()).mapNotNull { i ->
            try {
                val obj = array.getJSONObject(i)
                if (obj.optString("phase") != "BEFORE" || !obj.has("startTimeSeconds")) return@mapNotNull null
                Contest(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    startTimeSeconds = obj.getLong("startTimeSeconds"),
                    durationSeconds = obj.optLong("durationSeconds", 0L)
                )
            } catch (e: Exception) {
                null
            }
        }
        return ContestListResult.Success(contests.sortedBy { it.startTimeSeconds })
    }

    private fun httpGet(url: URL): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
