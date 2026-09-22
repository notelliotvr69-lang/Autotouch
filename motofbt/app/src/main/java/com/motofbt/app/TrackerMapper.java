package com.motofbt.app;

import com.google.mediapipe.tasks.components.containers.Landmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TrackerMapper {
    static final int MODE_3 = 3;
    static final int MODE_6 = 6;
    static final int MODE_8 = 8;

    static final int SMOOTH_RESPONSIVE = 0;
    static final int SMOOTH_BALANCED = 1;
    static final int SMOOTH_STABLE = 2;

    static final class Tracker {
        final int id;
        final float x, y, z;
        final float rx, ry, rz;

        Tracker(int id, float x, float y, float z, float rx, float ry, float rz) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }
    }

    static final class HeadAnchor {
        final float x, y, z;

        HeadAnchor(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final Map<Integer, Tracker> filtered = new HashMap<>();

    private float scale = 1f;
    private float centerX = 0f;
    private float centerZ = 0f;
    private float groundY = 0f;

    private float calibrationBodyYaw = 0f;
    private float calibrationLeftFootYaw = 0f;
    private float calibrationRightFootYaw = 0f;

    private boolean calibrated = false;
    private boolean bodyYawReady = false;
    private float filteredBodyYaw = 0f;

    boolean isCalibrated() {
        return calibrated;
    }

    void resetFilters() {
        filtered.clear();
        bodyYawReady = false;
        filteredBodyYaw = 0f;
    }

    boolean calibrate(PoseLandmarkerResult result, float userHeightMeters) {
        List<Landmark> lm = world(result);
        if (lm == null || lm.size() < 33) return false;

        Vec head = average(point(lm, 7), point(lm, 8));
        Vec leftFoot = foot(lm, true);
        Vec rightFoot = foot(lm, false);
        Vec feet = average(leftFoot, rightFoot);
        Vec hip = average(point(lm, 23), point(lm, 24));

        float rawHeight = Math.abs(head.y - feet.y);
        if (rawHeight < 0.25f) return false;

        float estimatedFullBody = rawHeight * 1.075f;
        scale = userHeightMeters / estimatedFullBody;

        Vec mappedHip = mapRaw(hip);
        Vec mappedFeet = average(mapRaw(leftFoot), mapRaw(rightFoot));
        centerX = mappedHip.x;
        centerZ = mappedHip.z;
        groundY = mappedFeet.y;

        calibrationBodyYaw = bodyYawAbsolute(lm);
        calibrationLeftFootYaw = vectorYawAbsolute(point(lm, 29), point(lm, 31));
        calibrationRightFootYaw = vectorYawAbsolute(point(lm, 30), point(lm, 32));

        calibrated = true;
        resetFilters();
        return true;
    }

    List<Tracker> map(
            PoseLandmarkerResult result,
            int trackerMode,
            int smoothingMode,
            boolean floorLock,
            boolean bodyTurning,
            boolean reverseTurning
    ) {
        List<Landmark> lm = world(result);
        if (lm == null || lm.size() < 33) return List.of();

        Vec hip = corrected(average(point(lm, 23), point(lm, 24)));
        Vec leftFoot = corrected(foot(lm, true));
        Vec rightFoot = corrected(foot(lm, false));

        if (floorLock) {
            leftFoot = floorLock(leftFoot);
            rightFoot = floorLock(rightFoot);
        }

        float turnSign = reverseTurning ? -1f : 1f;
        float bodyYaw = 0f;

        if (bodyTurning) {
            float targetYaw = normalizeAngle(bodyYawAbsolute(lm) - calibrationBodyYaw) * turnSign;
            bodyYaw = smoothBodyYaw(targetYaw, smoothingMode);
        }

        float leftFootYaw = bodyTurning
                ? normalizeAngle(vectorYawAbsolute(point(lm, 29), point(lm, 31))
                    - calibrationLeftFootYaw) * turnSign
                : 0f;
        float rightFootYaw = bodyTurning
                ? normalizeAngle(vectorYawAbsolute(point(lm, 30), point(lm, 32))
                    - calibrationRightFootYaw) * turnSign
                : 0f;

        Angles leftFootAngles = anglesBetween(point(lm, 29), point(lm, 31));
        Angles rightFootAngles = anglesBetween(point(lm, 30), point(lm, 32));

        List<Tracker> raw = new ArrayList<>();
        raw.add(new Tracker(1, hip.x, hip.y, hip.z, 0f, bodyYaw, 0f));
        raw.add(new Tracker(2, leftFoot.x, leftFoot.y, leftFoot.z,
                leftFootAngles.pitch, leftFootYaw, 0f));
        raw.add(new Tracker(3, rightFoot.x, rightFoot.y, rightFoot.z,
                rightFootAngles.pitch, rightFootYaw, 0f));

        if (trackerMode >= MODE_6) {
            Vec chest = corrected(average(point(lm, 11), point(lm, 12)));
            Vec leftKnee = corrected(point(lm, 25));
            Vec rightKnee = corrected(point(lm, 26));

            Angles leftLeg = anglesBetween(point(lm, 23), point(lm, 25));
            Angles rightLeg = anglesBetween(point(lm, 24), point(lm, 26));

            raw.add(new Tracker(4, chest.x, chest.y, chest.z, 0f, bodyYaw, 0f));
            raw.add(new Tracker(5, leftKnee.x, leftKnee.y, leftKnee.z,
                    leftLeg.pitch, bodyYaw, 0f));
            raw.add(new Tracker(6, rightKnee.x, rightKnee.y, rightKnee.z,
                    rightLeg.pitch, bodyYaw, 0f));
        }

        if (trackerMode >= MODE_8) {
            Vec leftElbow = corrected(point(lm, 13));
            Vec rightElbow = corrected(point(lm, 14));
            Angles leftArm = anglesBetween(point(lm, 11), point(lm, 13));
            Angles rightArm = anglesBetween(point(lm, 12), point(lm, 14));

            raw.add(new Tracker(7, leftElbow.x, leftElbow.y, leftElbow.z,
                    leftArm.pitch, bodyYaw, 0f));
            raw.add(new Tracker(8, rightElbow.x, rightElbow.y, rightElbow.z,
                    rightArm.pitch, bodyYaw, 0f));
        }

        List<Tracker> out = new ArrayList<>(raw.size());
        for (Tracker tracker : raw) {
            out.add(filter(tracker, smoothingMode));
        }
        return out;
    }

    HeadAnchor headAnchor(PoseLandmarkerResult result) {
        List<Landmark> lm = world(result);
        if (lm == null || lm.size() < 33 || !calibrated) return null;

        // VRChat wants the root of the head rather than the eyes. Blend the
        // ear midpoint toward the shoulder midpoint to approximate the base
        // of the skull/upper neck while staying in the exact same space as
        // the virtual body trackers.
        Vec ears = average(point(lm, 7), point(lm, 8));
        Vec shoulders = average(point(lm, 11), point(lm, 12));
        Vec headRoot = new Vec(
                ears.x * 0.72f + shoulders.x * 0.28f,
                ears.y * 0.72f + shoulders.y * 0.28f,
                ears.z * 0.72f + shoulders.z * 0.28f
        );

        Vec p = corrected(headRoot);
        return new HeadAnchor(p.x, p.y, p.z);
    }

    private float smoothBodyYaw(float target, int smoothingMode) {
        if (!bodyYawReady) {
            filteredBodyYaw = target;
            bodyYawReady = true;
            return target;
        }

        float alpha;
        if (smoothingMode == SMOOTH_STABLE) alpha = 0.22f;
        else if (smoothingMode == SMOOTH_RESPONSIVE) alpha = 0.58f;
        else alpha = 0.36f;

        filteredBodyYaw = lerpAngle(filteredBodyYaw, target, alpha);
        return filteredBodyYaw;
    }

    private float bodyYawAbsolute(List<Landmark> lm) {
        Vec leftSide = average(mapRaw(point(lm, 11)), mapRaw(point(lm, 23)));
        Vec rightSide = average(mapRaw(point(lm, 12)), mapRaw(point(lm, 24)));
        Vec shoulders = average(mapRaw(point(lm, 11)), mapRaw(point(lm, 12)));
        Vec hips = average(mapRaw(point(lm, 23)), mapRaw(point(lm, 24)));

        Vec side = subtract(rightSide, leftSide);
        Vec up = subtract(shoulders, hips);

        // Torso forward direction from the 3D body plane. Unlike shoulder-only
        // yaw this keeps a front/back distinction, so turning past 90 degrees
        // does not make the avatar suddenly snap the wrong way.
        Vec forward = cross(side, up);
        float length = length(forward);

        if (length < 0.0001f) {
            return vectorYawAbsolute(point(lm, 11), point(lm, 12)) + 90f;
        }

        forward = new Vec(forward.x / length, forward.y / length, forward.z / length);
        return (float) Math.toDegrees(Math.atan2(forward.x, forward.z));
    }

    private float vectorYawAbsolute(Vec from, Vec to) {
        Vec a = mapRaw(from);
        Vec b = mapRaw(to);
        return (float) Math.toDegrees(Math.atan2(b.x - a.x, b.z - a.z));
    }

    private Tracker filter(Tracker now, int smoothingMode) {
        Tracker old = filtered.get(now.id);
        if (old == null) {
            filtered.put(now.id, now);
            return now;
        }

        float baseAlpha;
        if (smoothingMode == SMOOTH_STABLE) baseAlpha = 0.24f;
        else if (smoothingMode == SMOOTH_RESPONSIVE) baseAlpha = 0.62f;
        else baseAlpha = 0.40f;

        float dx = now.x - old.x;
        float dy = now.y - old.y;
        float dz = now.z - old.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        float alpha = Math.min(0.88f, baseAlpha + Math.min(distance, 0.35f) * 1.2f);
        float maxJump = smoothingMode == SMOOTH_RESPONSIVE ? 0.45f : 0.32f;

        float ratio = 1f;
        if (distance > maxJump && distance > 0.0001f) {
            ratio = maxJump / distance;
        }

        float tx = old.x + dx * ratio;
        float ty = old.y + dy * ratio;
        float tz = old.z + dz * ratio;

        Tracker filteredNow = new Tracker(
                now.id,
                lerp(old.x, tx, alpha),
                lerp(old.y, ty, alpha),
                lerp(old.z, tz, alpha),
                lerpAngle(old.rx, now.rx, alpha),
                lerpAngle(old.ry, now.ry, alpha),
                lerpAngle(old.rz, now.rz, alpha)
        );

        filtered.put(now.id, filteredNow);
        return filteredNow;
    }

    private Vec floorLock(Vec foot) {
        float y = foot.y;
        if (y < 0f) y = 0f;
        if (y < 0.065f) y = 0f;
        return new Vec(foot.x, y, foot.z);
    }

    private List<Landmark> world(PoseLandmarkerResult result) {
        if (result == null || result.worldLandmarks().isEmpty()) return null;
        return result.worldLandmarks().get(0);
    }

    private Vec foot(List<Landmark> lm, boolean left) {
        if (left) {
            return weightedAverage(
                    point(lm, 27), 0.45f,
                    point(lm, 29), 0.25f,
                    point(lm, 31), 0.30f
            );
        }
        return weightedAverage(
                point(lm, 28), 0.45f,
                point(lm, 30), 0.25f,
                point(lm, 32), 0.30f
        );
    }

    private Vec point(List<Landmark> lm, int index) {
        Landmark l = lm.get(index);
        return new Vec(l.x(), l.y(), l.z());
    }

    private Vec mapRaw(Vec v) {
        return new Vec(-v.x * scale, -v.y * scale, -v.z * scale);
    }

    private Vec corrected(Vec v) {
        Vec m = mapRaw(v);
        if (!calibrated) return m;
        return new Vec(m.x - centerX, m.y - groundY, m.z - centerZ);
    }

    private Angles anglesBetween(Vec from, Vec to) {
        Vec a = corrected(from);
        Vec b = corrected(to);
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float dz = b.z - a.z;
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(0.0001f, horizontal)));
        return new Angles(pitch, yaw);
    }

    private Vec average(Vec... values) {
        float x = 0, y = 0, z = 0;
        for (Vec v : values) {
            x += v.x;
            y += v.y;
            z += v.z;
        }
        float n = values.length;
        return new Vec(x / n, y / n, z / n);
    }

    private Vec weightedAverage(
            Vec a, float aw,
            Vec b, float bw,
            Vec c, float cw
    ) {
        float total = aw + bw + cw;
        return new Vec(
                (a.x * aw + b.x * bw + c.x * cw) / total,
                (a.y * aw + b.y * bw + c.y * cw) / total,
                (a.z * aw + b.z * bw + c.z * cw) / total
        );
    }

    private Vec subtract(Vec a, Vec b) {
        return new Vec(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    private Vec cross(Vec a, Vec b) {
        return new Vec(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
        );
    }

    private float length(Vec v) {
        return (float) Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
    }

    private float normalizeAngle(float value) {
        while (value > 180f) value -= 360f;
        while (value <= -180f) value += 360f;
        return value;
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float lerpAngle(float a, float b, float t) {
        float d = normalizeAngle(b - a);
        return normalizeAngle(a + d * t);
    }

    private static final class Vec {
        final float x, y, z;
        Vec(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class Angles {
        final float pitch, yaw;
        Angles(float pitch, float yaw) {
            this.pitch = pitch;
            this.yaw = yaw;
        }
    }
}
