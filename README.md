# Pause

A small translucent floating button for Android that lets you set a "stop using this app" timer with one tap. Inspired by 4-7-8 breathing wind-downs and other digital-wellbeing rituals.

> Status: **Phase 4** — when a timer fires, the default stop mode opens: a full-screen **4-7-8 breathing wind-down** (a circle grows on the inhale, holds, then shrinks on the exhale; tap anywhere to finish). Builds on the draggable bubble, duration/clock-alarm picker, live countdown, theming, and drag-to-dismiss from earlier phases.

## What it does

Tap a draggable bubble that floats over any app. Pick a **duration** (5 / 10 / 15 min presets or a 1–120 min scroll wheel) or set a **clock alarm** for a specific time. When the timer fires, an overlay nudges you to stop. You choose how strict the nudge is — a banner, a notification, a full-screen breathing exercise, or an escalating sequence.

App-agnostic by design: it doesn't track which app you're in, doesn't use the Accessibility Service, doesn't read usage stats. The user decides when to tap it.

## Tech stack

- **Kotlin** + **Jetpack Compose**
- Android `WindowManager` overlay (`SYSTEM_ALERT_WINDOW`)
- `Foreground Service` with `specialUse` type (Android 14+)
- `AlarmManager.setAlarmClock()` for timing
- min SDK 26 (Android 8.0), target SDK 35 (Android 15)

## Build & run

Requires Android Studio Ladybug or newer.

1. Open this folder in Android Studio. It will sync Gradle and download the distribution on first launch.
2. Run the `app` configuration on a device or emulator (API 26+).
3. On first launch, the app walks you through three permission grants:
   - **Display over other apps** (`SYSTEM_ALERT_WINDOW`)
   - **Post notifications** (Android 13+)
   - **Battery optimization exemption** (so Android doesn't kill the service)

The Gradle wrapper is committed, so you can also build straight from the command line:

```sh
./gradlew assembleDebug
```


## Roadmap

- [x] **Phase 1** — Foreground service skeleton, permission onboarding, manifest declarations
- [x] **Phase 2** — Draggable translucent button via `WindowManager`
- [x] **Phase 3** — Inline timer picker with **duration** (5/10/15 + 1–120 min scroll wheel) **and clock-alarm** modes, `AlarmManager` scheduling, and a countdown/static bubble toggle
- [x] **Phase 4** — Default stop mode: full-screen **4-7-8 breathing wind-down** (circle grows on inhale, holds, shrinks on exhale; no numbers)
- [ ] **Phase 5** — Settings screen with stop-mode toggle
- [ ] **Phase 6** — Other stop modes: notification-only, escalating sequence
- [x] **Phase 7** — Polish: draining-hourglass bubble glyph, haptics (tap feedback), the breathing wind-down silences background media, animated setup screen, button positioning memory
- [x] **Phase 7b** — **App-blocking break**: "Stop for now" covers chosen apps (TikTok/Instagram/YouTube/…) for a configurable window via Usage Access + a full-screen cover
- [ ] **Phase 8** — Play Store prep (privacy policy, permission declarations, listing copy)

## Design decisions (and what we deliberately ruled out)

- **No `BIND_ACCESSIBILITY_SERVICE`** — Google rejects non-accessibility apps that use it. The app-blocking break detects the foreground app via Usage Access instead.
- **`PACKAGE_USAGE_STATS` (Usage Access)** — added for the opt-in app-blocking break, to read only the current foreground package. It stays optional: skip it and everything else still works.
- **`AlarmManager.setAlarmClock()`** rather than `setExactAndAllowWhileIdle()` — avoids the restricted `SCHEDULE_EXACT_ALARM` permission on Android 13+.
- **Compose** for UI — only the overlay itself uses classic `View` (Compose-in-overlay is awkward).

## License

TBD.
