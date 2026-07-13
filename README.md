# DumbLauncher

Minimal monochrome Android home-screen launcher for e-ink devices.

## Requirements

- Runs on Android 8.0+ (minSdk 26)
- **Target: Android 14 (API 34)**
- Set as the default Home app after install

## Features

- Home: clock, date, battery %, screen time, N favorite apps (text only)
- Swipe down: alphabetical list of all apps
- Type-ahead search on the all-apps screen (no visible search field — just type)
- Long-press home: settings (favorite count 3–12, pick/reorder favorites, usage access)
- No icons, no animations, black text on white background

## Build

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then: Settings → Apps → Default apps → Home app → DumbLauncher.

Grant **Usage access** in launcher settings to show screen time.
