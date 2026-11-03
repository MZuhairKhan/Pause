# Pause — behaviour flow

How the app moves between its states: first-run setup, the floating bubble, the timer,
the wind-down, and the app-blocking break. (Renders as a diagram on GitHub.)

The first diagram is what the app does. The three after it are plans for the designers to check
before anything is built; each says which branch it belongs to, and moves up into the first
diagram when its change lands.

| # | Diagram | Status |
|---|---|---|
| 1 | The whole app | Current (with `feat/simple-main-screen`) |
| 2 | Settings map | Proposed: settings hub |
| 3 | Timer feedback moments | In progress: haptics and fade-in |
| 4 | Crash and feedback reporting | Proposed: crash reports |

## 1. The whole app

```mermaid
flowchart TD
    Launch([App launched]) --> Onboarded{Onboarding complete?}

    Onboarded -- No --> Wizard["Setup wizard:<br/>1. Welcome<br/>2. Language (applies when picked)<br/>3. Permissions<br/>4. Block distracting apps<br/>5. Which app you use most (bubble size)<br/>6. When your timer ends<br/>7. You're all set"]
    Wizard -->|Get started| Finish[Mark onboarding complete,<br/>start overlay if it can draw]
    Onboarded -- Yes --> Main[Main screen:<br/>logo, Start/Stop Pause,<br/>missing-permission card]

    Finish --> Idle
    Main -->|Start Pause| Idle
    Main -->|Stop Pause| Off
    Main -->|Tap the gear| Settings[Settings screen:<br/>permissions, bubble, appearance,<br/>breathing, app blocking, about]
    Settings -->|Back| Main

    Idle([Bubble floating - idle]) -->|Tap bubble| Picker[Timer picker]
    Picker -->|Set duration / clock alarm| Running
    Picker -->|Tap outside / Back| Idle
    Idle -->|Drag to dismiss target| Off

    Running([Timer running - bubble shows countdown or draining hourglass])
    Running -->|Slide to dismiss while running| CancelStop[Cancel the alarm, THEN stop the overlay]
    Running -->|Cancel timer in picker| Idle
    Running -->|Timer fires| WindDown
    CancelStop --> Off

    WindDown{Wind-down opens}
    WindDown --> Breathe["4-7-8 breathing - non-skippable lock window, 0s if skipped"]
    Breathe -->|lock window elapses| Actions["Keep scrolling / Stop for now / Snooze"]

    Actions -->|Keep scrolling| Idle
    Actions -->|Snooze N min| Running
    Actions -->|Stop for now| StopForNow{Apps to block chosen<br/>and Usage access granted?}
    StopForNow -- Yes --> Break[App-blocking break:<br/>cover those apps when opened,<br/>bubble returns to idle]
    StopForNow -- No --> Off
    Break -->|break ends, or End break early| Idle

    Off(["Overlay stopped - ongoing 'Start Pause' notification"]) -->|Tap Start| Idle
    Off -->|Device reboot| Off
```

## 2. Settings map (proposed)

The gear opens a short list of categories instead of one long scroll, in the style of One UI's own
Settings. Each row shows a one-line summary of its current state. Nothing here adds a setting; it
re-homes what exists, plus the feedback and credits pages.

```mermaid
flowchart LR
    Main[Main screen] -->|Gear| List["Settings list"]

    List --> Perm["Permissions<br/>summary: All set / 1 missing"]
    List --> Bubble["Bubble<br/>summary: preset name"]
    List --> Look["Appearance<br/>summary: theme, accent"]
    List --> Wind["Wind-down<br/>summary: minimum time, snooze"]
    List --> Block["App blocking<br/>summary: apps chosen, minutes"]
    List --> Fb["Feedback and bug reports"]
    List --> Cred[Credits]
    List --> About["About<br/>version, license, source"]

    Perm --> PermPage["The four permission rows"]
    Bubble --> BubblePage["Countdown on bubble,<br/>size preset, custom sliders"]
    Look --> LookPage["Theme, accent colour,<br/>custom colour"]
    Wind --> WindPage["Minimum time, snooze length<br/>(4-7-8 timings are fixed)"]
    Block --> BlockPage["Break length, choose apps,<br/>usage-access hint"]

    Fb --> Bug["Report a bug"]
    Fb --> Feat["Suggest a feature"]
    Fb --> Ask["Ask a question"]
    Fb --> Tr["Help translate"]
    Fb --> LastCrash["Report last crash<br/>(only if one is saved)"]
    Bug -->|Opens browser, form pre-filled with<br/>version, Android, device, permissions| GH[GitHub issue form]
    Feat --> GH
    Ask --> GH
    Tr --> Weblate[Weblate project]
    LastCrash --> CrashScreen["Crash report screen (diagram 4)"]

    Cred --> CredPage["Contributors, by role,<br/>read from CONTRIBUTORS.md"]
```

