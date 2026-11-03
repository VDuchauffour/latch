#!/usr/bin/env bash
# Capture the README screenshots into assets/screenshots.
# Usage: scripts/screenshots.sh [AVD]  (boots AVD, default "latch", if no device is connected)
set -euo pipefail
cd "$(dirname "$0")/.."
avd="${1:-latch}"

if ! adb get-state >/dev/null 2>&1; then
    mise x android-sdk -- emulator -avd "$avd" -no-snapshot-save >/dev/null 2>&1 &
    adb wait-for-device
fi
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'

# Placeholder apps, only in the current user so they aren't duplicated in a work profile.
./gradlew -p screenshots/fakeapps assembleDebug
adb uninstall app.latch.fakeapps >/dev/null 2>&1 || true
adb install --user current screenshots/fakeapps/build/outputs/apk/debug/LatchFakeApps-debug.apk
./gradlew installDebug installDebugAndroidTest

# Clean status bar: fixed clock, full battery and wifi, no notifications.
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 1000
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile hide
demo notifications -e visible false
trap 'demo exit' EXIT

out=/sdcard/Android/data/app.latch/files/screenshots
adb shell rm -rf "$out"
adb shell am instrument -w -e screenshots true -e class app.latch.ScreenshotTest \
    app.latch.test/androidx.test.runner.AndroidJUnitRunner | tee /dev/stderr | grep -q '^OK '
mkdir -p assets/screenshots
adb pull "$out/." assets/screenshots/
adb shell cmd package set-home-activity app.latch/.MainActivity >/dev/null
