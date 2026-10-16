# ULTRAKILL Arsenal Expansion

BepInEx 5 x64 plugin project for ULTRAKILL.

## Equipment names and order

- **GODSPLITTER** is Gabriel's actual arm, equipped in V1's arm slot like the arm taken from V2. It is not a Gabriel-inspired gauntlet or the sword.
- **DIVINE JUDGEMENT** is Gabriel's divine-light sword, a separate selectable weapon-slot item planned after GODSPLITTER.
- Later, while GODSPLITTER is equipped, its secondary action will throw DIVINE JUDGEMENT. The sword should damage on the outbound and return passes and return after about 1–2 seconds. Only GODSPLITTER may override gun secondary fire; all other arm/gun behavior must remain unchanged.
- The arm should use ULTRAKILL's native punch/combo behavior.
- No Museum weapons. GODSPLITTER must not require killing Gabriel to obtain.

## Current prototype status

The branch has an experimental arm-cycle hook, but **Gabriel's actual arm model/rig is not attached yet**. The substitute primitive gauntlet has been removed rather than misrepresenting it as the real arm. The next implementation step is locating/loading the game's actual Gabriel model asset and attaching the correct animated arm to V1's arm holder. A successful compile alone does not prove in-game behavior.

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
