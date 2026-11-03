set dotenv-load

emulator := "mise x android-sdk -- emulator"

default:
    @just --list

# One-time setup: install tools, accept SDK licenses, and install all agent skills
setup:
    just _setup-tools
    just _setup-skills

# Install tools (JDK 17, Android SDK, yq) via mise and accept SDK licenses
[private]
_setup-tools:
    mise install
    mise x android-sdk -- sdkmanager --licenses

# Install all Android agent skills into this project
[private]
_setup-skills:
    android skills add --all --project .

# Build debug APK
debug:
    ./gradlew assembleDebug

# Build release APK (R8 + resource shrinking, debug-key signed)
release:
    ./gradlew assembleRelease

# Install debug build on connected device/emulator
install:
    ./gradlew installDebug

# Build and install debug, then start the emulator window
run AVD="latch":
    {{ emulator }} -avd {{ AVD }} -no-snapshot-save &
    adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
    ./gradlew installDebug

# Stop the running emulator
emu-stop:
    adb emu kill || adb devices

# JVM unit tests
test:
    ./gradlew testDebugUnitTest

# Instrumented tests on device/emulator
test-device:
    ./gradlew connectedDebugAndroidTest

# Lint
lint:
    ./gradlew lintDebug

# Remove build artifacts
clean:
    ./gradlew clean

# Stop Gradle daemons (needed after changing .env / JAVA_TOOL_OPTIONS)
gradle-stop:
    ./gradlew --stop
