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

PKG=io.github.mzuhairkhan.pause

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

adb uninstall "$PKG" || true
adb install -r /tmp/release.apk
adb shell am start -W -n "$PKG/.MainActivity"
sleep 5

adb logcat -d > release-logcat.txt || true

# R8 runs with an empty proguard-rules.pro, so a stripped-but-reflectively-needed class would
# surface here and nowhere else.
if grep -Eq "FATAL EXCEPTION|ClassNotFoundException|NoSuchMethodError" release-logcat.txt; then
  echo "::error::Release (minified) build crashed on launch - see the logcat artifact."
  grep -E -A5 "FATAL EXCEPTION|ClassNotFoundException|NoSuchMethodError" release-logcat.txt | head -40
  exit 1
fi

# pidof is toybox on Android and not present on every API level; fall back to ps.
if ! adb shell "pidof $PKG >/dev/null 2>&1 || ps -A 2>/dev/null | grep -q $PKG"; then
  echo "::error::Release build is not running after launch."
  exit 1
fi

echo "Release build launched and is still running."
echo "::endgroup::"

adb exec-out screencap -p > release-launch.png || true
