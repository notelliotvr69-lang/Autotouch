# VRFlex

Standalone Android/Quest project stored inside the AutoTouch repository.

## Build
From this directory, use Gradle 8.9 and Android SDK Platform 35:

```sh
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Current status
Version 0.1.0 is a UI/settings prototype. Settings persist locally. Movement and arm controls are not yet connected to an OpenXR runtime and do not affect other apps. No Guardian, system firmware, or OS modifications are performed.

The project is intentionally separate from the existing AutoTouch and QuestLink projects.
