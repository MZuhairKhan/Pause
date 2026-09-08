# Contributing to Pause

Thanks for taking an interest. Bug reports, translations and code are all welcome.

## Reporting a bug

Open an issue at https://github.com/MZuhairKhan/Pause/issues. Behaviour varies a lot between
devices, so include your Android version, phone model and which permissions were granted. "The
bubble disappeared" is hard to act on; "…after the screen locked on Android 14, Samsung A54" is not.

## Translating

Translations are done on [Weblate](https://weblate.org/) — no Git knowledge needed, and the
instructions there cover the conventions. English sources are in `app/src/main/res/values/strings.xml`.

Finnish is reviewed and ships. German, Spanish, Italian, Portuguese, Swedish and Turkish are
machine-assisted drafts no native speaker has checked, and correcting one is the most useful
contribution going. Unreviewed languages are excluded from builds, so yours won't appear in the app
until it is signed off — ask in an issue for a test build. `ONBOARDING.md` covers doing it by hand.

## Code changes

`java` is not on PATH; use the JDK bundled with Android Studio (JDK 21, matching CI) and set
`JAVA_HOME` to it. From the repo root:

```bash
./gradlew testDebugUnitTest     # JVM unit tests
./gradlew lintDebug             # lint
./gradlew assembleDebug         # installable debug APK
```

CI runs all three on every pull request and fails on a lint error, so run them before you push.
Work on a branch; please don't commit to `main` directly.

**Every pull request needs a `CHANGELOG.md` entry** under `[Unreleased]` — a separate CI job fails
without one. A sentence on what changed and why, not a one-word line. (Translation-only pull
requests are exempt.)

Match the surrounding code; comments should say why, not restate what. New behaviour needs tests in
the same change.

## Architecture

`ONBOARDING.md` documents the architecture, permission model and translation setup. Its second
half is a maintainer's release tracker — F-Droid blockers, store plans, open decisions — which you
don't need to read to contribute.
