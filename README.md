<div align="center">

# 🏆 CP Reminder

**An Android companion for competitive programmers who keep missing contests and breaking streaks.**

CP Reminder is a lightweight, dark-mode Android app that keeps your CodeForces streak alive and your contest calendar on time. It tracks your daily submission, lets you favorite upcoming contests and build a friends leaderboard — all with full-screen reminders that ring even when your phone is locked.

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#-requirements)
[![min SDK](https://img.shields.io/badge/min%20SDK-28%20(Android%209)-blueviolet?style=for-the-badge)](#-requirements)
[![target SDK](https://img.shields.io/badge/target%20SDK-36%20(Android%2016)-blueviolet?style=for-the-badge)](#-requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#-tech-stack)
[![AGP](https://img.shields.io/badge/AGP-9.2.1-0F9D58?style=for-the-badge&logo=android&logoColor=white)](#-tech-stack)
[![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge)](#-license)

</div>

---

## 📸 Overview

CP Reminder is organized as four bottom-tab screens, each focused on one aspect of your competitive-programming life:

| Tab          | What it does                                                                                     |
|--------------|--------------------------------------------------------------------------------------------------|
| 🔥 Streak    | Today's streak status (Safe / Careful now! / Oh, no!), 2×2 stat grid, and your daily reminders. |
| 🏆 Contests  | Upcoming CodeForces contests with per-contest favorite buttons and custom reminder times.       |
| 👥 Friends   | A leaderboard of your added friends, ranked by rating, with today's submission status per friend. |
| 👤 Profile   | Your CodeForces profile summary and a logout button.                                              |

A persistent top title bar shows the current tab's name. The bottom nav and ViewPager2 swipe are fully synced.

---

## ✨ Features

### Streak tracking
- **Three-tier status**: 🟢 *You're good* (solved today), 🟠 *Careful now!* (>6h left to end-of-day), 🔴 *Oh, no!* (≤6h left).
- Cached results display instantly; background refresh keeps them up to date.
- **Smart alarm gating** — the streak reminder won't ring if you've already solved a problem today.
- Per-reminder daily lead times (e.g. "remind me 2h 30m before end of day"), with a confirmation dialog on delete and a unique 10-minute snooze.

### Contest reminders
- Lists upcoming CodeForces contests with start time and countdown.
- **One-tap favorite** with a default 15-minutes-before reminder, plus a green star icon on the row.
- Multiple custom reminders per contest (hours/minutes before start).
- Confirmation dialog before deleting a reminder.
- All reminders persist across reboot.

### Friends leaderboard
- Add any CodeForces handle; rank by rating, highest first.
- Self (you) is automatically included and **highlighted** with a subtle lighter row + green rank number — and isn't removable.
- Each row shows ✅ tick or ⚠️ warning based on whether they submitted an accepted solution today.
- Tap a row to expand their profile details (registration time, last online, total submissions, max rating).
- Expanded rows persist across tab revisits.

### Full-screen reminders
- Ringing activity shows over the lock screen and turns it on.
- Alarm-style ringtone + vibration pattern (`USAGE_ALARM` audio attributes).
- **Snooze** for 10 minutes, or **Dismiss**; snooze is suppressed if it would land past the day's deadline (streak) or after contest start (contest).
- Prompts to grant the "Alarms & reminders" exact-alarm permission when missing.

### Always dark mode
- The app forces dark mode via `MODE_NIGHT_YES` on first launch, regardless of the system theme.

---

## 📋 Requirements

| Item            | Version                                                         |
|-----------------|-----------------------------------------------------------------|
| Android device  | 9.0 (Pie, API 28) or newer                                      |
| Target SDK      | 36 (Android 16)                                                  |
| Network         | Required for CodeForces API access                             |
| Permissions     | `INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`        |

---

## 🚀 Getting Started

### Build the APK from source

```bash
# Clone the repository
git clone https://github.com/0xPolybit/cp-reminder.git
cd cp-reminder

# Build a debug APK (Android Studio's AGP 9.x / Gradle 9.4+ wrapper handles everything)
./gradlew assembleDebug
```

The APK lands at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install on a device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### First-run flow

1. Launch **CP Reminder**.
2. Type your CodeForces handle and tap **Continue** — the app verifies it against the CodeForces API and stores it locally.
3. On first alarm setup, grant the **"Alarms & reminders"** permission (Android 12+). On Android 13+, also grant **POST_NOTIFICATIONS** so reminders can post.
4. Add your first streak reminder: tap the **+** on the Streak tab → choose hours/minutes before end of day → Add.

---

## 🧱 Tech Stack

| Layer          | Choice                                                                  |
|----------------|-------------------------------------------------------------------------|
| Language       | Kotlin (no separate Kotlin Gradle plugin — AGP 9 ships native Kotlin)  |
| Build system   | Gradle 9.4 with AGP 9.2.1                                              |
| UI toolkit     | Android Views + Material Components (no Compose, no RecyclerView)     |
| Navigation     | `ViewPager2` + `FragmentStateAdapter` + `BottomNavigationView`        |
| Toolbar        | `MaterialToolbar` with `setSupportActionBar`                          |
| HTTP           | `HttpURLConnection` (no Retrofit/OkHttp — kept lean)                   |
| JSON           | Built-in `org.json` (no Gson/Moshi)                                    |
| Persistence    | `SharedPreferences` with custom JSON serialization per data store        |
| Image loading  | Built-in `BitmapFactory` + `RoundedBitmapDrawableFactory` (no Glide/Coil) |
| Date / time    | `java.time` (local timezone, on-device)                                |
| Reminders      | `AlarmManager.setAlarmClock` + foreground service + full-screen activity |

> **Why so "plain"?** The app is intentionally dependency-free for everything that wasn't already in AGP's defaults — keeping the APK small (under 12 MB) and the install footprint minimal.

---

## 🏗 Architecture

```
app/src/main/java/com/example/cpreminder/
├── CodeForcesApi.kt          # All CF API calls (verifyHandle, getProfile(s), getStreakStatus,
│                              # hasAcceptedToday, getUpcomingContests) + shared httpGet/parseProfile
├── AlarmScheduler.kt         # Streak alarms (daily recurring) + canonical alarm clock scheduling
├── ContestAlarmScheduler.kt  # Contest alarms (one-time) + canonical alarm clock scheduling
├── AlarmReceiver.kt          # BroadcastReceiver that branches by kind; streak alarms are
│                              # suppressed when the user has already solved today
├── AlarmRingService.kt       # Foreground service: alarm ringtone + vibration + full-screen notification
├── AlarmRingActivity.kt      # Full-screen ringing UI with snooze / dismiss (lock-screen aware)
├── BootReceiver.kt           # Re-arms every stored alarm after BOOT_COMPLETED
│
├── MainActivity.kt           # Hosts the 4 tabs (ViewPager2 + BottomNavigationView) and a toolbar
├── LauncherActivity.kt       # Routes to either UsernameActivity or MainActivity based on
│                              # whether a verified username is stored
├── UsernameActivity.kt       # Initial handle entry + CodeForces verification (uses canonical casing)
│
├── StreakFragment.kt         # Streak tab — current/max streak, last submission, time left, reminders
├── ContestsFragment.kt       # Contests tab — list, favorites, per-contest reminders, alarm ringing
├── FriendsFragment.kt        # Friends tab — leaderboard, self-highlight, per-row expansion
├── ProfileFragment.kt        # Profile tab — avatar, rank/rating, registration, last online, logout
├── MainPagerAdapter.kt       # FragmentStateAdapter for the 4 tabs
│
├── AlarmPrefs.kt / FriendsPrefs.kt / ContestFavoritesPrefs.kt / StreakCache.kt
│                              # Local JSON-backed SharedPreferences storage (all share one file)
├── UserPrefs.kt              # Stored username (the only persisted session credential)
├── ImageLoader.kt            # Bitmap downloader + circular-crop helper
├── CodeForcesFormat.kt       # Rank-string formatting
├── DurationFormat.kt / TimeFormat.kt
│                              # Coarse "Xh Ym" and "X ago" labels (with plurals)
├── AlarmFormat.kt / ContestAlarmFormat.kt
│                              # Lead-time label formatting for streak / contest rows
```

### Alarm kind discrimination
Streak and contest alarms share `AlarmReceiver` and `AlarmRingService` — they branch on an `EXTRA_KIND` extra. Request codes for the two kinds are kept disjoint via a reserved bit (`1 shl 29` in the contest scheduler), since `PendingIntent` identity ignores extras.

### Settings
No settings screen — all preferences are stored silently via shared prefs:
- `cp_reminder_prefs.alarms` — streak reminders
- `cp_reminder_prefs.contest_favorites` — favorited contests + per-contest reminders
- `cp_reminder_prefs.friends` — added friend handles
- `cp_reminder_prefs.streak_cache` — last-fetched streak snapshot (for instant display)
- `cp_reminder_prefs.cf_username` — the verified CodeForces handle

A logout clears every one of these plus cancels every scheduled OS alarm.

---

## 🔐 Permissions

| Permission                          | Why                                                                    |
|-------------------------------------|------------------------------------------------------------------------|
| `INTERNET`                          | Fetch data from the CodeForces API.                                    |
| `SCHEDULE_EXACT_ALARM`              | Reminders ring at the exact minute, not just in a window.             |
| `RECEIVE_BOOT_COMPLETED`            | Re-arm every stored alarm after the device reboots.                    |
| `WAKE_LOCK`                         | Let the ring service keep the CPU alive while ringing.                 |
| `VIBRATE`                           | Vibrate during an alarm.                                               |
| `FOREGROUND_SERVICE`                | The ring service needs to be foreground while playing alarm sound.     |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required since the service plays alarm-tone audio.                    |
| `USE_FULL_SCREEN_INTENT`            | Show the ring activity over the lock screen.                           |
| `POST_NOTIFICATIONS`                | Post the full-screen / heads-up notification for reminders.            |

---

## 🛠 Development

### Open in Android Studio

1. Open Android Studio Hedgehog or later (AGP 9.2.1 requires a recent toolchain).
2. `File → Open` → select this repository's root.
3. Wait for Gradle sync (uses the included wrapper, no manual Gradle install needed).
4. Run on a device or emulator running API 28+.

### Running tests

```bash
./gradlew test
```

(The repo currently includes the placeholder unit test scaffolded by AGP — there are no feature-specific tests yet; contributions are welcome.)

---

## 🐛 Known Limitations

- Friend avatars and ratings re-download on every Friends-tab visit (no in-memory or disk image cache).
- Streak/Contest/Profile data refreshes only on tab switch — there's no pull-to-refresh yet.
- The "Friends" tab fetches profiles sequentially, which can be slow with many friends.
- No tests yet — contributions of unit/instrumented tests would be very welcome.

---

## 🤝 Contributing

PRs and issues are welcome. A few good places to start:

- Add tests for `CodeForcesApi` / `AlarmScheduler` parsing and timing logic.
- Add an image cache for `ImageLoader` (currently hits the network every time).
- Add pull-to-refresh on the four tabs.
- Migrate from `LinearLayout`-based lists to `RecyclerView` for better performance with many friends/contests.

When opening a PR, please keep one feature per change and run `./gradlew assembleDebug` to confirm a clean build.

---

## 📄 License

MIT — see the standard MIT terms. In short: do whatever you want with the code, but no warranty.

---

## 🙏 Acknowledgments

- [CodeForces](https://codeforces.com) for the public API the whole app is built on.
- [Android Jetpack](https://developer.android.com/jetpack) for the UI primitives.
- The Material Components library for sane defaults across themes and dialogs.

<div align="center">
<sub>Made with 🟢 for the competitive-programming community.</sub>
</div>