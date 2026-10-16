# ULTRAKILL Arsenal Expansion 0.4.0

A BepInEx 5 x64 plugin intended to make ULTRAKILL gear and weapon variants available without completing the campaign acts.

## Intended behavior

- Patches `GameProgressSaver.CheckGear` so the game treats gear as unlocked while this plugin is loaded.
- Does not write unlocks into the player's save file.
- Does not add museum weapons or depend on Gabriel model/texture files.

## Build and install

The GitHub Actions workflow builds the plugin against game and BepInEx reference DLLs supplied in one of the repository's reference ZIP archives. On a successful build, download the `ArsenalExpansion-v0.4.0-build` artifact, extract it, and place `ArsenalExpansion.dll` in the game's `BepInEx/plugins` folder.

## Testing status

This implementation is based on the game's `GameProgressSaver.CheckGear` gear-check API used by other ULTRAKILL mods. A successful compile still does not prove that every shop UI and variant-equipment path honors this check; in-game verification is required. The plugin does not permanently unlock or alter campaign progression in the save file.
