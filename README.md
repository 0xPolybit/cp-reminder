# CP Reminder

An Android companion for competitive programmers. Tracks your daily CodeForres streak, lists upcoming contests, lets you favorite them and set custom reminders, and ranks a leaderboard of friends by rating.

![Platform](https://img.shields.io/badge/Android-9%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2-7F52FF)
![Android Gradle Plugin](https://img.shields.io/badge/AGP-9.2.1-0F9D58)
![License](https://img.shields.io/badge/License-MIT-blue)

## Features

- **Streak tracking** — three-tier status (safe / warning / danger), cached display, daily alarms gated on "solved today."
- **Contests** — upcoming CodeForces contests, favorite with default 15-min lead time, add more per contest, confirmation dialogs on delete, reboot persistence.
- **Friends leaderboard** — add any handle, ranked by rating, self-row highlighted in green. Per-row icon shows if a friend has submitted an accepted solution today.
- **Full-screen reminders** — alarm ringtone + vibration over the lock screen, 10-minute snooze (suppressed if it would land past the day boundary / contest start).

## Requirements

- Android 9 (API 28) or newer
- Internet permission (the app talks to the CodeForces API)
- `SCHEDULE_EXACT_ALARM` and `POST_NOTIFICATIONS` are also requested; grants for the latter are prompted for on Android 13+, the former is routed to the system settings screen on Android 12+.

## Build

```bash
git clone https://github.com/0xPolybit/cp-reminder.git
cd cp-reminder
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

Single-activity, View-based Android app (no Compose, no RecyclerView).

- **`CodeForcesApi.kt`** — all CF API calls (`verifyHandle`, `getProfile(s)`, `getStreakStatus`, `hasAcceptedToday`, `getUpcomingContests`). Shared `httpGet` and `parseProfile` helpers. One friend-fetch + `mapNotNull` per-handle error tolerance, so a single renamed handle can't fail the whole leaderboard.
- **Alarm pipeline** — `AlarmScheduler` for streak alarms (recurring daily before local midnight), `ContestAlarmScheduler` for one-time contest alarms. Both use `AlarmManager.setAlarmClock` and ensure their `PendingIntent` request codes are disjoint via a reserved bit. A shared `AlarmReceiver` branches on `EXTRA_KIND`; `AlarmRingService` is the foreground service, `AlarmRingActivity` is the full-screen ringing UI. For streak alarms, the receiver first checks `CodeForcesApi.hasAcceptedToday` via `goAsync()` and silently skips ringing if the user has already solved.
- **Persistence** — all prefs share one file (`cp_reminder_prefs`) with distinct keys per concern (`alarms`, `contest_favorites`, `friends`, `streak_cache`, `cf_username`). JSON-serialized via `org.json`.

## Permissions

`INTERNET`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `VIBRATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`.

## License

MIT.content>