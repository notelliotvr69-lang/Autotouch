# VRFlex Android native movement core

This is the Android/Quest ARM64 shared-library port of the movement math from the
Windows prototype. Android native libraries use the `.so` format rather than
Windows `.dll` files.

## What it does

- Accepts right-trigger forward and left-trigger backward values from a host app.
- Uses the horizontal headset yaw to choose the forward direction.
- Applies a dead zone, bounded acceleration, a 1.5 m/s speed cap, and a 3 m
  app-local offset limit.
- Clamps frame time to avoid large jumps after a stalled frame.

## Important integration boundary

This library does **not** read controller input, install itself into other games,
or modify Quest-wide tracking. A compatible OpenXR application must load the
library, pass its own trigger/yaw values to `vrflex_movement_step`, and apply the
returned offset inside its own scene or locomotion system. Android app sandboxing
means this library cannot automatically attach to unrelated games.

The earlier Windows sample's `xrLocateSpace` interception was not a complete
API layer: it omitted the required `xrCreateApiLayerInstance` chain and applied
unbounded movement on every locate call. This Android port deliberately starts
with a bounded, app-local movement core instead of pretending to be a system-wide
layer.

## Build

The GitHub Actions workflow builds `libvrflex_movement.so` for Quest's
`arm64-v8a` ABI using the Android NDK. Hardware integration still needs to be
implemented and tested in a compatible OpenXR host app.
