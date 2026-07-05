package com.example.cpreminder

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import java.util.concurrent.Executors

/** "Profile" tab. Shows the user's CodeForces profile info and the logout action. */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    private lateinit var contentGroup: LinearLayout
    private lateinit var avatarImage: ImageView
    private lateinit var handleText: TextView
    private lateinit var rankText: TextView
    private lateinit var registeredValue: TextView
    private lateinit var lastActiveValue: TextView
    private lateinit var submissionsValue: TextView
    private lateinit var maxRatingValue: TextView
    private lateinit var progressBar: ProgressBar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        contentGroup = view.findViewById(R.id.contentGroup)
        avatarImage = view.findViewById(R.id.avatarImage)
        handleText = view.findViewById(R.id.handleText)
        rankText = view.findViewById(R.id.rankText)
        registeredValue = view.findViewById(R.id.registeredValue)
        lastActiveValue = view.findViewById(R.id.lastActiveValue)
        submissionsValue = view.findViewById(R.id.submissionsValue)
        maxRatingValue = view.findViewById(R.id.maxRatingValue)
        progressBar = view.findViewById(R.id.progressBar)

        view.findViewById<Button>(R.id.logoutButton).setOnClickListener { confirmLogout() }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
    }

    private fun loadProfile() {
        val handle = UserPrefs.getUsername(requireContext())
        if (handle.isNullOrBlank()) return

        val hasDataOnScreen = contentGroup.visibility == View.VISIBLE
        if (!hasDataOnScreen) {
            progressBar.visibility = View.VISIBLE
            contentGroup.visibility = View.GONE
        }

        ioExecutor.execute {
            val result = CodeForcesApi.getProfile(handle)
            val avatarUrl = (result as? CodeForcesApi.ProfileResult.Success)?.profile?.avatarUrl
            val avatarBitmap = avatarUrl?.let { ImageLoader.download(it) }

            activity?.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                progressBar.visibility = View.GONE
                when (result) {
                    is CodeForcesApi.ProfileResult.Success -> showProfile(result.profile, avatarBitmap)
                    is CodeForcesApi.ProfileResult.Error -> {
                        if (hasDataOnScreen) {
                            Toast.makeText(requireContext(), R.string.profile_refresh_error, Toast.LENGTH_SHORT).show()
                        } else {
                            contentGroup.visibility = View.GONE
                            Toast.makeText(requireContext(), R.string.profile_error, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showProfile(profile: CodeForcesApi.Profile, avatarBitmap: Bitmap?) {
        handleText.text = profile.handle

        rankText.text = if (profile.rank != null && profile.rating != null) {
            getString(R.string.profile_rank_rating, CodeForcesFormat.formatRank(profile.rank), profile.rating)
        } else {
            getString(R.string.profile_rank_unrated)
        }

        registeredValue.text = TimeFormat.agoLabel(requireContext(), profile.registrationTimeSeconds)
        lastActiveValue.text = TimeFormat.agoLabel(requireContext(), profile.lastOnlineTimeSeconds)

        submissionsValue.text = profile.submissionCount?.let { "%,d".format(it) }
            ?: getString(R.string.profile_value_unknown)

        maxRatingValue.text = profile.maxRating?.toString() ?: getString(R.string.profile_value_unknown)

        ImageLoader.loadCircularInto(avatarImage, avatarBitmap, resources)

        contentGroup.visibility = View.VISIBLE
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setNegativeButton(R.string.logout_confirm_negative, null)
            .setPositiveButton(R.string.logout_confirm_positive) { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        val context = requireContext()

        AlarmPrefs.getAlarms(context).forEach { AlarmScheduler.cancel(context, it.id) }
        ContestFavoritesPrefs.getFavorites(context).forEach { favorite ->
            favorite.reminders.forEach { ContestAlarmScheduler.cancel(context, favorite.contest.id, it.id) }
        }

        AlarmPrefs.clearAll(context)
        ContestFavoritesPrefs.clearAll(context)
        FriendsPrefs.clearAll(context)
        StreakCache.clearAll(context)
        UserPrefs.clearUsername(context)

        val intent = Intent(context, UsernameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
