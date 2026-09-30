# QuestLink wireless development build

This is a development candidate, not a verified playable release. A passing build is
not evidence that Gorilla Tag or a Quest 3 works end to end.

Implemented path: Gorilla Tag -> bundled OpenComposite -> QuestLink D3D11 OpenXR
runtime -> H.264 over LAN TCP -> Quest native OpenXR projection layers.
No SteamVR process is required by this path.

Use the v7.12-dev Quest APK and v0.4-dev runtime together. Protocol 2 intentionally
rejects the old 2D preview client. The PC v7.11 launcher still supplies game launching.
The Quest launcher opens a native VR activity for streaming. The native activity
retries the PC's IPv4 address on TCP 47991. Both devices must be on the same private
LAN and the existing runtime firewall rule must allow this port.

Media Foundation tries hardware H.264 encoders first, then software. If initialization
fails, it falls back to JPEG. NV12 conversion and GPU readback still use the CPU;
this implementation does not promise low latency on an integrated GPU. Video is
640x672 per eye and PC rendering is paced at 72 Hz. The Quest compositor uses the
original PC projection pose/FOV for late reprojection, rather than falsely labeling
an old frame with the newest head pose. Tracking is invalidated after 250 ms.

Before upgrading, retain the working APK and runtime folder. The development APK
uses a debug signing key; a differently signed existing APK may require an uninstall,
which clears its saved settings. Do not overwrite the previous runtime folder.
Activation changes the Windows OpenXR runtime. The included restore script restores
the previous runtime recorded by the installer.

## Hardware acceptance checks still required

1. Install the matching APK/runtime; connect Quest and PC to the same Wi-Fi LAN.
2. Open PC v7.11, enter its IPv4 address in the Quest launcher and connect.
3. Launch GTAG and enter wireless VR. Verify the Windows process list has no SteamVR.
4. Verify separate left/right images, correct vertical orientation, scale and IPD.
5. Verify head translation, rotation and both controller poses. Test buttons,
   thumbsticks, triggers and grips, including simultaneous left/right inputs.
6. Disconnect Wi-Fi: stale frames should disappear and inputs stop within 250 ms.
   Reconnect and verify the game resumes. Test the Quest system menu and recentering.
7. Measure sustained frame rate, encode/decode time, network latency, packet stalls,
   battery/thermal behavior and motion-to-photon delay before extended play.

Not implemented: PC game audio, microphone forwarding, haptic transport, depth-based
reprojection, adaptive bitrate, UDP/FEC transport, hardware zero-copy capture and
full OpenXR conformance. OpenComposite/game compatibility is not yet hardware-tested.
TCP can stall on Wi-Fi loss. A successful compile does not close these gaps.

PC logs: `%LOCALAPPDATA%\QuestLinkRuntime.log`. Android: `adb logcat -s QuestLink`.
