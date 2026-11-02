# Latch — minimal Android launcher

A text-first home screen: a list of user-defined **categories**, each expanding into its apps, plus a swipe-up **app drawer** listing every app A–Z.

Stack: **Kotlin + Jetpack Compose**, single-activity, no Rust. The `domain/` package stays free of Android imports so it could be moved to a Rust core (UniFFI) later without touching the UI.

---

## 1. Glossary

| Term | Meaning in Latch |
|---|---|
| **Home** | The main screen: vertical list of category names. |
| **Category** | User-named group of apps (e.g. "Talk", "Listen"). Ordered. |
| **Expand / collapse** | Tapping a category name reveals / hides its apps (accordion: one open at a time). |
| **App drawer** | Full-screen list of all launchable apps, opened by a **swipe-up gesture** from Home. |
| **Section header** | Sticky letter header in the drawer (`#`, `A`, `B`…). |
| **Fast-scroller** | Thumb on the right edge of the drawer to jump through letters. |
| **Context menu** | Popup shown on long-press. |
| **Drag and drop** | Long-press an app, move the finger, drop it on a category name to add it. |
| **AppKey** | Unique app identity: `ComponentName` + `UserHandle` (needed for work profiles and apps with multiple launcher activities). |

---

## 2. Functional spec

### Home
- Vertical, centered list of category names over the system wallpaper.
- Tap a name → it expands to show its apps (icon + label grid/row). Tap again → it collapses. Opening another category collapses the current one.
- Tap an app → launches it.
- Long-press an app inside a category → context menu:
  - **Remove from category**
  - **App info**
- Long-press a category name → context menu: **Rename**, **Delete**, (later: reorder).
- An **Add category** entry at the bottom of the list (or in a long-press on empty space).
- Pressing Home while on Home → collapse everything and close the drawer.

### App drawer
- Opened by swiping up on Home. Closed by swiping down while scrolled to the top, or by Back/Home.
- All launchable apps, sorted A–Z by label, locale-aware (accents sort with the base letter: "AlloCiné" sorts under A). Labels starting with a digit or symbol go under `#`.
- Sticky letter headers, plus a fast-scroller.
- Tap → launch.
- Long-press → context menu with **App info** only.
- **Long-press + move** (beyond touch slop) → the menu closes, a drag starts, the drawer slides away to reveal Home, and dropping on a category name adds the app to that category. Dropping elsewhere cancels.

### Data rules
- An app can belong to several categories.
- An uninstalled app is removed from all categories automatically.
- A newly installed app appears in the drawer immediately (no category by default).
- Categories and their contents persist across reboots and launcher restarts.

### Out of scope for v1
Search bar, usage time per app, icon packs, widgets, settings screen, backup/restore.

---

## 3. Architecture

```
app/src/main/java/app/latch/
 ├─ MainActivity.kt          single activity, HOME intent, hosts Compose
 ├─ LatchApp.kt              Application: creates the singletons (manual DI, no Hilt)
 ├─ domain/                  pure Kotlin, no android.* imports
 │   ├─ AppKey.kt            data class (packageName, className, userSerial)
 │   ├─ AppEntry.kt          key + label
 │   ├─ Category.kt          id, name, order, apps: List<AppKey>
 │   ├─ AlphaSections.kt     sort + group into sections (takes a Comparator)
 │   └─ CategoryOps.kt       add/remove/rename/reorder/prune uninstalled — pure functions
 ├─ data/
 │   ├─ AppRepository.kt     LauncherApps → StateFlow<List<AppEntry>>, updated by LauncherApps.Callback
 │   ├─ CategoryStore.kt     DataStore<Preferences> holding a JSON string (kotlinx.serialization)
 │   ├─ IconLoader.kt        LruCache<AppKey, ImageBitmap>, loads on Dispatchers.IO
 │   └─ AppLauncher.kt       launch(key), openAppInfo(key)
 └─ ui/
     ├─ LatchViewModel.kt    combines apps + categories → UiState; exposes intents
     ├─ HomeScreen.kt        category list, expand/collapse, drop targets
     ├─ AppDrawer.kt         swipe-up sheet, LazyColumn + stickyHeader, fast-scroller
     ├─ AppTile.kt           icon + label, tap / long-press / drag source
     ├─ ContextMenu.kt       DropdownMenu wrappers
     ├─ DragState.kt         dragged AppKey, pointer position, hovered category
     └─ theme/               dark, transparent background, text colors
```

