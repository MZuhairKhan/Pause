# Contributing to Pause

Thanks for taking an interest. Bug reports, translations and code are all welcome.

## Reporting a bug

Open an issue at https://github.com/MZuhairKhan/Pause/issues. Pause draws overlays and talks to
several permission systems, so behaviour varies a lot between devices — please include your Android
version, your phone model, and which permissions you had granted. "The bubble disappeared" is hard
to act on; "the bubble disappeared after the screen locked on Android 14, Samsung A54" is not.

## Translating

Translations are done on [Weblate](https://weblate.org/); no Git knowledge is needed, and the
per-project instructions there explain the conventions. English source strings live in
`app/src/main/res/values/strings.xml`.

Finnish is reviewed and ships. German, Spanish, Italian, Portuguese, Swedish and Turkish are
machine-assisted drafts that no native speaker has checked — correcting one of those is the most
useful contribution available right now. Unreviewed languages are deliberately excluded from
builds, so your language will not appear in the app until someone signs it off; ask in an issue if
you want a test build.

To add a language by hand instead, `ONBOARDING.md` explains the layout.

## Code changes

`java` is not on PATH; use the JDK bundled with Android Studio (JDK 21, matching CI) and set
`JAVA_HOME` to it. From the repo root:

```bash
./gradlew testDebugUnitTest     # JVM unit tests
./gradlew lintDebug             # lint
./gradlew assembleDebug         # installable debug APK
```

CI runs all three on every pull request and the build fails on a lint error, so run them before
you push.

Work on a branch and open a pull request — please don't commit to `main` directly.

**Every pull request needs a `CHANGELOG.md` entry** under `[Unreleased]`. A separate CI job checks
this and fails without one. Write a sentence saying what changed and why it matters, not a
one-word line.

Match the surrounding code: the existing style, naming and comment density are the spec. Comments
should explain why something is done, not restate what the code does. New behaviour needs tests in
the same change.

## Architecture

`ONBOARDING.md` documents the architecture, the permission model and the translation setup. Be
aware that the second half of that file is a maintainer's release tracker — F-Droid blockers, store
plans, open decisions — and is not something you need to read to contribute.
