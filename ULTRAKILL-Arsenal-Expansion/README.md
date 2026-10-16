# ULTRAKILL Arsenal Expansion

BepInEx 5 x64 plugin project for ULTRAKILL.

## Current focus: Gabriel's Arm

The current development step is the arm slot only. The separate GODSPLITTER weapon is intentionally not registered while the arm is being built.

Planned arm design:
- White/silver armor with gold divine detailing.
- A fast, combo-friendly primary punch using the game's actual arm/punch system.
- A later arm-specific secondary action for throwing Gabriel's divine-light sword. That input must only be intercepted while Gabriel's Arm is equipped; all other arms and guns must keep their normal behavior.

## Build

The GitHub Actions workflow compiles against game Managed assemblies and BepInEx core DLLs supplied in the repository's ULTRAKILL reference archive. The archive must contain:
- `Assembly-CSharp.dll`
- `UnityEngine.dll`
- `UnityEngine.CoreModule.dll`
- `UnityEngine.PhysicsModule.dll`
- `UnityEngine.InputLegacyModule.dll`
- `BepInEx.dll`
- `0Harmony.dll`

Do not commit or publicly redistribute the game's assemblies.

## Prototype status

This is an experimental development build, not a verified release. The arm integration and visual overlay must still be tested in-game. A successful compile alone does not prove the arm appears, cycles correctly, or has the intended punch feel.

The separate GODSPLITTER weapon is deferred until the arm slot is working. No Museum weapons are included in this project scope.
