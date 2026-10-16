# GODSPLITTER — implementation and design notes

## Intended fantasy
A boss-trophy weapon built from the idea of Gabriel losing his sword arm in an alternate mod encounter. GODSPLITTER is available without beating Gabriel, so a player who is stuck on the fight can still use the prototype.

## Moveset
- **Primary — Divine Edge:** fast, close-range sword slash. Brief recovery, meant for weaving into gun combos rather than replacing the red arm's heavy punch.
- **Secondary — SMITE:** right-click projects a golden beam from above the targeted point. Initial prototype cooldown: about 2.8 seconds.
- **Kill effect — Golden Afterimage:** after a confirmed sword kill, attempt one quick slash against the nearest living enemy within about 7 units.
- **Visual language:** gold and white light, silver hilt/guard, a small blue blade-spine accent; V1's rough industrial hardware should contrast with Gabriel's celestial design.
- **Story:** Gabriel returns later with a replacement arm. This is alternate mod story, not canon.

## Current code prototype
- `GodsplitterWeapon.cs` generates a simple sword viewmodel and a beam/impact effect without requiring an external asset bundle.
- Left-click attempts a close-range hit; right-click attempts a targeted SMITE hit.
- Damage API discovery uses reflection and can fail if this ULTRAKILL version's damage signature differs.
- The kill afterimage depends on successfully reading enemy health after the initial hit.
- Plugin registers GODSPLITTER in the extra weapon list alongside the two existing Museum weapons.

## Must test / known risks
1. Confirm GitHub Actions can compile against the user's current ULTRAKILL references.
2. Confirm GODSPLITTER appears as a separate selectable entry and is hidden while another weapon is equipped.
3. Check camera parenting and sword viewmodel positioning/FOV.
4. Confirm EnemyIdentifier discovery and DeliverDamage signature. If damage doesn't work, inspect the installed game's Assembly-CSharp APIs and patch this code to the exact signature.
5. Confirm SMITE does not hit world geometry in an unintuitive way and tune its cooldown/damage.
6. Replace primitive blade with a proper model/animation, then add audio, slash trail, and better impact VFX.
7. Test level transitions, restarts, weapon switching, and scenes with no enemies.

## Older backlog
- Dual revolvers: synchronized left/right firing, distinct ammo cadence, weapon switching and animations.
- Beamcutter: controlled precision beam with heat buildup.
- Black Hole Cannon: slow, telegraphed grouping projectile with strict range/duration/damage caps.