### State flow
```
LauncherApps ──► AppRepository ──┐
                                 ├─► LatchViewModel (combine) ──► UiState ──► Compose
DataStore ─────► CategoryStore ──┘            ▲
                                              └── user actions (expand, drop, remove…)
```

`UiState`:
```kotlin
data class UiState(
    val categories: List<CategoryUi>,      // with resolved AppEntry list, uninstalled filtered out
    val drawerSections: List<Section>,     // letter + apps
    val expandedCategoryId: String?,
    val drawerOpen: Boolean,
)
```

### Key platform APIs
| Need | API |
|---|---|
| List apps (incl. work profile) | `LauncherApps.getActivityList(null, user)` for each user in `UserManager.userProfiles` |
| Install/uninstall/update events | `LauncherApps.registerCallback(Callback)` |
| Label / icon | `LauncherActivityInfo.label`, `getBadgedIcon(density)` → `toBitmap()` |
| Launch | `LauncherApps.startMainActivity(component, user, sourceBounds, opts)` |
| App info | `LauncherApps.startAppDetailsActivity(component, user, null, null)` |
| Locale sort | `java.text.Collator.getInstance(Locale.getDefault())`, strength `PRIMARY` |
| Detect Home press while already on Home | `onNewIntent` with `Intent.CATEGORY_HOME` |

### Manifest essentials
```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>

<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:clearTaskOnLaunch="true"
    android:stateNotNeeded="true"
    android:excludeFromRecents="true"
    android:screenOrientation="portrait"
    android:windowSoftInputMode="adjustNothing"
    android:theme="@style/Theme.Latch">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```
Theme: `android:windowShowWallpaper=true`, `android:windowBackground=@android:color/transparent`, `android:windowIsTranslucent=false`, edge-to-edge via `enableEdgeToEdge()`.

No `QUERY_ALL_PACKAGES` needed (Play-policy friendly).

### Persistence format
DataStore key `categories_json`:
```json
{
  "version": 1,
  "categories": [
    { "id": "uuid", "name": "Talk", "order": 0,
      "apps": [ { "pkg": "org.thoughtcrime.securesms", "cls": "…RoutingActivity", "user": 0 } ] }
  ]
}
```
`user` = `UserManager.getSerialNumberForUser(handle)` (stable across reboots, unlike `UserHandle` ids).
On first launch, seed a few empty categories (e.g. "Talk", "Listen", "See", "Tech") so Home isn't blank.

---

## 4. Gestures & drag and drop — design notes

The tricky part. All in Compose, no View-based `DragEvent`.

1. **Swipe up to open the drawer**: `AnchoredDraggable` (anchors: `Closed`, `Open`) on a full-screen layer. The drawer's `LazyColumn` uses nested scroll so that dragging down while at index 0 closes the sheet instead of overscrolling.
2. **Long-press vs. drag on an app tile**: one `pointerInput` with `awaitEachGesture`:
   - down → wait for long-press timeout (`viewConfiguration.longPressTimeoutMillis`) → haptic + show context menu.
   - if the pointer then moves more than `touchSlop` → dismiss the menu, set `DragState.dragging = AppKey`, close the drawer (animate to `Closed`).
   - track the pointer in root coordinates and update `DragState.position`.
   - on up → if `DragState.hoveredCategory != null` call `vm.addToCategory(...)`; reset.
3. **Drop targets**: each category name records its bounds with `onGloballyPositioned { boundsInRoot() }` into a map in `DragState`; hit-testing = find the rect containing the pointer. The hovered category is highlighted (scale/alpha).
4. **Drag shadow**: a top-level `Box` overlay draws the dragged icon at `DragState.position` with `graphicsLayer` offset.
5. Dragging an app out of an expanded category onto another category = **move** (remove + add); onto empty space = no-op (removal goes through the context menu, as specified).

