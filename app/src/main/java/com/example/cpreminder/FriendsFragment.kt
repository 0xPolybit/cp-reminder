package com.example.cpreminder

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.Executors

/**
 * "Friends" tab. A leaderboard of CodeForces accounts the user has added as
 * friends, ranked by rating. Tapping a row expands it to show that person's
 * profile details, fetched on demand and cached for the session.
 */
class FriendsFragment : Fragment(R.layout.fragment_friends) {

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val detailCache = mutableMapOf<String, CodeForcesApi.Profile>()
    private val expandedHandles = mutableSetOf<String>()

    /** A leaderboard row's data, gathered on the background thread before rendering. */
    private data class FriendRowData(
        val profile: CodeForcesApi.Profile,
        val avatarBitmap: Bitmap?,
        val acceptedToday: Boolean?
    )

    private lateinit var friendsContainer: LinearLayout
    private lateinit var noFriendsText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        friendsContainer = view.findViewById(R.id.friendsContainer)
        noFriendsText = view.findViewById(R.id.noFriendsText)
        progressBar = view.findViewById(R.id.progressBar)
        errorText = view.findViewById(R.id.errorText)

        view.findViewById<FloatingActionButton>(R.id.addFriendButton).setOnClickListener { showAddFriendDialog() }
    }

    override fun onResume() {
        super.onResume()
        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        val selfHandle = UserPrefs.getUsername(requireContext())
        val friendHandles = FriendsPrefs.getFriends(requireContext())
        val handles = (listOfNotNull(selfHandle) + friendHandles).distinctBy { it.lowercase() }

        if (handles.isEmpty()) {
            friendsContainer.removeAllViews()
            noFriendsText.visibility = View.VISIBLE
            errorText.visibility = View.GONE
            progressBar.visibility = View.GONE
            return
        }

        val hasDataOnScreen = friendsContainer.childCount > 0
        if (!hasDataOnScreen) {
            progressBar.visibility = View.VISIBLE
            errorText.visibility = View.GONE
            noFriendsText.visibility = View.GONE
        }

        ioExecutor.execute {
            val profiles = CodeForcesApi.getProfiles(handles)
                .sortedByDescending { it.rating ?: -1 }
            val rows = profiles.map { profile ->
                FriendRowData(
                    profile = profile,
                    avatarBitmap = profile.avatarUrl?.let { ImageLoader.download(it) },
                    acceptedToday = CodeForcesApi.hasAcceptedToday(profile.handle)
                )
            }

            activity?.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                progressBar.visibility = View.GONE
                if (rows.isEmpty()) {
                    if (hasDataOnScreen) {
                        Toast.makeText(requireContext(), R.string.friends_refresh_error, Toast.LENGTH_SHORT).show()
                    } else {
                        errorText.visibility = View.VISIBLE
                    }
                } else {
                    noFriendsText.visibility = if (friendHandles.isEmpty()) View.VISIBLE else View.GONE
                    errorText.visibility = View.GONE
                    showLeaderboard(rows, selfHandle)
                }
            }
        }
    }

    private fun showLeaderboard(rows: List<FriendRowData>, selfHandle: String?) {
        friendsContainer.removeAllViews()
        rows.forEachIndexed { index, rowData ->
            val isSelf = selfHandle != null && rowData.profile.handle.equals(selfHandle, ignoreCase = true)
            friendsContainer.addView(buildFriendRow(index + 1, rowData, isSelf))
        }
    }

    private fun buildFriendRow(rank: Int, rowData: FriendRowData, isSelf: Boolean): View {
        val profile = rowData.profile
        val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_friend, friendsContainer, false)
        val rankText = row.findViewById<TextView>(R.id.rankText)
        val avatarImage = row.findViewById<ImageView>(R.id.avatarImage)
        val handleText = row.findViewById<TextView>(R.id.handleText)
        val rankNameText = row.findViewById<TextView>(R.id.rankNameText)
        val todayStatusIcon = row.findViewById<ImageView>(R.id.todayStatusIcon)
        val ratingText = row.findViewById<TextView>(R.id.ratingText)
        val summaryRow = row.findViewById<LinearLayout>(R.id.summaryRow)
        val detailSection = row.findViewById<LinearLayout>(R.id.detailSection)
        val registeredText = row.findViewById<TextView>(R.id.registeredText)
        val lastActiveText = row.findViewById<TextView>(R.id.lastActiveText)
        val submissionsText = row.findViewById<TextView>(R.id.submissionsText)
        val maxRatingText = row.findViewById<TextView>(R.id.maxRatingText)
        val detailProgressBar = row.findViewById<ProgressBar>(R.id.detailProgressBar)
        val removeFriendButton = row.findViewById<Button>(R.id.removeFriendButton)

        rankText.text = getString(R.string.friend_rank_format, rank)
        handleText.text = profile.handle
        if (isSelf) {
            row.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.friend_self_highlight))
            rankText.setTextColor(ContextCompat.getColor(requireContext(), R.color.streak_safe))
        }
        rankNameText.text = getString(
            R.string.friend_subtitle_format,
            profile.rank?.let { CodeForcesFormat.formatRank(it) } ?: getString(R.string.profile_rank_unrated),
            TimeFormat.agoLabel(requireContext(), profile.lastOnlineTimeSeconds)
        )
        ratingText.text = profile.rating?.toString() ?: getString(R.string.profile_value_unknown)

        when (rowData.acceptedToday) {
            true -> {
                todayStatusIcon.visibility = View.VISIBLE
                todayStatusIcon.setImageResource(R.drawable.ic_check_circle)
                todayStatusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.streak_safe))
                todayStatusIcon.contentDescription = getString(R.string.friend_submitted_today)
            }
            false -> {
                todayStatusIcon.visibility = View.VISIBLE
                todayStatusIcon.setImageResource(R.drawable.ic_warning)
                todayStatusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.streak_danger))
                todayStatusIcon.contentDescription = getString(R.string.friend_not_submitted_today)
            }
            null -> todayStatusIcon.visibility = View.GONE
        }

        ImageLoader.loadCircularInto(avatarImage, rowData.avatarBitmap, resources)

        val handleKey = profile.handle.lowercase()

        fun expandDetail() {
            detailSection.visibility = View.VISIBLE
            expandedHandles.add(handleKey)
            val cached = detailCache[profile.handle]
            if (cached != null) {
                populateDetail(cached, registeredText, lastActiveText, submissionsText, maxRatingText)
            } else {
                loadDetail(profile.handle, detailProgressBar, registeredText, lastActiveText, submissionsText, maxRatingText)
            }
        }

        summaryRow.setOnClickListener {
            if (detailSection.visibility == View.VISIBLE) {
                detailSection.visibility = View.GONE
                expandedHandles.remove(handleKey)
                return@setOnClickListener
            }
            expandDetail()
        }

        if (isSelf) {
            removeFriendButton.visibility = View.GONE
        } else {
            removeFriendButton.visibility = View.VISIBLE
            removeFriendButton.setOnClickListener { confirmRemoveFriend(profile.handle) }
        }

        if (expandedHandles.contains(handleKey)) {
            expandDetail()
        }

        return row
    }

    private fun loadDetail(
        handle: String,
        detailProgressBar: ProgressBar,
        registeredText: TextView,
        lastActiveText: TextView,
        submissionsText: TextView,
        maxRatingText: TextView
    ) {
        detailProgressBar.visibility = View.VISIBLE
        ioExecutor.execute {
            val result = CodeForcesApi.getProfile(handle)
            activity?.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                detailProgressBar.visibility = View.GONE
                when (result) {
                    is CodeForcesApi.ProfileResult.Success -> {
                        detailCache[handle] = result.profile
                        populateDetail(result.profile, registeredText, lastActiveText, submissionsText, maxRatingText)
                    }
                    is CodeForcesApi.ProfileResult.Error ->
                        Toast.makeText(requireContext(), R.string.friend_detail_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun populateDetail(
        profile: CodeForcesApi.Profile,
        registeredText: TextView,
        lastActiveText: TextView,
        submissionsText: TextView,
        maxRatingText: TextView
    ) {
        registeredText.text = getString(
            R.string.friend_detail_line,
            getString(R.string.profile_registered),
            TimeFormat.agoLabel(requireContext(), profile.registrationTimeSeconds)
        )
        lastActiveText.text = getString(
            R.string.friend_detail_line,
            getString(R.string.profile_last_active),
            TimeFormat.agoLabel(requireContext(), profile.lastOnlineTimeSeconds)
        )
        submissionsText.text = getString(
            R.string.friend_detail_line,
            getString(R.string.profile_submissions),
            profile.submissionCount?.let { "%,d".format(it) } ?: getString(R.string.profile_value_unknown)
        )
        maxRatingText.text = getString(
            R.string.friend_detail_line,
            getString(R.string.profile_max_rating),
            profile.maxRating?.toString() ?: getString(R.string.profile_value_unknown)
        )
    }

    private fun showAddFriendDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_friend, null)
        val handleEditText = dialogView.findViewById<TextInputEditText>(R.id.handleEditText)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_friend_title)
            .setView(dialogView)
            .setNegativeButton(R.string.add_alarm_negative, null)
            .setPositiveButton(R.string.add_friend_positive) { _, _ ->
                val handle = handleEditText.text?.toString()?.trim().orEmpty()
                if (handle.isNotEmpty()) verifyAndAddFriend(handle)
            }
            .show()
    }

    private fun verifyAndAddFriend(handle: String) {
        val selfHandle = UserPrefs.getUsername(requireContext())
        if (selfHandle != null && handle.equals(selfHandle, ignoreCase = true)) {
            Toast.makeText(requireContext(), R.string.friend_is_self, Toast.LENGTH_SHORT).show()
            return
        }
        if (FriendsPrefs.isFriend(requireContext(), handle)) {
            Toast.makeText(requireContext(), R.string.friend_already_added, Toast.LENGTH_SHORT).show()
            return
        }
        ioExecutor.execute {
            val result = CodeForcesApi.verifyHandle(handle)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                when (result) {
                    is CodeForcesApi.Result.Valid -> {
                        FriendsPrefs.addFriend(requireContext(), result.canonicalHandle)
                        loadLeaderboard()
                    }
                    is CodeForcesApi.Result.Invalid ->
                        Toast.makeText(requireContext(), R.string.username_not_found, Toast.LENGTH_LONG).show()
                    is CodeForcesApi.Result.Error ->
                        Toast.makeText(requireContext(), R.string.username_network_error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmRemoveFriend(handle: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.remove_friend_confirm_title)
            .setMessage(getString(R.string.remove_friend_confirm_message, handle))
            .setNegativeButton(R.string.delete_alarm_confirm_negative, null)
            .setPositiveButton(R.string.delete_alarm_confirm_positive) { _, _ ->
                FriendsPrefs.removeFriend(requireContext(), handle)
                detailCache.remove(handle)
                expandedHandles.remove(handle.lowercase())
                loadLeaderboard()
            }
            .show()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
