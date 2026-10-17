# TWIN REQUIEM — prototype 0.1.0

A BepInEx 5 x64 plugin foundation for the planned dual-Slab-Revolver ULTRAKILL weapon.

## Planned weapon
- Separate weapon slot (target slot 6).
- Two existing Slab Revolver models, alternating left/right shots.
- Dark crimson and dark purple finishes, with the red kept subdued.
- Variants: Requiem (balanced), Dirge (slower/heavier), Crescendo (faster/tighter cadence), and Encore (secret challenge variant).
- Encore challenge: finish a level using Twin Requiem for all attacks. Switching weapons is allowed; attacking with another weapon or punching invalidates the challenge.

## Current implementation status
This first commit is only a mechanics foundation. It adds the BepInEx plugin identity, variant configuration, and a reflection-based observer for the stock Revolver's Shoot method to track alternating-shot cadence. It does **not** yet add a selectable weapon slot, dual weapon models, new projectiles, damage changes, variant firing behavior, or the Encore unlock/finisher. Those require confirmed game method and weapon-registration hooks and in-game validation.

The prototype hook can be disabled in the BepInEx config under Prototype > Hook Existing Revolver.

## Build
The GitHub Actions workflow extracts the required game/BepInEx references from the repository's existing reference archives and compiles TwinRequiem.dll.
