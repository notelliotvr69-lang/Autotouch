#include "vrflex_movement.h"

#include <algorithm>
#include <cmath>

namespace {
constexpr float kMaxSpeed = 1.5f;       // metres per second
constexpr float kAcceleration = 3.0f;   // metres per second squared
constexpr float kDeceleration = 5.0f;   // metres per second squared
constexpr float kDeadZone = 0.08f;

float Clamp01(float value) {
    if (!std::isfinite(value)) return 0.0f;
    return std::clamp(value, 0.0f, 1.0f);
}

float Approach(float current, float target, float max_delta) {
    if (current < target) return std::min(current + max_delta, target);
    return std::max(current - max_delta, target);
}
}  // namespace

extern "C" VRFLEX_API void vrflex_movement_reset(VrFlexMovementState* state) {
    if (state == nullptr) return;
    state->velocity_x = 0.0f;
    state->velocity_z = 0.0f;
    state->offset_x = 0.0f;
    state->offset_z = 0.0f;
}

extern "C" VRFLEX_API int32_t vrflex_movement_step(
    VrFlexMovementState* state,
    float forward_trigger,
    float backward_trigger,
    float head_yaw_radians,
    float delta_seconds) {
    if (state == nullptr || !std::isfinite(head_yaw_radians) ||
        !std::isfinite(delta_seconds)) {
        return 0;
    }

    const float dt = std::clamp(delta_seconds, 0.0f, 0.05f);
    if (dt <= 0.0f) return 1;

    const float forward = Clamp01(forward_trigger);
    const float backward = Clamp01(backward_trigger);
    const float input = (forward > kDeadZone ? forward : 0.0f) -
                        (backward > kDeadZone ? backward : 0.0f);

    // Forward follows the user's horizontal head-facing direction.
    const float target_speed = std::clamp(input, -1.0f, 1.0f) * kMaxSpeed;
    const float rate = std::fabs(target_speed) > 0.001f
        ? kAcceleration : kDeceleration;
    const float speed = std::hypot(state->velocity_x, state->velocity_z);
    const float current_speed = std::copysign(speed, state->velocity_z == 0.0f
        ? target_speed : -state->velocity_z);
    const float next_speed = Approach(current_speed, target_speed, rate * dt);

    // OpenXR's conventional forward direction is -Z at zero yaw.
    const float direction_x = std::sin(head_yaw_radians);
    const float direction_z = -std::cos(head_yaw_radians);
    state->velocity_x = direction_x * next_speed;
    state->velocity_z = direction_z * next_speed;

    state->offset_x += state->velocity_x * dt;
    state->offset_z += state->velocity_z * dt;

    // Keep this app-local offset bounded; this is not a tracking-origin rewrite.
    constexpr float kMaxOffset = 3.0f;
    state->offset_x = std::clamp(state->offset_x, -kMaxOffset, kMaxOffset);
    state->offset_z = std::clamp(state->offset_z, -kMaxOffset, kMaxOffset);
    return 1;
}
