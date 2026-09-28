QuestLink OpenXR Runtime v0.2 - Windows x64 prototype

NO VISUAL STUDIO OR CMAKE IS REQUIRED TO USE THE DOWNLOADED BUILD.

INSTALL
1. Extract QuestLinkRuntime-Windows-x64.zip.
2. Double-click INSTALL_QUESTLINK_RUNTIME.bat.
3. Accept the Windows administrator prompt.
4. Use RUNTIME_STATUS.bat if you want to verify the active OpenXR runtime.

RESTORE YOUR OLD RUNTIME
Double-click RESTORE_PREVIOUS_RUNTIME.bat.

V0.2 PROGRESS
- QuestLink registers as the Windows OpenXR runtime.
- Exposes a prototype virtual stereo HMD.
- Adds XR_KHR_D3D11_enable support.
- Creates a D3D11 OpenXR session from the game's graphics device.
- Creates real D3D11 swapchain textures for the game to render into.
- Supports acquire / wait / release swapchain flow.
- Supports wait / begin / end frame flow at a prototype 90 Hz timing.
- Supports VIEW / LOCAL / STAGE reference spaces.
- Supplies static prototype head/eye poses.
- Adds neutral OpenXR action/input plumbing so apps can create input actions.
- QuestLink PC v7.8 tries Gorilla Tag directly with -vrmode openxr when QuestLink is active, without starting SteamVR.

NOT DONE YET
The Quest headset streaming/compositor bridge is not complete. V0.2 can accept the PC game's D3D11 render frames into QuestLink-owned swapchains, but those frames are not yet encoded and sent to the Quest app. Real headset/controller poses are also not connected yet.

If a game still chooses OpenVR instead of OpenXR, the later OpenVR compatibility shim will be needed.
