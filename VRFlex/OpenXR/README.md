# VRFlex OpenXR rebuild

## Target architecture

VRFlex is being rebuilt as a standalone immersive Quest application. The old Android Activity is a 2D prototype and is not the final app.

- Runtime: Khronos OpenXR on Meta Horizon OS.
- Rendering/input: native Android OpenXR app using the Khronos Android loader and Meta-supported OpenXR extensions where needed.
- Menu: a VRFlex-owned 3D panel positioned relative to the left controller grip pose.
- Menu visibility: an app-local boolean action bound to the left Touch controller Y click path; the panel is visible only while the action is held.
- Menu interaction: controller aim pose plus trigger click, with the right controller used as a pointer so the left hand can hold the menu.
- Settings: local headset storage, per-game profiles and capability declarations.
- Integrations: explicit supported game adapters only. OpenXR controller poses and input are scoped to VRFlex's own session; they do not grant access to another running game's input or tracking.

## First implementation milestones

1. Replace the 2D Activity entry point with an immersive OpenXR NativeActivity.
2. Establish the OpenXR instance, session, local reference space, stereo swapchains and frame loop.
3. Create left/right controller pose actions and the left-Y hold action.
4. Render a readable panel anchored to the left controller while Y is held.
5. Add menu tabs and local settings controls.
6. Add optional game adapters only where the game exposes a supported integration.

## Safety and compatibility boundaries

VRFlex will not root the headset, unlock its bootloader, replace Horizon OS files, write protected partitions, or execute arbitrary shell commands. A saved profile is not represented as an active game modification unless a compatible adapter confirms that it consumed the setting.

## Upstream technical reference

The implementation is based on the Khronos OpenXR Android loader and the official Meta OpenXR sample architecture. See the Meta OpenXR support and input guides:
- https://developers.meta.com/vr/documentation/native/android/mobile-openxr/
- https://developers.meta.com/vr/documentation/native/android/mobile-openxr-input/
- https://github.com/meta-quest/Meta-OpenXR-SDK

The native XR rebuild is not complete until its APK builds and the controller-attached menu has been tested on Quest hardware.
