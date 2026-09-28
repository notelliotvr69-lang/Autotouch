# QuestLink OpenVR Probe

Experimental x64 OpenVR proxy used by QuestLink V7.8.

This is the first step toward the QuestLink OpenVR -> OpenXR bridge:

1. QuestLink backs up the game's original `openvr_api.dll`.
2. This proxy takes its place.
3. Calls are logged to `QuestLinkOpenVR.log`.
4. Calls are forwarded to the original DLL so we can learn exactly which OpenVR interfaces Gorilla Tag requests.

It is intentionally a probe, not a complete SteamVR replacement yet. Once the required interface/version list is known, QuestLink can implement the minimum OpenVR-facing layer and bridge it to the active OpenXR runtime.

Use the QuestLink PC UI to install or restore it.