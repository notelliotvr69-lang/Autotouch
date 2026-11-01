# DualSense Rumble

A small Android app that asks Android's input-device framework to run a brief vibration test on a connected DualSense controller.

## Build

Open the GitHub Actions workflow named **Build DualSense Rumble APK** and run it manually, or push a change under `DualSenseRumble/`. Download the `DualSense-Rumble-debug-APK` artifact from the completed workflow.

## Use

1. Pair the DualSense in Android Bluetooth settings.
2. Open the app and tap **Scan for Controller**.
3. If Android exposes the controller's vibrator, choose an intensity and tap **Start Short Test**.
4. Each test is a 1.5-second burst. Tap **Stop** to cancel it immediately.

The app also stops vibration whenever it goes into the background.

## Compatibility note

This first version uses Android's public `InputDevice.getVibratorManager()` API. Some Android devices expose a connected gamepad's rumble motor through this API; others expose the controller only for button/stick input. If the app detects the DualSense but reports that no rumble motor is available, this Android build cannot directly send DualSense Bluetooth output reports through the public input-device API on that phone. The app reports that limitation rather than pretending rumble is working.
