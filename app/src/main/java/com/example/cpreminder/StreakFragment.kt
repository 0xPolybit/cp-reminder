package com.example.cpreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import java.time.Instant
import java.util.concurrent.Executors

/**
 * "Streak" tab. Shows the user's CodeForces streak, time since their last
 * submission, the deadline to keep an at-risk streak alive, and the
 * reminders list.
 */
class StreakFragment : Fragment(R.layout.fragment_streak) {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    private lateinit var contentGroup: LinearLayout
    private lateinit var streakIcon: ImageView
    private lateinit var streakStatusText: TextView
    private lateinit var currentStreakValue: TextView
    private lateinit var maxStreakValue: TextView
    private lateinit var lastSubmissionValue: TextView
    private lateinit var timeLeftCell: LinearLayout
    private lateinit var timeLeftValue: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var remindersContainer: LinearLayout
    private lateinit var noAlarmsText: TextView

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestNotificationPermissionIfNeeded()

        contentGroup = view.findViewById(R.id.contentGroup)
        streakIcon = view.findViewById(R.id.streakIcon)
        streakStatusText = view.findViewById(R.id.streakStatusText)
        currentStreakValue = view.findViewById(R.id.currentStreakValue)
        maxStreakValue = view.findViewById(R.id.maxStreakValue)
        lastSubmissionValue = view.findViewById(R.id.lastSubmissionValue)
        timeLeftCell = view.findViewById(R.id.timeLeftCell)
        timeLeftValue = view.findViewById(R.id.timeLeftValue)
        progressBar = view.findViewById(R.id.progressBar)
        remindersContainer = view.findViewById(R.id.remindersContainer)
        noAlarmsText = view.findViewById(R.id.noAlarmsText)

        view.findViewById<FloatingActionButton>(R.id.addAlarmButton).setOnClickListener { showAddAlarmDialog() }

        StreakCache.load(requireContext())?.let { showStreak(it) }
    }

    override fun onResume() {
        super.onResume()
        loadStreak()
        AlarmScheduler.rescheduleAll(requireContext())
        refreshAlarms()
    }

    private fun loadStreak() {
        val handle = UserPrefs.getUsername(requireContext())
        if (handle.isNullOrBlank()) {
            // No handle stored; nothing to show (logout will route away anyway).
            return
        }

        // If we already have something on screen (from cache or an earlier fetch this
        // session), refresh quietly in the background instead of blocking with a spinner.
        val hasDataOnScreen = contentGroup.visibility == View.VISIBLE
        if (!hasDataOnScreen) setLoading(true)

        ioExecutor.execute {
            val result = CodeForcesApi.getStreakStatus(handle)
            activity?.runOnUiThread {
                if (!isAdded || view == null) return@runOnUiThread
                if (!hasDataOnScreen) setLoading(false)
                when (result) {
                    is CodeForcesApi.StreakResult.Success -> {
                        showStreak(result)
                        StreakCache.save(requireContext(), result)
                    }
                    is CodeForcesApi.StreakResult.Error -> {
                        if (hasDataOnScreen) {
                            Toast.makeText(requireContext(), R.string.streak_refresh_error, Toast.LENGTH_SHORT).show()
                        } else {
                            contentGroup.visibility = View.GONE
                            Toast.makeText(requireContext(), R.string.streak_error, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun showStreak(data: CodeForcesApi.StreakResult.Success) {
        val now = Instant.now().epochSecond
        val timeLeftSeconds = data.deadlineEpochSeconds - now

        val (statusText, statusColor) = when {
            data.safe -> R.string.streak_safe to R.color.streak_safe
            timeLeftSeconds > SAFE_WARNING_THRESHOLD_SECONDS -> R.string.streak_warning to R.color.streak_warning
            else -> R.string.streak_danger to R.color.streak_danger
        }
        val accent = ContextCompat.getColor(requireContext(), statusColor)

        streakStatusText.setText(statusText)
        streakStatusText.setTextColor(accent)

        streakIcon.setImageResource(if (data.safe) R.drawable.ic_check_circle else R.drawable.ic_warning)
        streakIcon.setColorFilter(accent)

        currentStreakValue.text = resources.getQuantityString(
            R.plurals.days, data.currentStreak, data.currentStreak
        )
        maxStreakValue.text = resources.getQuantityString(
            R.plurals.days, data.maxStreak, data.maxStreak
        )

        lastSubmissionValue.text = data.lastSubmissionEpochSeconds?.let { last ->
            getString(R.string.last_submission_ago, DurationFormat.shortLabel(requireContext(), now - last))
        } ?: getString(R.string.last_submission_none)

        if (data.safe) {
            timeLeftCell.visibility = View.INVISIBLE
        } else {
            timeLeftCell.visibility = View.VISIBLE
            timeLeftValue.text = DurationFormat.shortLabel(requireContext(), timeLeftSeconds)
        }

        contentGroup.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) contentGroup.visibility = View.GONE
    }

    private fun refreshAlarms() {
        val alarms = AlarmPrefs.getAlarms(requireContext())
        remindersContainer.removeAllViews()
        noAlarmsText.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE

        for (alarm in alarms) {
            val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_alarm, remindersContainer, false)
            row.findViewById<TextView>(R.id.alarmLabel).text =
                AlarmFormat.leadTimeLabel(requireContext(), alarm.hoursBefore, alarm.minutesBefore)
            row.findViewById<ImageButton>(R.id.deleteButton).setOnClickListener {
                confirmDeleteAlarm(alarm)
            }
            remindersContainer.addView(row)
        }
    }

    private fun confirmDeleteAlarm(alarm: AlarmEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_alarm_confirm_title)
            .setMessage(
                getString(
                    R.string.delete_alarm_confirm_message,
                    AlarmFormat.leadTimeLabel(requireContext(), alarm.hoursBefore, alarm.minutesBefore)
                )
            )
            .setNegativeButton(R.string.delete_alarm_confirm_negative, null)
            .setPositiveButton(R.string.delete_alarm_confirm_positive) { _, _ ->
                AlarmScheduler.cancel(requireContext(), alarm.id)
                AlarmPrefs.removeAlarm(requireContext(), alarm.id)
                refreshAlarms()
            }
            .show()
    }

    private fun showAddAlarmDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_alarm, null)
        val hoursEditText = dialogView.findViewById<TextInputEditText>(R.id.hoursEditText)
        val minutesEditText = dialogView.findViewById<TextInputEditText>(R.id.minutesEditText)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_alarm_dialog_title)
            .setView(dialogView)
            .setNegativeButton(R.string.add_alarm_negative, null)
            .setPositiveButton(R.string.add_alarm_positive) { _, _ ->
                val hours = (hoursEditText.text?.toString()?.toIntOrNull() ?: 0).coerceIn(0, 23)
                val minutes = (minutesEditText.text?.toString()?.toIntOrNull() ?: 0).coerceIn(0, 59)
                val newAlarm = AlarmPrefs.addAlarm(requireContext(), hours, minutes)
                if (AlarmScheduler.canScheduleExactAlarms(requireContext())) {
                    AlarmScheduler.scheduleNext(requireContext(), newAlarm)
                } else {
                    promptExactAlarmPermission()
                }
                refreshAlarms()
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val SAFE_WARNING_THRESHOLD_SECONDS = 6 * 3_600L
    }
}
