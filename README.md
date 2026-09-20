# AutoTouch

AutoTouch is a native Android automation controller built around feature toggles instead of a raw macro-recording screen.

## Current UI

The main screen now exposes high-level toggles for:

- Auto Level
- Auto Quest
- Auto Money / Chest Farm
- Auto Fruit Farm
- Auto Buy Fruit

It also includes a configurable fruit-buy timer, controller loop delay, Android overlay permission, accessibility permission, and a movable floating Start/Stop controller.

The toggle framework stores and restores settings locally. Game-specific action modules are intentionally separated from the UI so they can be implemented and updated independently instead of exposing raw X/Y coordinates to the user.

## Build

Install Android SDK Platform 35, then run:

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The repository intentionally does not commit the binary Gradle wrapper JAR. Install Gradle 8.9 locally, or use the GitHub Actions build, which provisions that Gradle version automatically.
