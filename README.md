# Latch

A minimal, text-first Android launcher: a centered list of user-defined **categories** that expand into their apps, plus a swipe-up **A–Z app drawer**. Kotlin + Jetpack Compose, single activity. See [PLAN.md](PLAN.md) for the spec and architecture.

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

Release build (R8 + resource shrinking; signed with the debug key for convenience):

```sh
./gradlew assembleRelease
```

## Tests

```sh
./gradlew testDebugUnitTest         # JVM: AlphaSections, CategoryOps, JSON codec
./gradlew connectedDebugAndroidTest # device/emulator: CategoryStore persistence, Compose UI
```

Note: installing the test APK can reset the default home app; run `set-home-activity` again afterwards.

## Using it

- **Tap** a category name to expand/collapse it; **tap** an app to launch it.
- **Swipe up** for the drawer; swipe down at the top, Back, or Home closes it.
- **Long-press** an app for its menu; **long-press and move** to drag it onto a category name (from the drawer = add, from another category = move).
- **Long-press** a category name for Rename / Delete; **long-press and move** it vertically to reorder.
- **Home** while on Home collapses everything and closes the drawer.

## Layout

```
app/src/main/java/app/latch/
 ├─ domain/   pure Kotlin (no android.*): AppKey, Category, AlphaSections, CategoryOps
 ├─ data/     AppRepository (LauncherApps), CategoryStore (DataStore JSON), IconLoader, AppLauncher
 └─ ui/       LatchViewModel, LatchScreen (root, drawer sheet), HomeScreen, AppDrawer, AppTile, DragState
```

## License

[MIT](LICENSE)
