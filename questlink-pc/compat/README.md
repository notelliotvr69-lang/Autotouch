# Unity OpenVR compatibility

Gorilla Tag's XRSDKOpenVR.dll imports VRHeadsetView directly, while the pinned
OpenComposite 1.0.1539 DLL does not export it. Windows fails to load that plugin
with error 127 before OpenXR initialization.

This DLL forwards every existing OpenComposite export to opencomposite_backend.dll
and implements VRHeadsetView as an unavailable optional interface (null).
Valve's Unity plugin checks for null before accessing this desktop mirror API:
https://github.com/ValveSoftware/unity-xr-plugin/blob/master/Providers/Display/Display.cpp

The SteamVR desktop mirror is unavailable. This does not implement a replacement
headset, compositor, or controller API; those still come from OpenComposite.

Build with Zig 0.16.0 (python package ziglang):
python -m ziglang cc -target x86_64-windows-gnu -shared -nostdlib -O2 -DQUESTLINK_NO_CRT compat.c openvr_api.def -o openvr_api.dll
The backend is pinned to SHA256
827ad85f3606a4dc4a8f5561a8ca69e4c6c1b5d2b9cd3315a461b9270b08242c.
Run: python test_exports.py <directory-containing-both-dlls>

Local verification: current Gorilla Tag (Unity 6000.2.9f1) loads its OpenVR plugin,
creates a QuestLink OpenXR D3D11 session and submits projection frames without
SteamVR. Headset rendering and tracking still require a connected Quest test.
