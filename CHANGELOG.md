# Changelog

All notable changes to Pause are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.0] — 2026-09-06

### Added
- **Finnish translation**, and a Language step in setup backed by per-app locales.
  `locales_config.xml` also surfaces Pause in the Android 13+ system language picker.
- **Internationalization** — every user-facing string, plurals included, moved out of Kotlin
  into resources, so a language can be added without touching code.
- **Promoted ongoing notification** — on Android 16 a running timer pins to the top of the shade
  with the time left in the status bar. It is a request the system may decline.
- **Reproducible release builds** — two clean builds, and a build from a fresh clone at a
  different path, are byte-identical. This is what lets F-Droid verify and ship *our* signed APK,
  so an F-Droid install and a Play install stay the same app.
- **Release workflow** — a `v*` tag builds, tests, signs and publishes the APK with its SHA-256.
- **F-Droid metadata** — descriptions, per-version changelogs, a 512x512 icon and five
  screenshots, the latter generated from the existing Roborazzi tests.
- **Search field** in the "apps to block" picker.
- **Dependabot** for Gradle and Actions, with AGP, Kotlin, the Gradle wrapper and core-ktx pinned.

### Changed
- **This changelog is a third of its former size.** Entries had grown into paragraphs explaining
  mechanisms and reasoning — 0.5.0's section alone ran to about 18,000 characters, more than half
  the file. Every entry is now a line or two saying what changed and why it matters; the reasoning
  lives in the commit messages and `ONBOARDING.md`, where it can be read by whoever needs it.
- **Targets Android 16 (SDK 36)**, on AGP 8.13.2, Gradle 8.14.5 and JDK 21.
- **Application ID renamed** to `io.github.mzuhairkhan.pause`. It installs *alongside* an older
  Pause rather than updating it — uninstall the old one first.
- **Plainer, more consistent wording** — the floating control is the *bubble* everywhere, and
  unit suffixes, quote marks and stepper labels now match across the app.
- **Accessibility** — stepper buttons and accent swatches announce themselves to TalkBack, tap
  targets meet 48dp, and the wind-down announces each breathing phase.
- The setup wizard is vertically centred; the timer picker drops its "Hide the bubble" button;
  both notifications are harder to clear by accident.
- Release notes now come from the store changelog rather than this file, and the README is
  trimmed to what a visitor needs.
- **CI** — actions on their Node 24 releases, the Gradle wrapper checksum validated, and the
  emulator matrix widened to API 26, 35 and 36.

### Fixed
- **The released APK could not be verified against F-Droid's rebuild.** The build itself was
  reproducible — F-Droid rebuilt it from the tag and every file inside matched, byte for byte —
  but signing changed the archive around them. `apksigner` was adding v1 JAR signatures
  (`MANIFEST.MF` plus the `.SF`/`.RSA` pair, three ZIP entries a rebuild has no way to produce)
  and re-aligning while inserting the signing block, which shifted the local-header padding of
  about 160 entries. Since F-Droid copies our signing block onto the APK *it* builds, either
  alone breaks the digest. Signing now passes `--v1-signing-enabled false` (v2/v3 cover API 24+,
  and minSdk is 26) and `--alignment-preserved`; the signed APK's ZIP structure then matches
  AGP's output exactly.
- **Two more version pins that existed only as comments.** core-ktx was held at 1.18.0 in a
  comment, so a bot proposed 1.19.0, which needs compileSdk 37 and fails `checkDebugAarMetadata`
  outright. Roborazzi was capped at 1.66 for a Kotlin-metadata reason, but 1.65 breaks too — it
  moves `captureRoboImage` and every call in `ScreenshotTest.kt` stops resolving. Both are now
  rules Dependabot obeys rather than notes it cannot read.
- **Back is claimed explicitly** by the breathing wind-down, the block cover and the timer picker
  via `OnBackInvokedCallback` on API 33+. The no-skip lock had been working by accident on a
  legacy key-event path that Android is retiring; the key listeners remain the path below 33.
- **Three things were never localized** — the bubble countdown built its own "2h"/"5m" labels,
  the setup steppers hardcoded their suffixes, and the slider read-out always used a `.` decimal
  separator, so Finnish showed "12.2%" instead of "12,2 %".
- The bubble countdown and the notification chip could disagree about the time remaining.
- The release could publish an **unsigned APK**, and failed on a malformed signing secret with an
  opaque `base64: invalid input`. Both now fail early and say what to do.
- **The emulator job could pass having proven nothing** — `connectedDebugAndroidTest` exits 0 when
  zero tests run, and the crash-buffer capture failed open. Separately, API 26 hung for 40 minutes
  after passing, and installed the APK before the device was ready.
- **A Dependabot wrapper bump to Gradle 9.7.1 broke every build** (AGP 8.13 needs 9.5 or lower).
  The AGP and Kotlin ignore rules had never matched anything, because both are declared through
  the plugins DSL and were listed by Maven coordinate.
