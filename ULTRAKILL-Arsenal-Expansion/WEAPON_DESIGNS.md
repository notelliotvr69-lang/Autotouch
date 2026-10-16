# Weapon design backlog

Requested features in this file are design targets, not implemented yet.

## Dual Revolvers (requested for v0.3.x)
- Prototype as a selectable weapon using two existing revolver models, with synchronized left/right firing and a distinct weapon identity.
- Do not claim implemented until firing, ammo, switching, and animations work in-game.

## New Arm (requested for v0.3.x)
- New arm concept: **Rift Arm** — a short-range pull/punch that yanks light enemies or props toward V1, with a cooldown and no effect on bosses.
- Needs its own model/animation or a deliberate placeholder, plus input binding and gameplay testing.

## Original Dual Revolvers concept
- Two-hand firing pattern with a deliberate ammo/reload cadence.
- Must be balanced against the existing Marksman and Sharpshooter revolvers.

## Beamcutter
- Sustained precision beam with heat buildup and a cooldown.
- Intended for controlled damage rather than unlimited hitscan DPS.

## Black Hole Cannon
- Slow, telegraphed projectile that briefly groups nearby enemies before a limited implosion.
- Needs strict range, duration, and damage caps to avoid trivializing arenas and bosses.

## Implementation checklist
- Find the game's real weapon selection and input APIs.
- Add weapon visuals, firing, projectiles/hitscan, damage, audio, and cooldowns.
- Integrate with normal weapon selection and level restarts.
- Test in an unmodified local copy of the game and tune against existing weapons.
