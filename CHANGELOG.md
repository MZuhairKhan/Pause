# Changelog

All notable changes to Pause are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