- Unit tests ran on a JDK too old for Robolectric's SDK 36 sandbox.
- The README said the licence was "TBD".

### Removed
- Four unused `reminder_*` strings, left over from a notification the wind-down replaced.

## [0.4.1] — 2026-06-19

### Changed
- Accent swatches are reordered so Blue (the default accent) is the first swatch.

## [0.4.0] — 2026-06-19

### Added
- **Skippable wind-down** — the breathing exercise can be turned off, ending the timer with just
  the dismiss options.
- **Snooze** — the wind-down offers a "Snooze N min" action.
- **Per-app bubble alignment** — the bubble scales with the screen and offers per-app presets.
- **Quick-start notification** — persistent, survives reboot, with a *Start* action.
- **Hourglass logo** — launcher and notification icons are a real draining hourglass.
- **Stronger media muting** during the wind-down and the app-blocking break.
- **Unit tests** (`PauseLogicTest`) and **CI** running lint, tests and a debug build.

### Changed
- **New defaults** — light-blue accent, a 30-second no-skip lock.
- Pure overlay logic extracted to `PauseLogic.kt` so it is testable without an emulator.
- **Theme-aware** setup logo and overlays, following light/dark.
- **Live bubble-size preview** replaces the in-app preview box; the countdown bubble traces a ring.
- **"Stop for now" sends you home immediately**, so playback stops at once.
- The bubble glyph is pure white with a soft drop shadow, and durations read "5m" not "5 min".
- The permissions section shows "All set" once the three required grants are in place.
- The foreground-app usage query moved off the main thread.

### Fixed
- **No more silent timer misses** — a dropped alarm (some OEMs do this) is now detected.
- **Overlay crash hardening** — adding and removing overlay views is guarded.
- **No stranded mute** — the pre-mute volume is persisted, so being killed mid-wind-down cannot
  leave the device silent.
- Preferences read back from storage are clamped to valid ranges.
- Permission rows re-check their state when you return to the setup screen.
- The bubble's drop shadow rebuilds from the hourglass's own outline.

## [0.3.0] — 2026-06-03

### Added
- **App-blocking break** — "Stop for now" can now start a timed break that covers
  chosen apps (e.g. TikTok, Instagram, YouTube) with a full-screen "Taking a break"
  screen whenever they're opened, showing the time left and a button to the home
  screen. The app also goes quiet (audio focus) while it's covered.
- **App-blocking setup** — a new section to pick which apps to block, set the break
  length (default 5 minutes), and grant the **Usage Access** permission that lets the
  break detect which app is in the foreground. The app picker lists installed apps
  via a manifest `<queries>` declaration (no `QUERY_ALL_PACKAGES`).

### Notes
- App blocking is a soft block by design: it detects (with ~1s latency) and covers a
  blocked app, rather than force-killing it. Requires Usage Access; with no apps
  chosen, "Stop for now" still simply tears the overlay down.

## [0.2.0] — 2026-06-03

### Added
- **Draining hourglass bubble glyph** — a custom `HourglassDrawable` renders sand
  falling from the top bulb to the bottom, driven by the per-second ticker so the
  bubble cycles through fill levels as the timer runs down. It starts a touch below
  full, stops just shy of empty, and (because the bulbs are conical) the sand surface
  drops slowly at first and rushes as it nears the neck. Shown when the countdown
  number is turned off.
- **Haptics** — a light confirmation tick on the bubble, picker tabs, quick chips,
  and start/cancel/stop buttons.
- **Quiet wind-down** — when a timer fires, the breathing exercise grabs exclusive
  audio focus so any background media (a video, music) pauses for its duration and
  resumes when it closes.
- **Hourglass logo** on the setup screen — a static glyph inside a bubble-styled
  circle on a soft accent glow.

### Changed
- **Setup screen polish** — centered hero header, expand/collapse sections with an
  animated chevron and fade/expand transitions, permission rows with status badges
  that auto-collapse to an "All set ✓" summary once everything is granted, and accent
  swatches that animate their ring on selection.

## [0.1.0]

### Added
- **Phase 1** — Foreground service skeleton, permission onboarding, manifest declarations.
- **Phase 2** — Draggable translucent overlay button via `WindowManager`, with
  drag-to-dismiss and edge snapping.
- **Phase 3** — Inline timer picker with duration (5/10/15 + 1–120 min scroll wheel)
  and clock-alarm modes, `AlarmManager.setAlarmClock()` scheduling, live countdown,
  and a countdown/static bubble toggle.
- **Phase 4** — Default stop mode: a full-screen 4-7-8 breathing wind-down (circle
  grows on inhale, holds, shrinks on exhale; no numbers), with a configurable
  no-skip lock window.
- Theming: system/light/dark mode, preset accent colors, and a custom color picker.
