set dotenv-load

emulator := "mise x android-sdk -- emulator"

default:
    @just --list

# One-time setup: install tools, accept SDK licenses, install all agent skills and git hooks
setup:
    just _setup-tools
    just _setup-skills
    just _setup-precommit

# Install tools (JDK 17, Android SDK, pre-commit, yq) via mise and accept SDK licenses
[private]
_setup-tools:
    mise install
    mise x android-sdk -- sdkmanager --licenses

# Install git hooks (pre-commit and pre-push) via pre-commit
[private]
_setup-precommit:
    mise x pre-commit -- pre-commit install

# Install all Android agent skills into this project
[private]
_setup-skills:
    android skills add --all --project .

# Build debug APK
debug:
    ./gradlew assembleDebug

# Build release APK (R8 + resource shrinking; debug-key signed unless LATCH_KEYSTORE_* env vars are set)
release:
    ./gradlew assembleRelease

# One-time: create the release signing key (kept outside the repo) and store it as GitHub secrets for the Release APK workflow
release-keystore KEYSTORE="~/.android/latch-release.jks":
    #!/usr/bin/env bash
    set -euo pipefail
    keystore="{{ KEYSTORE }}"
    keystore="${keystore/#\~/$HOME}"
    if [ -e "$keystore" ]; then
        echo "$keystore already exists; reusing it (back it up: losing it breaks updates for every install)"
    else
        read -rsp "Keystore password (min 6 chars): " pass; echo
        mkdir -p "$(dirname "$keystore")"
        mise x java -- keytool -genkeypair -keystore "$keystore" -alias latch -keyalg RSA -keysize 4096 \
            -validity 10000 -storepass "$pass" -keypass "$pass" -dname "CN=Latch"
        echo "Created $keystore; back it up, losing it breaks updates for every install"
    fi
    [ -n "${pass:-}" ] || { read -rsp "Keystore password: " pass; echo; }
    base64 -w0 "$keystore" | gh secret set LATCH_KEYSTORE_BASE64
    printf %s "$pass" | gh secret set LATCH_KEYSTORE_PASSWORD
    printf %s "$pass" | gh secret set LATCH_KEY_PASSWORD
    printf latch | gh secret set LATCH_KEY_ALIAS

# Install debug build on connected device/emulator
install:
    ./gradlew installDebug

# Build and install debug, then start the emulator window
run AVD="latch":
    {{ emulator }} -avd {{ AVD }} -no-snapshot-save &
    adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
    ./gradlew installDebug

# Capture the README screenshots into assets/screenshots (boots the AVD if no device is connected)
screenshots AVD="latch":
    #!/usr/bin/env bash
    set -euo pipefail
    if ! adb get-state >/dev/null 2>&1; then
        {{ emulator }} -avd {{ AVD }} -no-snapshot-save >/dev/null 2>&1 &
        adb wait-for-device
    fi
    adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
    # Only in the current user, so they aren't duplicated in a work profile.
    ./gradlew -p screenshots/fakeapps assembleDebug
    adb uninstall app.latch.fakeapps >/dev/null 2>&1 || true
    adb install --user current screenshots/fakeapps/build/outputs/apk/debug/LatchFakeApps-debug.apk
    ./gradlew installDebug installDebugAndroidTest
    # Clean status bar: fixed clock, full battery and signal, no notifications.
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

# Stop the running emulator
emu-stop:
    adb emu kill || adb devices

# JVM unit tests
test:
    ./gradlew testDebugUnitTest

# Instrumented tests on device/emulator
test-device:
    ./gradlew connectedDebugAndroidTest

# Android Lint
lint:
    ./gradlew lintDebug

# Format Kotlin sources (Spotless + ktlint)
fmt:
    ./gradlew spotlessApply

# Check formatting without modifying files
fmt-check:
    ./gradlew spotlessCheck

# Run the same checks as CI: formatting, lint, unit tests
ci:
    ./gradlew spotlessCheck lintDebug testDebugUnitTest

# Remove build artifacts
clean:
    ./gradlew clean

# Stop Gradle daemons (needed after changing .env / JAVA_TOOL_OPTIONS)
gradle-stop:
    ./gradlew --stop
