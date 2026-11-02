# Build, install, and test tasks (mirrors README "Build & run")
# Requires `just` and JDK/Android SDK configured via local.properties.

default:
    @just --list

# Build debug APK
debug:
    ./gradlew assembleDebug

# Build release APK (R8 + resource shrinking, debug-key signed)
release:
    ./gradlew assembleRelease

# Install debug build on connected device/emulator
install:
    ./gradlew installDebug

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
