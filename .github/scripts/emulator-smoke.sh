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

# The step used to hang for 40 minutes AFTER the tests passed. The action runs us via
# @actions/exec, whose Node process cannot exit while any surviving descendant still holds the
# stdout/stderr pipes it inherited -- so a wedged emulator deadlocks the step no matter how the
# tests went. An earlier attempt killed only crashpad_handler and did nothing, because the
# emulator itself is the pipe holder. Tear it down here instead, redirecting everything to
# /dev/null so nothing we spawn inherits those pipes either.
#
# This must stay a trap (a trailing line is skipped by `set -e` on failure, which is exactly when
# a wedged emulator is most likely) and it must NOT write the sentinel -- see the note below.
teardown() {
  adb emu kill >/dev/null 2>&1 || true
  sleep 2
  pkill -9 -f qemu-system-x86_64 >/dev/null 2>&1 || true
  pkill -9 -f crashpad_handler >/dev/null 2>&1 || true
  adb kill-server >/dev/null 2>&1 || true
}
trap teardown EXIT

PKG=io.github.mzuhairkhan.pause

# The workflow gates the job on this file, not on the step's exit status, because the step can
# still hang after a perfectly good run. It is written ONCE, on the straight-line path, as the
# very last thing the script does.
#
# NEVER move this write into the trap. The trap fires on failure too, so a sentinel written there
# would report SUCCESS for every failed run.
SENTINEL="${GITHUB_WORKSPACE:-$PWD}/emulator-smoke-ok"
rm -f "$SENTINEL"

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

# Gradle exits 0 when ZERO tests ran -- this repo has already produced "Starting 0 tests /
# Finished 0 tests" with a green build after an install failure. Without this assertion the
# sentinel below would be written and the job would report success having tested nothing.
python3 - app/build/outputs/androidTest-results <<'ASSERT'
import sys, glob, os
import xml.etree.ElementTree as ET
root = sys.argv[1]
files = glob.glob(os.path.join(root, '**', 'TEST-*.xml'), recursive=True)
if not files:
    print("::error::No instrumented test result XML under %s - the run produced no results." % root)
    sys.exit(1)
tests = failures = errors = skipped = 0
for f in files:
    for ts in ET.parse(f).iter('testsuite'):
        tests += int(ts.get('tests') or 0)
        failures += int(ts.get('failures') or 0)
        errors += int(ts.get('errors') or 0)
        skipped += int(ts.get('skipped') or 0)
print("instrumented: tests=%d failures=%d errors=%d skipped=%d (%d file(s))"
      % (tests, failures, errors, skipped, len(files)))
if tests == 0:
    print("::error::Zero instrumented tests ran - Gradle reported success but nothing executed.")
    sys.exit(1)
if failures or errors:
    print("::error::Instrumented tests failed.")
    sys.exit(1)
if skipped:
    print("::error::Instrumented tests were skipped.")
    sys.exit(1)
ASSERT
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
# The main buffer is only an artifact, but the crash buffer backs the assertion below: if the
# capture fails we get an empty file, grep finds nothing, and a crashed build reads as healthy.
adb logcat -b crash -d > release-crash.txt || {
  echo "::error::Could not read the crash buffer - cannot assert the release build did not crash."
  exit 1
}

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

# Everything passed. See the note beside SENTINEL: this line stays out of the trap.
printf 'api=%s tests_passed=1 release_smoke=1 sha=%s
' "${API_LEVEL:-?}" "${GITHUB_SHA:-?}" > "$SENTINEL"
echo "Wrote $SENTINEL"
