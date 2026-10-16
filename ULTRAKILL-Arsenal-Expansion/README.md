# ULTRAKILL Arsenal Expansion

BepInEx 5 x64 plugin project. The current experimental build adds the existing Museum weapons and a custom **GODSPLITTER** weapon prototype to the extra weapon slot.

## GODSPLITTER concept

A weapon inspired by Gabriel's gold, white, silver, and blue divine aesthetic. It is available through the mod's extra weapon slot and does not require defeating Gabriel.

- **Primary fire — Divine Edge:** fast sword slash with a short recovery.
- **Secondary fire — SMITE:** right-click calls a golden light strike onto the point under the crosshair, with a cooldown.
- **Kill afterimage:** when the game exposes enemy health in a readable way and the damage call succeeds, a sword kill attempts one golden follow-up slash against a nearby enemy.
- **Story idea:** an alternate mod encounter has Gabriel lose his sword arm; he later returns with a replacement. This is a mod concept, not original-game canon.

## Build

The GitHub Actions workflow can be started manually from the Actions tab. It compiles against game Managed assemblies and BepInEx core DLLs supplied in `UKReferences.zip` at the repository root. The archive must contain:
- `Assembly-CSharp.dll`
- `UnityEngine.dll`
- `UnityEngine.CoreModule.dll`
- `BepInEx.dll`
- `0Harmony.dll`

Do not commit or publicly redistribute the game's assemblies.

## Important prototype status

**This is an experimental first pass, not a verified release.** The sword model and beam are generated from simple Unity primitives at runtime. Enemy damage is attempted through reflection because ULTRAKILL's internal damage API is version-sensitive. The custom object is registered into the existing extra weapon list, but the slot's display, selection/holstering behavior, damage signatures, and visuals need an actual compile and in-game test. Expect bugs; keep a backup of your current plugin DLL.

The build workflow packages its DLL as a downloadable Actions artifact. A successful compile does not prove the weapon works correctly in-game.

The plugin does not register or invoke CheatsManager. That does not guarantee anything about achievement or leaderboard eligibility; this can depend on ULTRAKILL and other installed mods.
