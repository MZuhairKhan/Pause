# Pause

A small translucent floating button for Android that lets you set a "stop using this app" timer with one tap. Inspired by 4-7-8 breathing wind-downs and other digital-wellbeing rituals.

## What it does

Tap a draggable bubble that floats over any app. Pick a **duration** (5 / 10 / 15 min presets or a 1–120 min wheel) or set a **clock alarm**. When the timer fires, a full-screen 4-7-8 breathing wind-down helps you stop — or turn it off and the timer just ends.

Optionally, a **"Stop for now"** break covers apps you choose with a full-screen reminder whenever you open them.

No ads, no analytics, no trackers, no internet permission. The core timer doesn't use the Accessibility Service and reads no usage data. The optional break is the only feature that reads which app is in the foreground, only while a break is active, and only after you grant Usage Access.

## Install

Download the signed APK from the [latest release](https://github.com/MZuhairKhan/Pause/releases/latest).

## Build

Requires Android Studio Ladybug or newer, and JDK 21.

```sh
./gradlew assembleDebug
```

On first launch the app walks you through the permission grants it needs: display over other apps, post notifications, and a battery-optimization exemption so Android doesn't kill the service.

## Contributing

`CONTRIBUTING.md` covers reporting bugs, translating, and what a pull request needs — including a `CHANGELOG.md` entry, which CI enforces. `ONBOARDING.md` goes deeper on the architecture and the release process.

## Translations

Pause is translated with [Weblate](https://weblate.org/). The English strings in
`app/src/main/res/values/strings.xml` are the source; every other language is translated from them.

Finnish is reviewed and ships. German, Spanish, Italian, Portuguese, Swedish and Turkish are in the
repository as machine-assisted drafts that no native speaker has checked, so they are held out of
released builds until someone signs them off — correcting one of those six is the most useful thing
a translator can do here. `ONBOARDING.md` explains how to add a language by pull request instead.

## Credits

- **Idea** — Zarin Maisha
- **Finnish translation** — Joonas Nivala

## License

[GPL-3.0-or-later](LICENSE). Pause is free software: you may redistribute and modify it under
the terms of the GNU General Public License, version 3 or (at your option) any later version.