Back, from any page, returns one level up: sub-page to the list, the list to the main screen.

## 3. Timer feedback moments (in progress)

Where the app now answers the user physically. A confirm haptic marks the moments that matter
(a timer set, a wind-down arriving); navigation taps keep the lighter tick so the two feel
different. The fade follows One UI's motion guide: 500 ms at most, on its own curve.

```mermaid
flowchart TD
    Idle([Bubble idle]) -->|Tap bubble| Picker[Timer picker]
    Picker -->|Switch Duration / Alarm tab| Tick1["Light tick"]
    Tick1 --> Picker
    Picker -->|Tap 5 / 10 / 15, or Start| Confirm1["Confirm haptic"]
    Confirm1 --> Running([Timer running])

    Running -->|Deadline reached| Arrive["Wind-down fades in over 0.5 s<br/>+ one confirm haptic"]
    Arrive --> Lock["4-7-8 breathing<br/>actions hidden for the lock window"]
    Lock -->|Lock elapses, actions fade in| Actions["Keep scrolling / Stop for now / Snooze"]

    Actions -->|Snooze| Confirm2["Confirm haptic"]
    Confirm2 --> Running
    Actions -->|Keep scrolling or Stop for now| Tick2["Light tick"]
    Tick2 --> Done(["Idle, or the break begins"])
```

If the phone has animations or touch feedback turned off, Android's own settings suppress the fade
and the haptics; the flow is otherwise the same.

## 4. Crash and feedback reporting (proposed)

Pause has no internet permission, so nothing is sent by the app itself. A crash is saved on the
phone; the user chooses whether it goes anywhere, and "going" means opening a pre-filled GitHub issue
form in the browser.

```mermaid
flowchart TD
    Crash([Uncaught exception, in the service or the app]) --> Save["Crash saved on the phone:<br/>version, Android, device, stack trace,<br/>recent log of Pause's own process"]
    Save --> Notif{Notifications allowed?}

    Notif -- Yes --> Banner["Notification:<br/>Pause stopped, tap to report"]
    Notif -- No --> Kept["Report kept quietly"]
    Banner -->|Tap| Screen
    Kept --> Later["Settings, Feedback:<br/>Report last crash"]
    Later --> Screen

    Screen["Crash report screen:<br/>shows exactly what would be shared,<br/>optional 'What were you doing?' note"]
    Screen -->|Report on GitHub| Open["Full report copied,<br/>browser opens the crash form,<br/>pre-filled and trimmed to fit a URL"]
    Screen -->|Copy| Clip[Report on the clipboard]
    Screen -->|Share| Sheet[Android share sheet]
    Screen -->|Don't send| Deleted[Report deleted]

    Open --> Form["GitHub form: the user reads it,<br/>adds anything, and presses Submit"]
```

The GitHub form requires the device, Android version, app version and permission state, so a report
can't arrive without them; the app fills them in, and a person filing by hand has to.

## Notes on specific behaviours

- **Slide to dismiss while a timer is running** drags the bubble onto the dismiss target. This
  calls `stopSelf()`, and `onDestroy()` runs `cancelPendingAlarm()` first — so the pending alarm
  is cancelled *before* the overlay is torn down, and it can never fire after the bubble is gone.
- **Keep scrolling** dismisses the wind-down only; the bubble stays floating (idle), ready for the
  next timer.
- **Stop for now** starts the app-blocking break **if** you've chosen apps to block and granted
  Usage access (the bubble returns to idle while the break covers those apps). If no apps are
  chosen, it leaves the current app and stops the overlay entirely.
- **Snooze** dismisses the wind-down and re-arms the timer for the chosen number of minutes.
- **Persistent notifications (not truly pinned).** Both are ongoing and carry `FLAG_NO_CLEAR`: the
  running foreground-service notification, and the "Start Pause" notification shown while the
  overlay is off (re-posted after a reboot by `BootReceiver`). Neither flag makes them
  undismissable. On Android 14+ the user may swipe an ongoing notification away while the app is
  backgrounded, and **Samsung's One UI removes them on "Clear" regardless of `FLAG_NO_CLEAR`** —
  confirmed on a device. Treat AOSP behaviour as the best case, not the guarantee. Real pinning
  needs Android 16's promoted-ongoing notifications, which require SDK 36; see the Google Play
  plan in ONBOARDING.md.
- **First-run wizard** shows only on first launch (gated by `SettingsStore.onboardingComplete`).
  The language is applied the moment it's picked, so every later page is already in it; below
  Android 13 that recreates the activity, which is why the wizard's state is saved across it.
