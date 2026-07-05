package com.example.cpreminder

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen alarm UI shown when a streak or contest reminder rings.
 * Launched from [AlarmRingService]'s full-screen notification, including
 * over the lock screen.
 */
class AlarmRingActivity : AppCompatActivity() {

    private var kind: String = AlarmScheduler.KIND_STREAK
    private var alarmId: Long = -1L
    private var reminderId: Long = -1L
    private var contestId: Int = -1
    private var contestName: String = ""
    private var contestStartSeconds: Long = 0L
    private var hoursBefore = 0
    private var minutesBefore = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)

        setContentView(R.layout.activity_alarm_ring)

        populateFromIntent()

        findViewById<Button>(R.id.snoozeButton).setOnClickListener { onSnooze() }
        findViewById<Button>(R.id.closeButton).setOnClickListener { finishRinging() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishRinging()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        populateFromIntent()
    }

    /** Reads the current [getIntent] extras and refreshes the on-screen title/subtitle. */
    private fun populateFromIntent() {
        kind = intent.getStringExtra(AlarmScheduler.EXTRA_KIND) ?: AlarmScheduler.KIND_STREAK
        hoursBefore = intent.getIntExtra(AlarmScheduler.EXTRA_HOURS_BEFORE, 0)
        minutesBefore = intent.getIntExtra(AlarmScheduler.EXTRA_MINUTES_BEFORE, 0)

        val titleView = findViewById<TextView>(R.id.alarmTitle)
        val subtitleView = findViewById<TextView>(R.id.alarmSubtitle)

        if (kind == AlarmScheduler.KIND_CONTEST) {
            reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
            contestId = intent.getIntExtra(AlarmScheduler.EXTRA_CONTEST_ID, -1)
            contestName = intent.getStringExtra(AlarmScheduler.EXTRA_CONTEST_NAME).orEmpty()
            contestStartSeconds = intent.getLongExtra(AlarmScheduler.EXTRA_CONTEST_START_SECONDS, 0L)

            titleView.setText(R.string.contest_alarm_ring_title)
            subtitleView.text = ContestAlarmFormat.ringSubtitle(this, hoursBefore, minutesBefore, contestName)
        } else {
            alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)

            titleView.setText(R.string.alarm_ring_title)
            subtitleView.text = AlarmFormat.leadTimeLabel(this, hoursBefore, minutesBefore)
        }
    }

    private fun onSnooze() {
        if (!AlarmScheduler.canScheduleExactAlarms(this)) {
            // Stop the ringing immediately, but don't finish() yet — that would tear
            // down the activity (and dismiss the dialog with it) before the user can
            // read/act on the prompt.
            stopService(Intent(this, AlarmRingService::class.java))
            promptExactAlarmPermission { finish() }
            return
        }

        val snoozed = if (kind == AlarmScheduler.KIND_CONTEST) {
            val contest = Contest(contestId, contestName, contestStartSeconds, 0L)
            val reminder = ContestReminder(reminderId, contestId, hoursBefore, minutesBefore)
            ContestAlarmScheduler.scheduleSnooze(this, contest, reminder)
        } else {
            AlarmScheduler.scheduleSnooze(this, AlarmEntry(alarmId, hoursBefore, minutesBefore))
        }
        if (!snoozed) {
            val message = if (kind == AlarmScheduler.KIND_CONTEST) {
                R.string.contest_alarm_snooze_unavailable
            } else {
                R.string.alarm_snooze_unavailable
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        finishRinging()
    }

    private fun promptExactAlarmPermission(onDismiss: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.exact_alarm_permission_title)
            .setMessage(R.string.exact_alarm_permission_message)
            .setOnCancelListener { onDismiss() }
            .setNegativeButton(R.string.exact_alarm_permission_negative) { _, _ -> onDismiss() }
            .setPositiveButton(R.string.exact_alarm_permission_positive) { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
                onDismiss()
            }
            .show()
    }

    private fun finishRinging() {
        stopService(Intent(this, AlarmRingService::class.java))
        finish()
    }
}
