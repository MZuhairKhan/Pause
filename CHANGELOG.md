# Changelog

All notable changes to Pause are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.0] — 2026-08-30

### Added
- **Internationalization** — every user-facing string (including `<plurals>`) now lives in
  `res/values/strings.xml` instead of being hardcoded in the Compose setup UI and the overlay
  views, so the app can be translated without touching Kotlin.
- **Finnish translation** (`values-fi`) — a complete translation of all 113 strings and
  2 plurals, reviewed and corrected by Joonas Nivala over two rounds: once for the original
  translation, and again for the strings this release reworded.
- **Per-app language picker** — a Language step in the setup wizard, backed by AppCompat's
  per-app locales. `res/xml/locales_config.xml` declares the shipped languages, which also
  surfaces the system picker on Android 13+ (Settings → Apps → Pause → Language). On Android 12L
  and below, `LocaleSupport.wrap()` applies the chosen locale to service contexts, which
  AppCompat does not localize automatically.
- **App-picker search** — the "apps to block" picker gains a search field with a clear button,
  so long app lists are usable.
- **Release workflow** — pushing a `v*` tag builds and tests the release APK, signs it when
  signing secrets are configured (and warns loudly when they are not), then publishes a GitHub
  Release with the APK, its SHA-256 checksum, and the notes from this file's matching section.
- **F-Droid metadata** — `fastlane/metadata/android/en-US/` with the store title, short and full
  descriptions, and per-version changelogs.

### Changed
- **Both notifications survive "Clear all".** They were already ongoing, which is not the same
  thing: clearing the shade still took them. They now also carry `FLAG_NO_CLEAR`. An individual
  swipe still dismisses them on Android 14 and later -- that is OS policy and cannot be
  overridden. The running notification also posts immediately rather than waiting out the
  system's grace period for foreground services.
- **The timer picker no longer has a "Hide the bubble" button.** Dismissing the bubble is a
  deliberate gesture, not a button one taps by accident while setting a timer: tap outside the
  card (or press Back) to close the picker, and drag the bubble onto the dismiss target to put
  it away. The setup screen still has an explicit control.
- **Plainer wording throughout** — the floating control is now called the **bubble** everywhere
  (it was previously split between "floating button" and "bubble"), and the developer-facing
  "overlay service" is gone from the UI: "Start/Stop overlay service" is now "Show/Hide the
  bubble", and the timer picker reuses that label instead of a second one for the same action.
  "No-skip lock" is now "Minimum exercise time", and the app-blocking and Usage-access hints
  describe what actually happens instead of referring to an unexplained "cover".
- **Consistent unit abbreviations** — the compact suffix beside a number now uses `m` and `h`
  in English (previously a mix of "5 min", "2h" and "2h 30m"). The timer picker's standalone
  minutes label stays "min": a lone "m" sitting apart from the number wheel reads ambiguously.
  Each language keeps its own convention — Finnish uses `min` and `t`.
- **Matching quotation marks** — the same phrase appeared with curly quotes on the welcome
  screen and straight quotes one tap away in settings. Both are curly now (Finnish uses its
  own ”…” convention).
- **The setup wizard is vertically centred.** Its pages were pinned to the top, leaving roughly
  two thirds of a tall screen empty and reading as unfinished. Short pages now centre; longer
  ones still grow and scroll from the top.
- **Application ID renamed** to `io.github.mzuhairkhan.pause`, matching the project's source
  repository as F-Droid expects. **This changes the app's identity:** a
  0.5.0 build installs alongside an existing 0.4.x install rather than upgrading it, and settings
  from the old install are not carried over. Uninstall the old build first.
- The app theme's parent is now `Theme.AppCompat.Light.NoActionBar`, since `MainActivity` had to
  become an `AppCompatActivity` for per-app locales. This is only the window/launch theme — the
  UI itself is still Compose (`PauseTheme`).
- CI validates the Gradle wrapper's checksum before building, so a tampered wrapper JAR fails the
  build rather than silently executing.

### Fixed
- **The bubble countdown was never translated** — it built its own "2h"/"5m"/"30s" labels in
  code, so a Finnish user saw "2h" on the bubble while the notification correctly said "2 t".
  It now uses string resources like the rest of the UI.
- **The setup steppers were never translated** — the seconds/minutes suffixes were hardcoded
  Kotlin defaults rather than string resources.
- **The bubble sliders showed the wrong decimal separator** — the percent read-out was built by
  Kotlin string interpolation, which always emits a period, so Finnish showed "12.2%" instead of
  "12,2 %". It now formats in the active locale.
- The `LICENSE` (GPL-3.0) is now reflected in the README, which still said the license was "TBD".

## [0.4.1] — 2026-06-19

### Changed
- Accent swatches are reordered so Blue (the default accent) is the first swatch.

## [0.4.0] — 2026-06-19

### Added
- **Skippable wind-down** — the *Breathing wind-down* setup section gains a **Breathing exercise**
  toggle. Turned off, a finished timer skips the breathing animation and the no-skip lock and drops
  straight to the dismiss options (Keep scrolling / Stop for now / Snooze) over the full themed
  background. The breathing-specific controls hide while it's off; snooze length still applies.
- **Per-app bubble alignment** — the floating bubble now scales with the screen and offers
  **Instagram / TikTok / Shorts / Custom** presets that match each app's action rail, with a
  live in-app preview (showing the real dp size + edge offset) and 0.1%-precision Custom
  size/edge sliders. Size and edge gap are independent: size resizes the glyph; edge gap
  slides it inward without resizing. It also spawns one rail-slot above the like button.
