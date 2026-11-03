# Pause on iOS — feasibility

Written for the maintainer and designers. Nothing here is built; it says what an iPhone version
could and couldn't be, so the decision to start is an informed one.

**Short version:** a faithful port is impossible, because iOS has no way to draw over other apps. A
*different* Pause is feasible: a system timer that ends in the breathing screen, plus optional
app shielding. It needs a Mac, a paid developer account and a licensing decision first.

Claims below come from Apple's WWDC25 AlarmKit session, Apple developer forum threads and app store
listings (sources at the end). I haven't built or run any of it; check Apple's current docs before
committing to a design.

## What Android Pause does, and what iOS allows

| Android feature | iOS equivalent | Verdict |
|---|---|---|
| Draggable bubble over any app | None. iOS apps can't draw over other apps. Closest: a Control Center control, a Lock Screen / Home widget, a Live Activity. | Different shape |
| Timer that fires on time | **AlarmKit** (iOS 26): system-scheduled countdown with a Live Activity. | Good fit |
| Full-screen 4-7-8 wind-down over whatever you're doing | The alarm alert's custom "Open" button launches Pause into its own breathing screen. It can't be forced over another app. | Weaker: one tap more |
| "Stop for now" break covering chosen apps | **Screen Time API** (FamilyControls + ManagedSettings + DeviceActivity): shield chosen apps. | Possible, needs Apple approval |
| Interrupt on opening Instagram/TikTok | A user-made **Shortcuts "app opened" automation** that opens Pause (how *one sec* works). | Possible, user sets it up |
| Mute media while winding down | No public API. | Not possible |
| Quick Settings tile | Control Center control. | Good fit |

## Pieces worth knowing

- **AlarmKit** lets a third-party app schedule a countdown alarm. The countdown and paused states
  are drawn by the app's own Live Activity, in a widget extension. A custom button can run an App
  Intent that opens the app. Without a Live Activity the system may drop the alarm. The Live
  Activity isn't available in some states, such as before first unlock after a restart.
- **Screen Time API** needs the *Family Controls* entitlement. Apple has to approve the
  *Distribution* version before TestFlight or release, and developers report each extension (shield
  configuration, device activity monitor) having to be approved too. Extensions share state with the
  app only through an App Group. Reports also say timed unlocks re-shielding through
  `intervalDidEnd` can be unreliable.
- **Shortcuts automations** need no entitlement and *delay* rather than block, which is the
  philosophy of Pause. The user creates them, and they can delete them, so Pause can't enforce it.
- **Picture-in-picture floating clock** is possible by feeding frames to a PiP window (a few apps do
  it) but it is a workaround, App Review friction is reported, and a timer isn't what PiP is for.
  Not recommended.

## Proposed shape

A native **SwiftUI** app. The shared logic worth porting is the framework-free part of
`PauseLogic.kt` (about 300 lines: time formatting, the breathing frame maths, settings ranges, the
timer-fire decision). Port it by hand with the same test cases; a Kotlin Multiplatform setup is
more machinery than 300 lines justify.

1. **Timer:** pick a duration, AlarmKit counts it down with a Live Activity.
2. **Wind-down:** the alert's Open button lands on the breathing screen; same 4-7-8, same lock
   rules, same actions (Keep scrolling / Stop for now / Snooze).
3. **Break:** "Stop for now" shields chosen apps through Screen Time for N minutes.
4. **Entry points:** a Control Center control and a widget start a timer; a Shortcuts recipe in
   the app's setup opens it when a chosen app launches.

## Costs and blockers

- **Mac required.** Xcode only runs on macOS, and this machine is Windows on ARM. Options: a Mac
  mini, or macOS CI/cloud builders (slow for iterating on UI and entitlements).
- **Apple Developer Program:** about 99 USD a year, needed for device installs beyond a week and
  for TestFlight and the App Store.
- **Entitlement approval** for Family Controls: an application and a wait. Do it early; the rest of
  the app doesn't depend on it.
- **A real iPhone** for testing; the simulator can't cover Screen Time or alarms well.
- **Privacy claim:** "no internet permission" is an Android manifest fact. iOS has no equivalent
  switch, so the store listing would state it as a promise, and the Screen Time APIs deliberately
  hide which apps were chosen, which suits Pause.

## Licensing: decide before outside code arrives

Pause is GPL-3.0-or-later. The App Store's terms and the GPL are widely reported to conflict, and
projects that ship GPL code there (Signal, for one) added an explicit App Store exception to their
license. That is only straightforward when one party holds the copyright. As of today git history
shows you as the only author of code (outside contributions so far are docs and dependency bumps),
so you can grant yourself the exception or dual-license your own code.

- Every outside code contribution from now makes that harder: each contributor holds copyright in
  their part. Either add a contributor agreement, or add the exception to the license before
  accepting code.
- Third-party dependencies need to be compatible too.
- This is practitioner consensus, not legal advice. Have someone qualified read the exact wording.

## Phased path

1. **Spike (a weekend, needs a Mac and a phone):** AlarmKit countdown with a Live Activity and an
   Open button into a stub breathing screen. Question answered: does the flow feel right?
2. **Decide:** licensing exception, and whether to apply for Family Controls.
3. **MVP:** timer, wind-down, settings, Control Center control. No app shielding.
4. **Shielding:** once the entitlement is approved.
5. **TestFlight**, then the store.

## Sources

- [WWDC25 session 230, AlarmKit](https://developer.apple.com/videos/play/wwdc2025/230)
- [Family Controls distribution threads](https://developer.apple.com/forums/thread/825312) and
  [extension entitlement thread](https://developer.apple.com/forums/thread/824845)
- [Timed unlock / intervalDidEnd thread](https://developer.apple.com/forums/thread/820956)
- [one sec on the App Store](https://apps.apple.com/de/app/one-sec-screen-time-focus/id1532875441?l=en-GB)
- [PiP floating-content review question](https://developer.apple.com/forums/thread/839424)
- [Signal license update](https://signal.org/blog/license-update/) and
  [Pidgin: why no iOS version](https://developer.pidgin.im/wiki/WhyNoiOSVersion)