---

## 5. Milestones

Each milestone ends with something installable and testable on a real device.

### M0 — Project skeleton
- [ ] Gradle project (`app.latch`), Kotlin, Compose BOM, minSdk 26, target/compileSdk latest.
- [ ] Dependencies: compose (ui, foundation, material3), activity-compose, lifecycle-viewmodel-compose, datastore-preferences, kotlinx-serialization-json, kotlinx-coroutines.
- [ ] Manifest as above, transparent wallpaper theme, edge-to-edge.
- [ ] `MainActivity` showing "Latch" over the wallpaper.
- **Done when**: Latch can be picked as default home app and the wallpaper shows through.

### M1 — App drawer
- [ ] `AppRepository` with callback-driven `StateFlow`.
- [ ] `AlphaSections` (pure, unit-tested): Collator sort, `#` bucket, accent folding for headers (`É` → `E`).
- [ ] `AppDrawer`: `LazyColumn` + `stickyHeader`, rows of tiles (icon + label, max 2 lines, ellipsized).
- [ ] `IconLoader` with LRU cache; placeholder while loading.
- [ ] Swipe-up open / swipe-down close / Back closes.
- [ ] Tap → launch; long-press → "App info".
- **Done when**: installing/uninstalling an app updates the drawer live; scrolling is smooth (no jank with ~200 apps).

### M2 — Categories on Home
- [ ] `Category`, `CategoryOps` (pure, unit-tested), `CategoryStore` (DataStore + JSON).
- [ ] `HomeScreen`: centered category names, bold large type; accordion expand/collapse with `AnimatedVisibility`.
- [ ] Expanded category shows its apps as a horizontal row (scrollable if overflowing) like the reference.
- [ ] Add / rename / delete category (dialog with `TextField`).
- [ ] Prune uninstalled apps when combining state.
- [ ] Home button while on Home → collapse all, close drawer.
- **Done when**: categories survive force-stop and reboot.

### M3 — Drag and drop
- [ ] `DragState` + overlay drag shadow.
- [ ] Gesture handling in `AppTile` (long-press → menu, move → drag).
- [ ] Drawer auto-closes when a drag starts; category drop targets highlight on hover.
- [ ] Drop → add to category (no duplicates); drag from one category to another → move.
- [ ] Long-press in a category → "Remove from category" / "App info".
- **Done when**: an app can be dragged from the drawer into any category and removed again, with haptic feedback.

### M4 — Polish
- [ ] Fast-scroller with letter bubble.
- [ ] Animations: drawer fade/slide, category expand spring, drop "bump".
- [ ] Work profile apps (badged icons) in drawer and categories.
- [ ] Reorder categories (long-press name → drag vertically).
- [ ] Dark text-shadow / scrim so labels stay readable on bright wallpapers.
- [ ] Baseline profile + R8 for fast startup.
- [ ] Rotation/config-change and process-death safety (`rememberSaveable` for expanded/drawer state).

### Later (post v1)
Search bar ("Tap to search" pill at the bottom), usage time per app/category via `UsageStatsManager` (needs `PACKAGE_USAGE_STATS` granted manually), hide apps, settings, export/import categories.

---

## 6. Testing

- **Unit (JVM)**: `AlphaSections` (accents, digits, emoji, mixed case, locales), `CategoryOps` (add dup, remove, rename, prune), JSON round-trip + version migration.
- **Instrumented**: `CategoryStore` persistence; Compose UI tests for expand/collapse, drawer open/close, context menu entries.
- **Manual checklist**: set as default home, Home button behavior, install/uninstall while open, work profile, gesture nav vs. 3-button nav, light/dark wallpapers, 200+ apps scroll performance.

---

## 7. Tooling

- Android Studio or CLI: `./gradlew installDebug`, then `adb shell cmd package set-home-activity app.latch/.MainActivity` to switch home without the settings UI.
- Relevant local skills in `skills/`: `android-cli`, `edge-to-edge`, `jetpack-compose-performance`, `testing-setup`, `r8-analyzer`.
