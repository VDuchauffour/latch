# Latch

[![Release](https://img.shields.io/github/v/release/VDuchauffour/latch?style=flat-square)](https://github.com/VDuchauffour/latch/releases/latest)
[![CI](https://img.shields.io/github/actions/workflow/status/VDuchauffour/latch/ci.yml?branch=main&style=flat-square&label=CI)](https://github.com/VDuchauffour/latch/actions/workflows/ci.yml)
[![Release APK](https://img.shields.io/github/actions/workflow/status/VDuchauffour/latch/release.yml?style=flat-square&label=release%20APK)](https://github.com/VDuchauffour/latch/actions/workflows/release.yml)
[![Downloads](https://img.shields.io/github/downloads/VDuchauffour/latch/total?style=flat-square)](https://github.com/VDuchauffour/latch/releases)
[![License](https://img.shields.io/github/license/VDuchauffour/latch?style=flat-square)](LICENSE)
[![Get it on Obtainium](https://img.shields.io/badge/Get%20it%20on-Obtainium-4A148C?style=flat-square&logo=obtainium&logoColor=white)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22app.latch%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FVDuchauffour%2Flatch%22%2C%22author%22%3A%22VDuchauffour%22%2C%22name%22%3A%22Latch%22%2C%22supportFixedAPKURL%22%3Afalse%7D)

A minimal, text-first Android launcher: a centered list of user-defined **categories** that expand into their apps, plus a swipe-up **A–Z app drawer**. Kotlin + Jetpack Compose, single activity. See [PLAN.md](PLAN.md) for the spec and architecture.

## Screenshots

|                                                                              |                                                                                                     |                                                                                    |                                                                                                  |
| :--------------------------------------------------------------------------: | :-------------------------------------------------------------------------------------------------: | :--------------------------------------------------------------------------------: | :----------------------------------------------------------------------------------------------: |
|    <img src="assets/screenshots/home.png" alt="Home" width="200"><br>Home    | <img src="assets/screenshots/drawer-productivity.png" alt="Open drawer" width="200"><br>Open drawer | <img src="assets/screenshots/app-menu.png" alt="App menu" width="200"><br>App menu | <img src="assets/screenshots/app-drawer.png" alt="A–Z app drawer" width="200"><br>A–Z app drawer |
| <img src="assets/screenshots/search.png" alt="Search" width="200"><br>Search |      <img src="assets/screenshots/quick-menu.png" alt="Quick menu" width="200"><br>Quick menu       | <img src="assets/screenshots/settings.png" alt="Settings" width="200"><br>Settings |                                                                                                  |

## Install

Download `latch-vX.Y.Z.apk` from the [latest release](https://github.com/VDuchauffour/latch/releases/latest), or add Latch to [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22app.latch%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FVDuchauffour%2Flatch%22%2C%22author%22%3A%22VDuchauffour%22%2C%22name%22%3A%22Latch%22%2C%22supportFixedAPKURL%22%3Afalse%7D) to get updates automatically (or add `https://github.com/VDuchauffour/latch` as an app in Obtainium by hand).

## Using it

See [docs/features.md](docs/features.md) for everything Latch does.

- **Tap** a category name to expand/collapse it; **tap** an app to launch it.
- **Swipe up** for the drawer; swipe down at the top, Back, or Home closes it.
- **Long-press** an app for its menu; **long-press and move** to drag it onto a category name (from the drawer = add, from another category = move).
- **Long-press** a category name for Rename / Delete; **long-press and move** it vertically to reorder.
- **Home** while on Home collapses everything and closes the drawer.

## Development

Building, testing, releasing and regenerating screenshots: see [docs/development.md](docs/development.md).

## License

[MIT](LICENSE)
