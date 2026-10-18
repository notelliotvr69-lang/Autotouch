# Auto Parry

A small BepInEx 5 prototype for ULTRAKILL.

## Prototype behavior
- Looks for nearby Projectile objects.
- Attempts to call the game's Punch.ParryProjectile method on them.
- Configurable enable switch, scan interval, and range in the BepInEx config.

## Important limitation
This is an experimental first pass. ULTRAKILL's parry method may rely on state established during a normal punch, and not every projectile is parryable. A successful compile does not prove the behavior works in-game. Check the BepInEx log and test carefully.

## Install
After a successful GitHub Actions build, put AutoParry.dll in ULTRAKILL/BepInEx/plugins/. Do not install the source files. Requires a working BepInEx 5 installation.