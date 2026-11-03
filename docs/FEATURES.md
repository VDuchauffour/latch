# Latch — Features

## Home
- Text-first home screen over the system wallpaper, with transparent status and navigation bars.
- A centered list of user-defined categories; none exist on first launch.
- Tap a category to expand it accordion-style into a horizontal row of app tiles; only one is open at a time.
- An empty category shows a hint to drag apps into it.
- "+ Add category" entry at the bottom of the list.
- Pressing Home while already on home collapses everything and closes the drawer.
- Swipe down on home (with the category list at its top) to pull down the notification shade.
- Back never leaves the launcher: it closes the drawer, ends the search, or collapses the open category.

## Search
- A "Tap to search" bar sits at the bottom of home and moves up with the keyboard.
- As you type, a panel above the bar shows up to 5 matching apps. The best match is on the right, highlighted, and launched by the keyboard's Go key.
- Matching ignores case and accents. Label prefixes rank first, then word prefixes ("you" → "Google TV: For you"), then substrings.
- Launching an app, opening the drawer, pressing Back, or pressing Home clears the search.
- You can long-press a result for App info, or drag it onto a category.

## Quick menu
- Long-press the magnifying glass in the search bar to open a column of round buttons above it over a dimmed home. Tapping the magnifying glass still starts a search.
- Without lifting your finger, slide up and down: the button under it turns yellow and shifts left, with a haptic tick. Release to run it.
- If you release without a highlight, the menu stays open: tap a button to run it, or tap outside or press Back to close it.
- Buttons: Add drawer (opens the new-category dialog) and Android settings (gear icon, opens the system Settings app).

## App drawer
- Swipe up from home to open it, swipe down to close it; it works as a bottom sheet together with list scrolling.
- Lists all launchable apps A–Z with locale-aware sorting (`Collator`, so accents don't affect order).
- Sticky letter headers. Accented initials are folded (`É` → `E`), and labels that don't start with a letter go under `#`.
- A fast-scroller on the right edge with a letter bubble.
- A grid of 5 columns, or 4 on narrow screens.

## App tiles
- Each tile shows the icon, including the work-profile badge, and a label of up to two lines.
- Tap launches the app with a scale-up animation.
- Long-press opens a context menu with haptic feedback:
  - in the drawer: App info;
  - in a category: App info and Remove from category.
- Icons are loaded off the main thread into an in-memory LRU cache and refreshed when an app updates.

## Drag and drop
- Long-press and move a drawer tile to add the app to a category. The drawer closes automatically so the categories are visible.
- Long-press and move a tile from one category to another to move it.
- The category under the pointer is highlighted while hovering, and it plays a short "bump" animation when the app is dropped.
- Duplicates are ignored, and dropping onto the source category does nothing.

## Category management
- Long-press a category name to open Rename and Delete; deleting asks for confirmation.
- Long-press and drag a category name vertically to reorder it.
- New and renamed categories are entered in a dialog with automatic keyboard focus.

## Persistence and app lifecycle
- Categories are saved to DataStore as one versioned JSON document. It survives restarts, and older formats are migrated.
- A corrupt document is never overwritten by updates.
- Uninstalled apps are pruned from categories live and also at start-up.
- Apps that are temporarily unavailable are not pruned: suspended apps, apps on SD cards that aren't mounted, and apps in a work profile that is paused or locked.
- The drawer updates live when apps are installed, updated, enabled or disabled.

## Work profile
- Apps from every user profile are listed and badged.
- Apps launch in their own profile and keep their category membership per profile.
- Reloads when a work profile becomes available, unavailable or unlocked.

## Build and quality
- Kotlin and Jetpack Compose in a single activity, with manual dependency injection and no third-party UI libraries.
- Release build uses R8 and resource shrinking, and ships a baseline profile (hand-written for now) through ProfileInstaller.
- JVM unit tests cover alphabetical sectioning, category operations and the JSON codec. Instrumented tests cover persistence and the main UI flows.

## Not yet verified / follow-ups
- Smoothness with 200 or more apps, 3-button navigation, and persistence across a real reboot.
- A generated (Macrobenchmark) baseline profile to replace the hand-written one.
