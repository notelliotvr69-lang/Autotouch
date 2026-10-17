# TWIN REQUIEM — unified mechanics runtime 0.4.0

A BepInEx 5 x64 project for the planned dual-Slab-Revolver ULTRAKILL weapon.

## Design
- Target weapon slot: **5**.
- Weapon concept: two Slab Revolver models with alternating left/right shots.
- Palette: subdued dark crimson and dark purple.
- Variants: Requiem (balanced), Dirge (slower/heavier), Crescendo (faster/tighter rhythm), and secret Encore.
- Encore challenge: finish a level using Twin Requiem for all attacks. Switching weapons is allowed; attacking with another weapon or punching invalidates the attempt. A successful unlock should be permanent.

## Implemented foundation
- BepInEx plugin identity and config entries for selecting a palette name.
- An opt-in experimental preview that tints the stock Revolver's renderer materials. It is disabled by default under `Prototype > Tint Stock Revolver`.
- `WeaponMechanics.cs` now owns a single `RequiemRuntime` that combines variant tuning, left/right shot cadence, and Encore challenge tracking.
- The plugin initializes that runtime from the selected variant config. Automated tests exercise the combined runtime as well as the individual rules.
- Variant tuning currently defines prototype values:
  - Requiem: 0.32 s shot interval, 1.00x damage, 1.00x spread.
  - Dirge: 0.48 s, 1.45x damage, 0.80x spread.
  - Crescendo: 0.19 s, 0.78x damage, 0.72x spread.
  - Encore: 0.24 s, 1.10x damage, 0.55x spread.

## Still not implemented
This is **not yet a playable custom weapon**. The combined runtime is initialized by the plugin, but it is not wired into ULTRAKILL's weapon selection, firing input, projectile spawning, damage pipeline, dual model rendering, variant switching UI, or level-completion/unlock save system. Slot 5 is the target, not a registered slot yet. The stock-Revolver tint is only a temporary visual experiment and can affect the existing Revolver model if enabled. Do not treat a successful compile as proof of in-game compatibility.

## Build validation
GitHub Actions compiles against reference assemblies as a code check. While this remains a prototype, the workflow does not publish a downloadable DLL. A public DLL release will wait until the custom weapon has meaningful integrated behavior.
