package com.motofbt.app;

import com.google.mediapipe.tasks.components.containers.Landmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.ArrayList;
import java.util.List;

final class TrackerMapper {
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

    private float scale = 1f;
    private float centerX = 0f;
    private float centerZ = 0f;
    private float groundY = 0f;
    private boolean calibrated = false;

    boolean isCalibrated() {
        return calibrated;
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
        if (rawHeight < 0.25f) rawHeight = 1.6f;

        scale = userHeightMeters / rawHeight;

        Vec mappedHip = mapRaw(hip);
        Vec mappedFeet = average(mapRaw(leftFoot), mapRaw(rightFoot));
        centerX = mappedHip.x;
        centerZ = mappedHip.z;
        groundY = mappedFeet.y;
        calibrated = true;
        return true;
    }

    List<Tracker> map(PoseLandmarkerResult result, boolean extras) {
        List<Landmark> lm = world(result);
        if (lm == null || lm.size() < 33) return List.of();

        Vec hip = corrected(average(point(lm, 23), point(lm, 24)));
        Vec leftFoot = corrected(foot(lm, true));
        Vec rightFoot = corrected(foot(lm, false));

        float hipYaw = yawBetween(point(lm, 23), point(lm, 24)) + 90f;
        float leftFootYaw = yawBetween(point(lm, 29), point(lm, 31));
        float rightFootYaw = yawBetween(point(lm, 30), point(lm, 32));

        List<Tracker> out = new ArrayList<>();
        out.add(new Tracker(1, hip.x, hip.y, hip.z, 0, hipYaw, 0));
        out.add(new Tracker(2, leftFoot.x, leftFoot.y, leftFoot.z, 0, leftFootYaw, 0));
        out.add(new Tracker(3, rightFoot.x, rightFoot.y, rightFoot.z, 0, rightFootYaw, 0));

        if (extras) {
            Vec chest = corrected(average(point(lm, 11), point(lm, 12)));
            Vec leftKnee = corrected(point(lm, 25));
            Vec rightKnee = corrected(point(lm, 26));
            out.add(new Tracker(4, chest.x, chest.y, chest.z, 0, hipYaw, 0));
            out.add(new Tracker(5, leftKnee.x, leftKnee.y, leftKnee.z, 0, hipYaw, 0));
            out.add(new Tracker(6, rightKnee.x, rightKnee.y, rightKnee.z, 0, hipYaw, 0));
        }

        return out;
    }

    private List<Landmark> world(PoseLandmarkerResult result) {
        if (result == null || result.worldLandmarks().isEmpty()) return null;
        return result.worldLandmarks().get(0);
    }

    private Vec foot(List<Landmark> lm, boolean left) {
        if (left) {
            return average(point(lm, 27), point(lm, 29), point(lm, 31));
        } else {
            return average(point(lm, 28), point(lm, 30), point(lm, 32));
        }
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

    private float yawBetween(Vec from, Vec to) {
        Vec a = corrected(from);
        Vec b = corrected(to);
        float dx = b.x - a.x;
        float dz = b.z - a.z;
        return (float) Math.toDegrees(Math.atan2(dx, dz));
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

    private static final class Vec {
        final float x, y, z;
        Vec(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
