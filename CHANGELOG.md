# Changelog

All notable changes to Pause are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] — 2026-06-03

### Added
- **Draining hourglass bubble glyph** — a custom `HourglassDrawable` renders sand
  falling from the top bulb to the bottom, driven by the per-second ticker so the
  bubble cycles through every fill level (full → trickle → empty) as the timer runs
  down. Shown when the countdown number is turned off.
- **Haptics** — a light confirmation tick on the bubble, picker tabs, quick chips,
  and start/cancel/stop buttons, plus a distinct rising three-pulse buzz when a
  timer ends (so the wind-down is felt even with the screen off).
- **"Vibrate when the timer ends" setting** (default on), exposed in the
  Breathing wind-down section of the setup screen.
- **Live bubble preview** on the setup screen — the real draining hourglass looping
  full → empty inside a bubble-styled circle, so the on-screen result is visible
  before starting the service.
- `VIBRATE` permission.

### Changed
- **Setup screen polish** — centered hero header, expand/collapse sections with an
  animated chevron and fade/expand transitions, permission rows with status badges
  (✓ granted / ! needed) replacing disabled buttons, and accent swatches that
  animate their ring on selection.

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
