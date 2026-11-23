# VRFlex

Standalone Android/Quest project stored inside the AutoTouch repository.

## Build
From this directory, use Gradle 8.9 and Android SDK Platform 35:

```sh
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Current status
The launcher has separate Games (left Menu / ☰) and Mod Menu (left Y) screens. Selecting Launch on an installed app saves that app's profile before starting it; profile settings are available from both screens. Settings persist locally. The button labels describe the intended shortcuts, but this APK does not have a Quest-wide controller listener, so the shortcuts are not yet intercepted inside arbitrary games. Movement and arm controls remain profile values until a compatible game integration applies them. No Guardian, system firmware, or OS modifications are performed.

The project is intentionally separate from the existing AutoTouch and QuestLink projects.
