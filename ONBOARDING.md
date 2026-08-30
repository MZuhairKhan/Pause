# Pause — Onboarding & Release Tracker

**Pause** is an Android digital-wellbeing app: a draggable floating bubble runs a timer; when it
ends, a 4-7-8 breathing wind-down (skippable) plays, and a "Stop for now" break can cover chosen
apps (TikTok/Instagram/…) for a set time. Kotlin, Jetpack Compose setup screen + a foreground
`OverlayService` that draws the overlays. Package `io.github.mzuhairkhan.pause`.

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

## Translations

UI text is all in `res/values/strings.xml` (+ `<plurals>`); each language is a
`res/values-<code>/strings.xml`. `res/xml/locales_config.xml` lists the shipped languages and powers
the Android 13+ per-app language picker (Settings → Apps → Pause → Language).

Currently shipped: **English** (default) + **Finnish** (`fi`, reviewed by Joonas Nivala).

**Round 2 reviewed.** The 22 strings changed after Joonas's first pass (the `kelluva painike`
→ `kupla` terminology switch, the `peiteilmoituspalvelu` → `Näytä/Piilota kupla` rewording,
`breathing_done` → `Harjoitus ohi`, the new `unit_*` / `slider_readout` resources and the
quote-mark change) went back to him and came back with a single correction, now applied:
`onb_size_body` is "Sovitamme kuplan koon sovelluksen painikkeisiin." — `kuplan koon` keeps
the sense of *size*, and `sovelluksen` is explicit where `sen` was a vague pronoun.

Add a language by hand: copy `values/strings.xml` → `values-<code>/strings.xml`, translate the values
(keep the `name=` keys and the `%1$s` / `%1$d` / `✓` bits intact), and add
`<locale android:name="<code>"/>` to `locales_config.xml`. `./gradlew.bat lintDebug` flags any missing
keys or mismatched placeholders.

Community translations (recommended): host on **Weblate** (free for FOSS at hosted.weblate.org). Point
a component at `app/src/main/res/values*/strings.xml` (Android resource format); translators contribute
online and Weblate pushes commits/PRs per language, with per-language progress. Add each new `<locale>`
to `locales_config.xml` when its file lands.

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
- [x] **Added a `LICENSE`: GPLv3** (strong copyleft — derivatives must stay open source, no
      proprietary forks). Compatible with the app's Apache-2.0 deps (Apache-2.0 is one-way
      compatible *into* GPLv3); **avoid GPLv2**, which is incompatible with Apache-2.0. F-Droid
      requires a free license.
- [x] **Release build compiles** — `assembleRelease` with R8/minify + resource shrinking succeeds
      against current code (1.7 MB unsigned APK); `lintVitalRelease` passes. F-Droid builds release
      from the git tag and signs it themselves, so **no signing config is needed for F-Droid**.
- [ ] **Smoke-test that release APK on a real device** — minified builds can fail at runtime where
      debug builds do not (R8 stripping reflectively-reached code); `proguard-rules.pro` is empty,
      so this is unverified.
- [ ] **On-device verify the timer fires the wind-down** when backgrounded / screen-off / swiped
      from recents (the highest-risk functional path; grant "Ignore battery optimization").
- [x] F-Droid metadata: `fastlane/metadata/android/en-US/` (title, short + full descriptions,
      per-versionCode changelogs). Still to add: `images/` (icon + phone screenshots).
- [ ] **fdroiddata submission** (source + issue-tracker URLs, license, `UpdateCheckMode: Tags`).
- [ ] Add a `<monochrome>` layer to the adaptive icon (themed-icon polish).

### Google Play — release plan

Play is a bigger lift than F-Droid: F-Droid builds and signs from our git tag, whereas Play
demands a signed bundle, a console full of declarations, and review of three permissions that
Google treats as sensitive. Ordered by what blocks what.

#### 1. Build changes (do these first — they gate everything else)
- [ ] **Raise `targetSdk`.** Play enforces an annual target-API floor: roughly, by 31 August each
      year new apps and updates must target the API level released the previous year. We are on
      **35**; as of late-2026 the floor is likely **36**. *Verify against the current Play policy
      page before planning around it* — if it has already moved, this blocks upload outright.
