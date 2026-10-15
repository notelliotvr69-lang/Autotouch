# ULTRAKILL Arsenal Expansion

A BepInEx 5 x64 plugin project for making the existing Developer Museum weapons available during regular levels.

## Intended behavior

- Automatically adds the existing **Beamcutter** and **Black Hole Cannon** prefabs to ULTRAKILL's extra weapon slot when a gameplay scene loads.
- Does **not** register a cheat in the Cheats menu or require selecting an in-game cheat.
- Uses the existing game prefabs and weapon behavior rather than recreating their projectiles from scratch.

## Build

The GitHub Actions workflow can be started manually from the Actions tab. It compiles against the game's Managed assemblies and BepInEx core DLLs supplied to the runner. Configure the repository variable ULTRAKILL_REFERENCE_BUNDLE_URL to a ZIP containing:
- ULTRAKILL_Data/Managed/Assembly-CSharp.dll
- the game's Unity Managed DLLs
- BepInEx/core/BepInEx.dll
- BepInEx/core/0Harmony.dll

Do not commit or publicly redistribute the game's assemblies.

## Status and testing

The plugin now contains an initial automatic weapon-slot implementation based on the public Museum Weapons Cheat source's prefab paths and slot registration approach. It has **not yet been compiled or tested in-game**. Game updates can change APIs or prefab paths, and the extra slot's input/display behavior needs in-game verification. Do not treat it as a confirmed working build until GitHub Actions succeeds and the plugin is tested.

The current approach does not register or invoke CheatsManager at all. This does not guarantee anything about achievement/leaderboard eligibility, which can depend on ULTRAKILL and other installed mods.
