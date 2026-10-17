# TWIN REQUIEM — visual foundation 0.2.0

A BepInEx 5 x64 project for the planned dual-Slab-Revolver ULTRAKILL weapon.

## Design
- Target weapon slot: **5**.
- Planned weapon: two Slab Revolver models, alternating left/right shots.
- Palette: subdued dark crimson and dark purple.
- Variants: Requiem (balanced palette), Dirge (darker palette), Crescendo (brighter crimson/purple accents), and Encore (near-black crimson/purple).
- Planned Encore challenge: finish a level using Twin Requiem for all attacks. Switching weapons is allowed; attacking with another weapon or punching invalidates the challenge.

## Implemented in 0.2.0
- BepInEx plugin identity and a configuration entry to select the palette name.
- An **opt-in experimental visual preview** that tints the stock Revolver's renderer materials using the selected palette. It is disabled by default under `Prototype > Tint Stock Revolver`.
- Palette values are deliberately subdued; the crimson is not neon red.

## Not implemented yet
This is **not a playable custom weapon**. It does not register slot 5, spawn two Slab Revolver models, change firing, add projectiles or damage behavior, implement unique variant mechanics, or unlock Encore. The stock-Revolver tint is only a temporary visual experiment and can affect the existing Revolver model if enabled. Do not treat a successful compile as proof of in-game compatibility.

## Build validation
GitHub Actions compiles the project against the repository's reference archives as a code check. While this remains a visual/configuration prototype, the workflow does not publish a downloadable DLL. A direct DLL release will be enabled only after the custom weapon has meaningful implemented behavior.
