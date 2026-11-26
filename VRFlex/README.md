# VRFlex for Meta Quest

VRFlex is being migrated from a flat Android activity to an immersive Meta Spatial SDK application.

## Current spatial milestone

- Native Meta Spatial SDK `AppSystemActivity` and VR feature registration.
- Procedural 3D scene: dark floor, orange architectural accents, and a floating spatial panel.
- Compose-based black/orange launcher panel with Games, Mod Menu, and Arms sections.
- Initial shared prediction and arm-length controls are UI prototypes only; they do not yet alter controller tracking in other apps.
- The in-game Y-button overlay and cross-game tracking integration are later milestones, not part of this first spatial shell.

## Build

The Quest APK is built by GitHub Actions using Meta Spatial SDK 0.15.0, Android Gradle Plugin 8.11.1, Kotlin 2.1.0, and Gradle 9.4.1.
