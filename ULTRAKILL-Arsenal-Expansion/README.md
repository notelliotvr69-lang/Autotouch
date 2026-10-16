# ULTRAKILL Arsenal Expansion

A BepInEx 5 x64 plugin project. The rejected Momentum Overdrive mechanic has been removed.

## Reclaimer mechanics — prototype

The first Reclaimer gameplay prototype is now implemented as Harmony hooks on the existing punch/parry methods, temporarily using the current arm while custom arm registration remains a separate task.

- On a successful `Punch.ParryProjectile`, store one parried projectile.
- Keep the stored projectile inactive while it is held.
- On the next `Punch.PunchStart`, reposition and reactivate the stored projectile in front of the player, aiming toward the current camera/target.
- Additional parries do not overwrite a projectile already being held.
- After release, the slot is empty and can catch another projectile.

This is a mechanics prototype, not a finished selectable arm. It reuses the parried projectile object; different projectile types may behave differently when reactivated, and in-game testing is needed to verify damage, movement, and lifetime. It is temporarily hooked to the existing arm's parry/punch methods and is not yet restricted to a custom Reclaimer arm.

## Arm/model work — not implemented yet

The target remains a third selectable arm with yellow/gold plating, based on the reference image created for this project. Custom arm registration and the 3D model/animation still need to be integrated.

## Build and install

The GitHub Actions workflow builds against game and BepInEx reference DLLs supplied in one of the repository's reference ZIP archives. A successful plugin build alone does not prove the projectile loop behaves correctly in-game.

## Testing status

The existing gear-check patch is separate from the Reclaimer. It overrides `GameProgressSaver.CheckGear`; it does not write unlocks into the save file and may not make every weapon variant equippable. In-game verification is required.
