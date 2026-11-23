# Quest OS-supported panel launch

VRFlex opts into Meta Horizon OS's documented hybrid-app panel launch path.

## Implemented in the manifest

- Declares Meta Horizon OS hybrid-app metadata (minimum OS SDK 69).
- Keeps the existing 2D launcher activity.
- Adds a separate com.oculus.intent.category.OVERLAY_LAUNCHER intent filter to the panel activity.
- Sets a minimum panel size so the app's existing settings UI remains usable.

On a supported Horizon OS build, this makes VRFlex eligible to be launched as an OS-managed 2D panel from the Quest overlay launch flow while another immersive app is active. The OS controls placement and input focus.

## Explicit limitations

The current app UI separates the intended left-Y Mod Menu from the left-Menu (☰) Games switcher. The Games screen can launch an installed app after saving its profile, and both screens show the selected game's saved settings. This is UI/profile behavior, not proof that the physical controller shortcuts work in another game.\n\nThis is not a global floating-window permission and does not provide a global controller input listener. The public panel-launch mechanism does not let a third-party app register a callback for holding the left controller's Y button, attach its panel to a tracked controller, or force itself open over every game.

OpenXR input actions are scoped to the app's own OpenXR session. A game must integrate VRFlex's adapter or expose a compatible plugin interface for VRFlex to receive game input or apply app-local transforms.

## Safety

No root, bootloader unlock, firmware modification, Horizon OS file replacement, protected partition writes, or game-file patching is used.

## Verification status

The manifest change is source-level implementation. Build success will verify Android packaging only. Actual overlay launch behavior still needs testing on a Quest running a supported Horizon OS version.
