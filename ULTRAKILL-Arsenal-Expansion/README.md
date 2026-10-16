# ULTRAKILL Arsenal Expansion

A BepInEx 5 x64 plugin project. The current code keeps the separate gear-check patch; the rejected Momentum Overdrive mechanic has been removed.

## Reclaimer arm — in progress

The target is a third selectable arm with yellow/gold plating, based on the supplied reference image. Intended behavior:

- Catch and store one enemy projectile when successfully parrying it.
- Launch the stored projectile with the next punch.
- Allow normal punches while no projectile is stored.
- Register as a custom arm rather than merely changing a normal punch effect.

The arm is not yet implemented or verified in-game. A successful plugin build alone will not prove arm selection, visuals, projectile capture, or projectile release.

## Build and install

The GitHub Actions workflow builds against game and BepInEx reference DLLs supplied in one of the repository's reference ZIP archives. A successful workflow produces the `ArsenalExpansion-v0.4.0-build` artifact. Extract it and place `ArsenalExpansion.dll` in the game's `BepInEx/plugins` folder.

## Testing status

The gear-check patch is based on `GameProgressSaver.CheckGear`; it does not write unlocks into the save file and may not make every weapon variant equippable. In-game verification is required.
