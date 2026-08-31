#!/usr/bin/env bash
#
# Runs the instrumented tests and then smoke-tests the MINIFIED release build on a booted
# emulator. Invoked from .github/workflows/emulator.yml.
#
# This lives in a file rather than inline in the workflow on purpose:
# reactivecircus/android-emulator-runner executes its `script:` input one line at a time, each
# through a separate `sh -c`. That means shell variables do not survive between lines, `set -e`
# has no effect, and any multi-line `if ... fi` is split into invalid fragments. Calling a single
# script keeps normal shell semantics -- and lets us use bash rather than the runner's dash.
set -euo pipefail

# Work around ReactiveCircus/android-emulator-runner#385: the emulator spawns crashpad_handler
# children, and the action's teardown waits on the whole process tree rather than the emulator
# alone, so those orphans deadlock the step. It bites API 26 here; API 35 shuts down cleanly.
# This has to run from inside the action's own script -- a later workflow step never gets to run
# while the step ahead of it is hung -- and it has to be a trap rather than a trailing command,
# because `set -e` means a failing test run exits before any trailing line is reached, which is
# exactly when the emulator is most likely to leave orphans behind.
trap 'pkill -SIGTERM crashpad_handler 2>/dev/null || true' EXIT

PKG=io.github.mzuhairkhan.pause

# The action waits for sys.boot_completed, but on API 26 the package manager is still coming up
# behind it -- the boot log shows "device offline" and a failed adb connect -- and an install
# fired at that window dies with "Failure calling service package: Broken pipe (32)" and runs
# zero tests. Wait until pm actually answers before letting Gradle install anything.
echo "Waiting for the package manager to answer..."
for _ in $(seq 1 60); do
  if adb shell pm path android >/dev/null 2>&1; then break; fi
  sleep 2
done
adb shell pm path android >/dev/null 2>&1 || {
  echo "::error::Package manager never became ready; the emulator is not usable."
  exit 1
}

# Clearing the ring buffer fails on some API levels ("failed to clear the 'main' log") and is
# only a convenience, so never let it end the run.
adb logcat -c || true

echo "::group::Instrumented tests"
./gradlew connectedDebugAndroidTest --stacktrace
echo "::endgroup::"

echo "::group::Release build smoke test (R8/minify)"
# The release APK is unsigned; sign it with a throwaway debug key just so it can be installed.
keystore="$HOME/.android/debug.keystore"
if [ ! -f "$keystore" ]; then
  keytool -genkeypair -keystore "$keystore" -alias androiddebugkey \
    -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000
fi

bt=$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -n1)
[ -n "$bt" ] || { echo "::error::No build-tools found under $ANDROID_HOME."; exit 1; }

unsigned=app/build/outputs/apk/release/app-release-unsigned.apk
[ -f "$unsigned" ] || { echo "::error::Missing $unsigned - did assembleRelease run?"; exit 1; }

"$bt/zipalign" -p -f 4 "$unsigned" /tmp/aligned.apk
"$bt/apksigner" sign --ks "$keystore" --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android \
  --out /tmp/release.apk /tmp/aligned.apk

# The instrumented tests left the debug build installed, signed with a different key.
adb uninstall "$PKG" || true
adb install -r /tmp/release.apk

# Clear immediately before launching so the captured log covers this app's start-up only.
adb logcat -c || true
adb shell am start -W -n "$PKG/.MainActivity"
sleep 5

adb logcat -d > release-logcat.txt || true
adb logcat -b crash -d > release-crash.txt || true

# Only crashes attributable to THIS app count. The main buffer is full of unrelated system
# noise -- Google's API 35 image logs ClassNotFoundException from com.android.settings'
# SliceDataConverter on every boot, which a naive grep reads as our app exploding.
if grep -q "$PKG" release-crash.txt 2>/dev/null; then
  echo "::error::Release (minified) build crashed on launch - see the logcat artifact."
  grep -B5 -A30 "$PKG" release-crash.txt | head -60
  exit 1
fi

# The strongest signal: R8 stripping something reflectively-needed kills the process outright.
# pidof is toybox on Android and not present on every API level, so fall back to ps.
if ! adb shell "pidof $PKG >/dev/null 2>&1 || ps -A 2>/dev/null | grep -q $PKG"; then
  echo "::error::Release build is not running after launch."
  echo "--- crash buffer ---"; tail -60 release-crash.txt || true
  exit 1
fi

echo "Release build launched and is still running."
echo "::endgroup::"

adb exec-out screencap -p > release-launch.png || true
