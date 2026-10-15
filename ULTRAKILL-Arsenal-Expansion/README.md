# ULTRAKILL Arsenal Expansion

A BepInEx 5 x64 plugin project for experimenting with additional ULTRAKILL weapons. This lives in a separate folder so it does not interfere with the AutoTouch Android project.

## Build

The GitHub Actions workflow in `.github/workflows/ultrakill-build.yml` runs on pushes that touch this project and can be started manually from the Actions tab. It compiles the plugin against the BepInEx and Unity/game assemblies supplied to the runner.

**Current status:** project/build pipeline scaffold. It is not yet a playable weapon pack. The custom weapons still need implementation and testing against the game's actual weapon/projectile APIs. Do not install a build as a finished mod until the workflow succeeds and the weapon implementation is complete.

## Planned concepts

- Dual Revolvers
- Beamcutter
- Black Hole Cannon
- additional regular-play weapon variants

The goal is to add weapons for normal gameplay, not enable cheats.
