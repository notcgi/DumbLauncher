# DumbLauncher

Monochrome, text-only Android home-screen launcher for e-ink devices.

**Repository:** [github.com/notcgi/DumbLauncher](https://github.com/notcgi/DumbLauncher) (private)

## About

DumbLauncher replaces the stock home screen with a deliberately minimal UI: black text on white, no app icons, no animations, and a hidden status bar. It is aimed at Android 14 (API 34) e-ink readers and other devices where visual noise and refresh cost matter.

## Features

- **Home screen** — clock, date, battery percentage, today’s screen time, and up to 10 favorite apps (labels only)
- **All apps** — swipe up or down from home to open an alphabetical list
- **Hidden type-ahead search** — start typing on the all-apps screen (no visible search field); RU/EN matching via QWERTY↔ЙЦУКЕН layout swap and phonetic transliteration
- **Auto-launch** — when search narrows to a single app, it launches immediately
- **Long-press on an app** — hide from the list, rename, or uninstall
- **Settings** (long-press on home) — manage favorites (add/remove, reorder), unhide apps, reset custom names, optionally hide DumbLauncher itself, open Usage Access
- **Top-left swipe** — expand the notification shade
- **Header shortcuts** — tap clock → Clock app; tap date → Calendar; tap screen time → Digital Wellbeing / screen-time UI
- **System Home** — pressing Home returns to the launcher home screen
- **Screen time** — approximates Digital Wellbeing via Usage Access (`PACKAGE_USAGE_STATS`)
- **E-ink friendly** — no icons, no animations, status bar hidden

## Screenshots

Screenshots are not included in this repository yet. Add images under a `docs/` or `screenshots/` folder and link them here when available.

## Requirements

| Item | Value |
|------|--------|
| minSdk | 26 (Android 8.0) |
| targetSdk | 34 (Android 14) |
| compileSdk | 35 |
| JDK | 17 |
| Android SDK | `ANDROID_HOME` set to a Command-line Tools / SDK install |

## Build

```bash
export ANDROID_HOME=/path/to/android/sdk   # e.g. /opt/homebrew/share/android-commandlinetools
./gradlew assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install via adb

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Set as default launcher

1. Open **Settings → Apps → Default apps → Home app** (path may vary by OEM).
2. Choose **DumbLauncher**.

Pressing the system Home key should return you to this launcher’s home screen.

## Permissions

| Permission | Why |
|------------|-----|
| **Usage Access** (`PACKAGE_USAGE_STATS`) | Required to show today’s screen-time total on the home header and to open the system screen-time UI. Grant via the in-app prompt, Settings in the launcher, or **Settings → Apps → Special app access → Usage access → DumbLauncher**. Without it, screen time is hidden and a hint is shown instead. |
| `EXPAND_STATUS_BAR` / status bar access | Used so a swipe from the top-left can open the notification shade. |

## License

No `LICENSE` file is present in this repository. Treat copyright and redistribution terms as **unspecified** until a license is added.
