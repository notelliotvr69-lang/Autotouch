# ULTRAKILL Arsenal Expansion

A BepInEx 5 x64 plugin project. The rejected Momentum Overdrive mechanic has been removed.

## Reclaimer arm — in progress

Target: a third selectable arm with yellow/gold plating, based on the reference image created for this project.

Mechanics:
- Catch and store one enemy projectile during a successful parry.
- Launch the stored projectile on the next punch.
- Punch normally when no projectile is stored.
- Clear the stored projectile after release; allow the next catch.

## Implementation research

The public MIT-licensed [ULTRAKILL Custom Arms proof of concept](https://github.com/Temperz87/Ultrakill-Custom-Arms-Proof-Of-Concept) demonstrates the relevant integration points: a custom-arm registry through `FistControl.ArmChange`, a parry callback from `Punch.ParryProjectile`, and a punch callback from `Punch.PunchStart`. Its vortex-arm example also demonstrates storing projectile GameObjects and releasing them on a later swing.

That proof of concept is built around the older UMM framework and cannot simply be dropped into this standalone BepInEx project. The Reclaimer needs a compatible arm-registration path before the capture/release mechanic can be honestly described as implemented. The existing project currently does not register a third arm or implement Reclaimer capture/release.

## Build and install

The GitHub Actions workflow builds against game and BepInEx reference DLLs supplied in one of the repository's reference ZIP archives. A successful plugin build alone does not prove arm selection, visuals, projectile capture, or projectile release.

## Testing status

The existing gear-check patch is separate from the Reclaimer. It overrides `GameProgressSaver.CheckGear`; it does not write unlocks into the save file and may not make every weapon variant equippable. In-game verification is required.