- **Snooze** — the breathing wind-down now offers a "Snooze N min" action that dismisses
  the wind-down and re-arms the timer, with a configurable snooze length (default 5 min)
  in the setup screen's *Breathing wind-down* section.
- **Quick-start notification** — a persistent, reboot-surviving notification with a *Start*
  action lets you launch the overlay from anywhere (a boot receiver re-posts it after a
  restart); it switches to the Pause-ready / Alarm-in states once the overlay is running.
- **Hourglass logo** — the notification icon and the home-screen launcher icon are now the
  hourglass glyph (replacing the pause-bars symbol).
- **Stronger media muting** — the wind-down and app-blocking break now also zero the
  media-stream volume (and restore it afterwards), so apps that ignore audio-focus loss
  (TikTok, Instagram Reels) actually go quiet instead of playing through the pause.
- **Unit tests** — a JVM test suite (`PauseLogicTest`) covering time formatting, the
  hourglass fill remap, bubble-position round-tripping, and settings clamping.
- **Continuous integration** — a GitHub Actions workflow that runs lint, unit tests, and
  a debug build on every push and pull request, and blocks PRs that don't update this file.

### Changed
- **New defaults** — fresh installs now start with a light-blue accent, a 30-second no-skip lock,
  a 30-minute break length, and the bubble countdown number off (the draining hourglass shows
  instead). Existing installs keep whatever they've already set.
- Pure overlay logic (time formatting, hourglass math, bubble placement, settings ranges)
  moved into a testable `PauseLogic.kt` with no Android dependencies.
- **Smoother app-blocking break** — the foreground-app usage query now runs on a
  background thread, so the per-second poll no longer risks janking the UI on slow devices.
- **Permissions section** now shows "All set ✓" once the three required permissions are
  granted, but only auto-collapses once the optional Usage access is granted too.
- **Theme-aware setup logo** — the in-app hourglass and its chip now follow the light/dark
  theme instead of staying on a fixed dark plate, and it shows a mid-drain pose (~70% top,
  30% bottom, mid-flow) matching the icons.
- **Theme-aware overlay** — the timer picker, breathing wind-down, and "taking a break"
  cover now follow the app's Light/Dark choice (via `values-night` overlay colors and a
  day/night picker theme). The floating bubble stays white + shadow so it reads over any app.
- **Draining-hourglass icons** — the launcher and notification icons are now an actual frame
  of the live timer animation (baked from `HourglassDrawable`'s geometry into a static
  vector: ~70%-full top, small bottom pile, falling stream). The launcher drops the blue ring
  and shows the gray hourglass on the chip; the notification is the same shape as a white
  silhouette. Generated by `tools/gen_hourglass_frame.py` and previewed with the
  `view-vector-drawable` skill.
- **Live bubble-size preview** — the *Bubble* setup section drops the in-app preview box and
  instead shows the **real floating bubble**: changing the size (preset or Custom sliders) starts
  the overlay if needed and resizes the live bubble on screen, the way pressing Start does, so
  what you see is exactly what you get. The size/edge readout stays, and the preset label now
  reads "Match the icon size of:". Auto-start needs the same permissions as the Start button.
- **Countdown bubble ring** — when the countdown number is shown, the bubble now traces its
  circular footprint with a thin white ring, so the digits read as sitting inside the bubble
  instead of floating loose. It uses the same soft drop shadow as the glyphs (a new
  `RingDrawable` wrapped in `ShadowDrawable`) to stay legible over light and dark content.
- **Compact durations** — the snooze and break-length steppers read "5m" instead of "5 min".
- **"Stop for now" sends you home immediately**, so playback stops at once and re-opening a
  blocked app is detected without first having to leave and return.
- **Visible floating bubble** — the bubble glyph is now pure white with a soft drop shadow
  (Instagram-style) instead of a translucent plate, so the icon and countdown stay legible
  over light *and* dark content. A new `ShadowDrawable` bakes the blurred silhouette shadow
  for the stopwatch and hourglass; the countdown number uses a text shadow.

### Fixed
- **Draining hourglass shadow** — the bubble's drop shadow now rebuilds from the hourglass's
  current silhouette each tick, so the emptied top is transparent instead of leaving a stale
  black shadow blob where the sand used to be.
- **No more silent timer misses** — if the scheduled alarm is dropped (some OEMs do this
  under battery management), the per-second ticker now fires the wind-down itself.
- **Overlay crash hardening** — adding/removing overlay views is now guarded, so a
  permission revoked mid-session or a teardown racing a delayed callback can no longer
  crash the service.
- **Pref validation** — values read back from storage are clamped to their valid ranges,
  so a corrupt or restored preference can't feed a bad value into an animation or alarm.
- **No stranded mute** — the pre-mute media volume is now persisted, so if the app is
  killed mid-pause (force-stop, low memory) the next launch restores the volume instead
  of leaving the user stuck at zero.
- **Permission rows refresh on return** — the setup screen now re-checks permission state
  whenever it returns to the foreground, so granting "Display over other apps", "Ignore
  battery optimization", or "Usage access" in the system Settings screens immediately
  flips the row to "Granted" (and reflects an already-granted battery exemption) instead
  of staying stuck at "Grant".

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
