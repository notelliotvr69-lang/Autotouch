#pragma once
#include <stdint.h>

#if defined(__GNUC__)
#define VRFLEX_API __attribute__((visibility("default")))
#else
#define VRFLEX_API
#endif

#ifdef __cplusplus
extern "C" {
#endif

// App-local movement state. This library does not read controller input itself.
// The calling OpenXR app supplies normalized trigger values and head yaw.
typedef struct VrFlexMovementState {
    float velocity_x;
    float velocity_z;
    float offset_x;
    float offset_z;
} VrFlexMovementState;

// Configure conservative movement limits. Values are clamped internally.
VRFLEX_API void vrflex_movement_reset(VrFlexMovementState* state);

// Advance app-local movement by one frame.
// forward_trigger: right-trigger amount [0,1].
// backward_trigger: left-trigger amount [0,1].
// head_yaw_radians: yaw in radians.
// delta_seconds: frame duration; internally clamped to 0..0.05.
// Returns 1 when state was updated, otherwise 0.
VRFLEX_API int32_t vrflex_movement_step(
    VrFlexMovementState* state,
    float forward_trigger,
    float backward_trigger,
    float head_yaw_radians,
    float delta_seconds);

#ifdef __cplusplus
}
#endif
