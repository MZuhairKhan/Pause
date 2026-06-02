# Pause

A small translucent floating button for Android that lets you set a "stop using this app" timer with one tap. Inspired by 4-7-8 breathing wind-downs and other digital-wellbeing rituals.

> Status: **Phase 2** — the draggable translucent button now floats over other apps. Timer picker and stop-mode UX come in later phases (see Roadmap).

## What it does

Tap a draggable bubble that floats over any app. Pick a duration (5 / 10 / 15 / 30 min). When the timer fires, an overlay nudges you to stop. You choose how strict the nudge is — a banner, a notification, a full-screen breathing exercise, or an escalating sequence.

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
- [ ] **Phase 3** — Inline timer picker (5/10/15/30 min + custom) and `AlarmManager` scheduling
- [ ] **Phase 4** — Default stop mode: persistent overlay nudge
- [ ] **Phase 5** — Settings screen with stop-mode toggle
- [ ] **Phase 6** — Other three stop modes: full-screen 4-7-8 breathing wind-down, notification-only, escalating
- [ ] **Phase 7** — Polish (animations, haptics, button positioning memory)
- [ ] **Phase 8** — Play Store prep (privacy policy, permission declarations, listing copy)

## Design decisions (and what we deliberately ruled out)

- **No `BIND_ACCESSIBILITY_SERVICE`** — Google rejects non-accessibility apps that use it. We don't need it.
- **No `PACKAGE_USAGE_STATS`** in v1 — keeps the permission onboarding short and ships faster. May revisit if users want per-app behavior.
- **`AlarmManager.setAlarmClock()`** rather than `setExactAndAllowWhileIdle()` — avoids the restricted `SCHEDULE_EXACT_ALARM` permission on Android 13+.
- **Compose** for UI — only the overlay itself uses classic `View` (Compose-in-overlay is awkward).

## License

TBD.
