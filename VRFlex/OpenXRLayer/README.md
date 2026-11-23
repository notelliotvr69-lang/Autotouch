# Android OpenXR API layer port

This folder ports the Windows `XR_APILAYER_NOVENDOR_pull.dll` prototype to an
Android ARM64 OpenXR API layer named `libXR_APILAYER_NOVENDOR_pull.so`.

## What is included

- Khronos loader negotiation entry point.
- Required `xrCreateApiLayerInstance` chain implementation.
- `xrGetInstanceProcAddr` dispatch that intercepts `xrLocateSpace`.
- A frame-time-based forward pull using the located pose yaw.
- Speed and total-offset limits to prevent unbounded displacement.

## Android packaging

The layer manifest is packaged as an implicit APK layer at:

`assets/openxr/1/api_layers/implicit.d/XR_APILAYER_NOVENDOR_pull.json`

The shared library is packaged under the APK's native ARM64 library directory.
Android OpenXR layers discovered this way are available to the app containing
the assets and native library. This does not install a system-wide layer or
make unrelated games load it.

## Important behavior

This first Android port reproduces the supplied sample's always-on forward-pull
behavior. It does not yet bind the effect to controller triggers. It changes
located OpenXR space positions, so it is a prototype and must be tested in a
controlled, stationary play area; stop using it if it causes discomfort.
