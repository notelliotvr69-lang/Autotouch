QuestLink OpenXR Runtime - Windows x64 prototype

NO VISUAL STUDIO OR CMAKE IS REQUIRED TO USE THE DOWNLOADED BUILD.

INSTALL
1. Extract QuestLinkRuntime-Windows-x64.zip.
2. Double-click INSTALL_QUESTLINK_RUNTIME.bat.
3. Accept the Windows administrator prompt.
4. Use RUNTIME_STATUS.bat if you want to verify the active OpenXR runtime.

RESTORE YOUR OLD RUNTIME
Double-click RESTORE_PREVIOUS_RUNTIME.bat.

IMPORTANT
This is the early QuestLink PC-runtime prototype. It can register with the Windows OpenXR loader and exposes a prototype virtual HMD/system, but the real D3D11 compositor, swapchains, tracking bridge, controller input and Quest video transport are still being built.

Do not expect Gorilla Tag to render in the headset yet.
