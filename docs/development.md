# Development

## Build & run

Requirements: JDK 17, Android SDK with platform 35.

One-time setup (installs JDK 17, the Android SDK, and yq via [mise](https://mise.jdx.dev/), then accepts SDK licenses):

```sh
mise install # or: just setup (also accepts SDK licenses)
```

```sh
./gradlew installDebug
adb shell cmd package set-home-activity app.latch/.MainActivity # make Latch the home app
```

With multiple devices/emulators connected, target one via `adb`'s serial:

```sh
export ANDROID_SERIAL=emulator-5554 # gradle installDebug and connected tests use only this device
```

Release build (R8 + resource shrinking; signed with the debug key unless the `LATCH_KEYSTORE_*` env vars are set):

```sh
./gradlew assembleRelease
```

## Releasing

[release-drafter](../.github/release-drafter.yml) keeps a draft release up to date as PRs merge. Publishing it triggers the [Release APK](../.github/workflows/release.yml) workflow, which builds the APK with the version from the tag (`v1.2.3` → `versionName` 1.2.3, `versionCode` 10203), signs it with the release key, and attaches `latch-v1.2.3.apk` to the release. Re-run it for an existing tag from the Actions tab.

One-time setup of the release key (stored outside the repo and uploaded as the `LATCH_KEYSTORE_BASE64`, `LATCH_KEYSTORE_PASSWORD`, `LATCH_KEY_ALIAS`, `LATCH_KEY_PASSWORD` repository secrets):

```sh
just release-keystore # defaults to ~/.android/latch-release.jks
```

Back the keystore up: Android (and Obtainium) only install updates signed with the same key, so losing it means every user has to uninstall and reinstall.

## Tests

```sh
./gradlew testDebugUnitTest         # JVM: AlphaSections, CategoryOps, JSON codec
./gradlew connectedDebugAndroidTest # device/emulator: CategoryStore persistence, Compose UI
```

Note: installing the test APK can reset the default home app; run `set-home-activity` again afterwards.

## Screenshots

`just screenshots` regenerates the README screenshots in [assets/screenshots](../assets/screenshots) on an emulator: it boots the AVD if no device is connected, installs the placeholder apps from [screenshots/fakeapps](../screenshots/fakeapps), cleans the status bar with demo mode, runs `ScreenshotTest` (which fills a Productivity and a Social drawer) and pulls the PNGs.

## Layout

```
app/src/main/java/app/latch/
 ├─ domain/   pure Kotlin (no android.*): AppKey, Category, AlphaSections, CategoryOps
 ├─ data/     AppRepository (LauncherApps), CategoryStore (DataStore JSON), IconLoader, AppLauncher
 └─ ui/       LatchViewModel, LatchScreen (root, drawer sheet), HomeScreen, AppDrawer, AppTile, DragState
```
