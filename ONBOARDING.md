# Pause — Onboarding & Release Tracker

**Pause** is an Android digital-wellbeing app: a draggable floating bubble runs a timer; when it
ends, a 4-7-8 breathing wind-down (skippable) plays, and a "Stop for now" break can cover chosen
apps (TikTok/Instagram/…) for a set time. Kotlin, Jetpack Compose setup screen + a foreground
`OverlayService` that draws the overlays. Package `com.lifelineventures.pause`.

Near-term goal: **ship on F-Droid**. Possibly Google Play later (optional).

---

## Build, run, test

`java` is **not** on PATH; use the JDK bundled with Android Studio. All commands from the repo root:

```bash
# Windows (PowerShell): set JAVA_HOME for the session first
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

./gradlew.bat testDebugUnitTest     # run JVM unit tests
./gradlew.bat assembleDebug         # build the installable debug APK
./gradlew.bat lintDebug             # lint (CI runs this)
./gradlew.bat assembleRelease       # minified release APK (UNSIGNED — see below)
```

- Debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
- To share a test build, copy it to the repo root as `Pause-<version>-debug.apk` (git-ignored).
- Version lives in `app/build.gradle.kts` (`versionName` / `versionCode`). Currently **0.4.1 / 5**.
  Bump `versionCode` for every build you distribute. Tag releases `vX.Y.Z`.

## Architecture (key files)

| File | Role |
|---|---|
| `OverlayService.kt` | The foreground service: floating bubble, timer + `AlarmManager` scheduling, breathing wind-down, "Stop for now" app-blocking break, notification. The big one. |
| `MainActivity.kt` | Compose setup screen: permissions, bubble alignment, theme/accent, breathing settings, app blocking. |
| `SettingsStore.kt` | SharedPreferences-backed settings. First-run defaults come from `SettingsDefaults`. |
| `PauseLogic.kt` | **Pure, Android-free** logic — `TimeFormat`, `HourglassMath`, `BubblePosition`, `BubblePresets`, `SettingsRanges`, `SettingsDefaults`. Unit-tested. |
| `ui/theme/Accents.kt` | Accent palette (Blue is the default, listed first). |
| `HourglassDrawable` / `RingDrawable` / `ShadowDrawable` | Custom bubble glyphs (white + soft shadow). |
| `TimerReceiver` / `BootReceiver` | Alarm fire; re-post the "Start" notification after reboot. |
| `res/layout/` | `overlay_bubble`, `timer_picker`, `breathing`, `block_overlay`, `dismiss_target`. |

Pure logic lives in `PauseLogic.kt` and is covered by `app/src/test/.../PauseLogicTest.kt`; keep
that split so behaviour stays unit-testable without an emulator.

## Permissions

`SYSTEM_ALERT_WINDOW` (overlay), `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`,
`POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `PACKAGE_USAGE_STATS` (Usage Access — for the
break's foreground-app detection), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. No `INTERNET` — the
app makes no network calls and has no analytics. Data is local SharedPreferences only;
`allowBackup="false"`.

---

## Release checklist

### Done (audit follow-up, this round)
- [x] Timer-fire path shows the wind-down even if foreground-service promotion is rejected.
- [x] Media stays muted continuously across the breathing → app-block hand-off (no audio blip).
- [x] Block cover re-detection uses a rolling usage-event cursor (survives missed events better).
- [x] Breathing wind-down accessibility: phase announced (live region), circle marked decorative,
      no-skip lock bypassed under a screen reader.
- [x] 48dp minimum tap targets (picker tabs, snooze, steppers, accent swatches).
- [x] Accent swatches labelled for TalkBack; dead code removed; settings-defaults unit tests added.

### F-Droid (blockers first)
- [ ] **Add a `LICENSE`: GPLv3** (strong copyleft — derivatives must stay open source, no
      proprietary forks). Compatible with the app's Apache-2.0 deps (Apache-2.0 is one-way
      compatible *into* GPLv3); **avoid GPLv2**, which is incompatible with Apache-2.0. F-Droid
      requires a free license.
- [ ] **Build & smoke-test a RELEASE build** (`assembleRelease`, R8/minify on) on a real device —
      it has never been run against current code. F-Droid builds release from the git tag and signs
      it themselves, so **no signing config is needed for F-Droid**.
- [ ] **On-device verify the timer fires the wind-down** when backgrounded / screen-off / swiped
      from recents (the highest-risk functional path; grant "Ignore battery optimization").
- [ ] F-Droid metadata: `fastlane/metadata/android/en-US/` (title, descriptions, changelogs) + the
      fdroiddata submission (source + issue-tracker URLs, license, `UpdateCheckMode: Tags`).
- [ ] Add a `<monochrome>` layer to the adaptive icon (themed-icon polish).

### Google Play (optional, later)
- [ ] Release signing config + `bundleRelease` (.aab; Play needs a bundle, not an APK).
- [ ] Play Console justification for `FOREGROUND_SERVICE_SPECIAL_USE` (high rejection risk).
- [ ] Data Safety form + prominent disclosure for Usage Access; battery-exemption justification.
- [ ] Hosted privacy policy URL; 512×512 store icon + screenshots.

### Quality backlog (not blocking)
- [ ] Internationalization: the Compose setup UI is hardcoded English; move strings to `strings.xml`.
- [ ] More tests (instrumented flows); de-duplicate the permission-check helpers.
