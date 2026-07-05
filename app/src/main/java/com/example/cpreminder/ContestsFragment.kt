package com.example.cpreminder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * "Contests" tab. Lists upcoming CodeForces contests; each can be favorited
 * (auto-scheduling a reminder 15 minutes before it starts) and given
 * additional custom reminders, just like the Streak tab's alarms.
 */
class ContestsFragment : Fragment(R.layout.fragment_contests) {

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val timeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

    private lateinit var contestsContainer: LinearLayout
    private lateinit var noContestsText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contestsContainer = view.findViewById(R.id.contestsContainer)
        noContestsText = view.findViewById(R.id.noContestsText)
        progressBar = view.findViewById(R.id.progressBar)
        errorText = view.findViewById(R.id.errorText)
    }

    override fun onResume() {
        super.onResume()
        ContestAlarmScheduler.rescheduleAll(requireContext())
        loadContests()
    }

    private fun loadContests() {
        val hasDataOnScreen = contestsContainer.childCount > 0
        if (!hasDataOnScreen) {
            progressBar.visibility = View.VISIBLE
            errorText.visibility = View.GONE
        }

        ioExecutor.execute {
            val result = CodeForcesApi.getUpcomingContests()
            activity?.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                progressBar.visibility = View.GONE
                when (result) {
                    is CodeForcesApi.ContestListResult.Success -> {
                        ContestFavoritesPrefs.pruneStarted(requireContext(), result.contests)
                        errorText.visibility = View.GONE
                        showContests(result.contests)
                    }
                    is CodeForcesApi.ContestListResult.Error -> {
                        if (hasDataOnScreen) {
                            Toast.makeText(requireContext(), R.string.contests_refresh_error, Toast.LENGTH_SHORT).show()
                        } else {
                            errorText.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun showContests(contests: List<Contest>) {
        contestsContainer.removeAllViews()
        noContestsText.visibility = if (contests.isEmpty()) View.VISIBLE else View.GONE
        for (contest in contests) {
            contestsContainer.addView(buildContestRow(contest))
        }
    }

    private fun buildContestRow(contest: Contest): View {
        val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_contest, contestsContainer, false)
        val nameText = row.findViewById<TextView>(R.id.contestNameText)
        val timeText = row.findViewById<TextView>(R.id.contestTimeText)
        val favoriteButton = row.findViewById<ImageButton>(R.id.favoriteButton)
        val remindersSection = row.findViewById<LinearLayout>(R.id.remindersSection)
        val remindersContainer = row.findViewById<LinearLayout>(R.id.contestRemindersContainer)
        val noRemindersText = row.findViewById<TextView>(R.id.noContestRemindersText)
        val addReminderButton = row.findViewById<Button>(R.id.addContestReminderButton)

        nameText.text = contest.name
        val startZoned = Instant.ofEpochSecond(contest.startTimeSeconds).atZone(ZoneId.systemDefault())
        val relative = DurationFormat.shortLabel(requireContext(), contest.startTimeSeconds - Instant.now().epochSecond)
        timeText.text = getString(R.string.contest_time_format, startZoned.format(timeFormatter), relative)

        fun refreshFavoriteUi() {
            val favorite = ContestFavoritesPrefs.getFavorite(requireContext(), contest.id)
            favoriteButton.setImageResource(if (favorite != null) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            favoriteButton.contentDescription = getString(
                if (favorite != null) R.string.unfavorite_contest else R.string.favorite_contest
            )
            remindersSection.visibility = if (favorite != null) View.VISIBLE else View.GONE

            remindersContainer.removeAllViews()
            val reminders = favorite?.reminders.orEmpty()
            noRemindersText.visibility = if (favorite != null && reminders.isEmpty()) View.VISIBLE else View.GONE
            for (reminder in reminders) {
                val reminderRow = LayoutInflater.from(requireContext()).inflate(R.layout.item_alarm, remindersContainer, false)
                reminderRow.findViewById<TextView>(R.id.alarmLabel).text =
                    ContestAlarmFormat.rowLabel(requireContext(), reminder.hoursBefore, reminder.minutesBefore)
                reminderRow.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
                    confirmDeleteContestReminder(contest, reminder, ::refreshFavoriteUi)
                }
                remindersContainer.addView(reminderRow)
            }
        }

        favoriteButton.setOnClickListener {
            val existing = ContestFavoritesPrefs.getFavorite(requireContext(), contest.id)
            if (existing != null) {
                existing.reminders.forEach { ContestAlarmScheduler.cancel(requireContext(), contest.id, it.id) }
                ContestFavoritesPrefs.removeFavorite(requireContext(), contest.id)
            } else {
                val defaultReminder = ContestFavoritesPrefs.addFavorite(requireContext(), contest)
                if (ContestAlarmScheduler.canScheduleExactAlarms(requireContext())) {
                    ContestAlarmScheduler.schedule(requireContext(), contest, defaultReminder)
                } else {
                    promptExactAlarmPermission()
                }
            }
            refreshFavoriteUi()
        }

        addReminderButton.setOnClickListener {
            showAddContestReminderDialog(contest, ::refreshFavoriteUi)
        }

        refreshFavoriteUi()
        return row
    }

    private fun confirmDeleteContestReminder(contest: Contest, reminder: ContestReminder, onDone: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_alarm_confirm_title)
            .setMessage(
                getString(
                    R.string.delete_alarm_confirm_message,
                    ContestAlarmFormat.rowLabel(requireContext(), reminder.hoursBefore, reminder.minutesBefore)
                )
            )
            .setNegativeButton(R.string.delete_alarm_confirm_negative, null)
            .setPositiveButton(R.string.delete_alarm_confirm_positive) { _, _ ->
                ContestAlarmScheduler.cancel(requireContext(), contest.id, reminder.id)
                ContestFavoritesPrefs.removeReminder(requireContext(), contest.id, reminder.id)
                onDone()
            }
            .show()
    }

    private fun showAddContestReminderDialog(contest: Contest, onDone: () -> Unit) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_alarm, null)
        dialogView.findViewById<TextView>(R.id.descriptionText).text =
            getString(R.string.add_contest_reminder_description, contest.name)
        val hoursEditText = dialogView.findViewById<TextInputEditText>(R.id.hoursEditText)
        val minutesEditText = dialogView.findViewById<TextInputEditText>(R.id.minutesEditText)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_contest_reminder_title)
            .setView(dialogView)
            .setNegativeButton(R.string.add_alarm_negative, null)
            .setPositiveButton(R.string.add_alarm_positive) { _, _ ->
                val hours = (hoursEditText.text?.toString()?.toIntOrNull() ?: 0).coerceIn(0, 23)
                val minutes = (minutesEditText.text?.toString()?.toIntOrNull() ?: 0).coerceIn(0, 59)
                val reminder = ContestFavoritesPrefs.addReminder(requireContext(), contest.id, hours, minutes)
                if (reminder != null) {
                    if (ContestAlarmScheduler.canScheduleExactAlarms(requireContext())) {
                        ContestAlarmScheduler.schedule(requireContext(), contest, reminder)
                    } else {
                        promptExactAlarmPermission()
                    }
                }
                onDone()
            }
            .show()
    }

    private fun promptExactAlarmPermission() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.exact_alarm_permission_title)
            .setMessage(R.string.exact_alarm_permission_message)
            .setNegativeButton(R.string.exact_alarm_permission_negative, null)
            .setPositiveButton(R.string.exact_alarm_permission_positive) { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${requireContext().packageName}")
                    )
                )
            }
            .show()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