- [ ] **Produce an `.aab`.** Play has not accepted APKs for new apps since 2021. `assembleRelease`
      is not enough; wire and test `bundleRelease`.
- [ ] **`bundle { language { enableSplit = false } }`.** Without it, Play splits by language and
      the per-app language picker can select a locale whose strings were never downloaded. Lint
      already flags this (`AppBundleLocaleChanges`). Non-negotiable now that we ship Finnish.
- [ ] **Release signing config + Play App Signing.** `app/build.gradle.kts` has no `signingConfig`
      at all — the release workflow signs externally with `apksigner`. Play needs an upload key
      enrolled in Play App Signing.
- [ ] **Smoke-test the minified bundle on a device.** `isMinifyEnabled = true` with a completely
      empty `proguard-rules.pro`. R8 can strip reflectively-reached code, and a bundle adds
      resource-splitting on top. This has never been run on hardware.

#### 2. Console declarations (expect review friction)
- [ ] **`FOREGROUND_SERVICE_SPECIAL_USE`** — highest rejection risk. Google wants a
      standard FGS type where one fits, and reviews `specialUse` by hand. A justification is
      already drafted in `AndroidManifest.xml` (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`); reuse that
      wording in the console so the two match.
- [ ] **`PACKAGE_USAGE_STATS`** — a sensitive permission. Needs a declaration plus a prominent
      in-app disclosure *before* the permission is requested. Our advantage: it is genuinely
      optional and only powers the app-blocking break, so say exactly that.
- [ ] **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** — restricted; must map to an allowed use case.
      An alarm that must fire on time is a defensible one, but expect to argue it.
- [ ] **`SYSTEM_ALERT_WINDOW`** — must be core to the product. It is, and that is easy to show.
- [ ] Data safety form. Easy win: no collection, no sharing, no network code at all.
- [ ] Content rating questionnaire; target-audience declaration.
- [ ] **Hosted privacy policy URL** — required, and doubly so with the sensitive permissions.

#### 3. Account and testing runway (start early — this is the long pole)
- [ ] Developer account (one-off 25 USD).
- [ ] **Closed testing before production.** Personal (non-organisation) accounts must run a
      closed test with a minimum number of testers held for a continuous period — recently
      12 testers for 14 days — before production access unlocks. Recruit testers *now*; this
      is calendar time no amount of engineering removes. Verify the current numbers in the console.

#### 4. Store listing assets (none exist yet)
- [ ] 512x512 PNG app icon, 1024x500 feature graphic, and at least two phone screenshots.
      `fastlane/metadata/android/en-US/` has all the text but no `images/` directory.
- [ ] The listing text can be reused from the F-Droid `full_description.txt` largely as-is.

#### 5. Product polish worth doing before either store
- [ ] `<monochrome>` layer on the adaptive icon (lint `MonochromeLauncherIcon`; themed icons).
- [ ] **The launcher glyph is undersized.** The hourglass occupies roughly 43% of the 108dp
      canvas where the adaptive-icon safe zone is about 61%. It will read noticeably smaller than
      neighbouring icons. The notification icon, by contrast, is correct.
- [ ] Delete the four dead `reminder_*` strings (unreferenced; superseded by the wind-down).
- [ ] Finnish re-review (21 strings) and the six other languages currently out for review.
- [ ] Instrumented tests — `app/src/androidTest` does not exist. The overlay, alarm and
      app-blocking paths are exactly what unit tests cannot reach.

#### 6. Decisions to make deliberately
- **GPLv3 and Play App Signing.** Google holds the signing key, so a user cannot rebuild and
  install their own binary. Many GPL apps ship on Play regardless, but decide consciously rather
  than discover the argument later.
- **Two stores, two signing keys.** An F-Droid install and a Play install are different apps to
  Android and cannot update each other. Pick which is canonical and say so in the README.
- **No crash reporting.** The store description promises no analytics and no network access.
  Keeping that promise means shipping blind. That is a legitimate choice — just an explicit one.

### Quality backlog (not blocking)
- [x] Internationalization: all UI strings live in `strings.xml`; Finnish (`values-fi`) added
      (reviewed by Joonas Nivala) and Android 13 per-app language wired (`res/xml/locales_config.xml`).
- [ ] More tests (instrumented flows); de-duplicate the permission-check helpers.
