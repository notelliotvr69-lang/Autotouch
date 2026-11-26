package com.vrflex.mobile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONObject;

/**
 * Normalizes Quest pose telemetry and applies VRFlex-specific arm reach settings.
 * Coordinates are currently adjusted in the Quest client's transmitted world axes.
 * This is an internal tracking adapter, not OS-wide input injection.
 */
public final class TrackingProcessor {
    public static final String ACTION_TRACKING_UPDATED = "com.vrflex.mobile.TRACKING_UPDATED";
    public static final String EXTRA_TRACKING_JSON = "tracking_json";
    public static final String EXTRA_PACKET_COUNT = "packet_count";
    private static volatile JSONObject latestTracking;
    private TrackingProcessor() {}

    public static JSONObject process(Context context, JSONObject incoming, int packetCount) {
        try {
            JSONObject head = incoming.optJSONObject("head");
            JSONObject left = incoming.optJSONObject("left");
            JSONObject right = incoming.optJSONObject("right");
            if (head == null || left == null || right == null) return null;

            float hx = finite(head.optDouble("x", Double.NaN));
            float hy = finite(head.optDouble("y", Double.NaN));
            float hz = finite(head.optDouble("z", Double.NaN));
            float scale = 1.0f, ox = 0f, oy = 0f, oz = 0f;
            SharedPreferences prefs = context.getSharedPreferences("vrflex_tracking", Context.MODE_PRIVATE);
            scale = clamp(prefs.getFloat("arm_scale", 1.0f), 0.5f, 1.5f);
            ox = prefs.getInt("arm_offset_x_cm", 0) / 100.0f;
            oy = prefs.getInt("arm_offset_y_cm", 0) / 100.0f;
            oz = prefs.getInt("arm_offset_z_cm", 0) / 100.0f;

            long now = System.currentTimeMillis();
            JSONObject adjusted = new JSONObject()
                .put("type", "tracking_adjusted")
                .put("head", vector(hx, hy, hz))
                .put("left", adjust(left, hx, hy, hz, scale, ox, oy, oz))
                .put("right", adjust(right, hx, hy, hz, scale, ox, oy, oz))
                .put("buttons", incoming.optInt("buttons", 0))
                .put("arm_scale", scale)
                .put("arm_offset", vector(ox, oy, oz))
                .put("packet_count", packetCount)
                .put("timestamp_ms", now);

            latestTracking = adjusted;
            prefs.edit().putInt("last_packet_count", packetCount).putLong("last_packet_at", now).apply();
            Intent update = new Intent(ACTION_TRACKING_UPDATED);
            update.setPackage(context.getPackageName());
            update.putExtra(EXTRA_TRACKING_JSON, adjusted.toString());
            update.putExtra(EXTRA_PACKET_COUNT, packetCount);
            context.sendBroadcast(update);
            return adjusted;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static JSONObject getLatestTracking() {
        JSONObject value = latestTracking;
        if (value == null) return null;
        try { return new JSONObject(value.toString()); } catch (Exception ignored) { return null; }
    }

    private static JSONObject adjust(JSONObject hand, float hx, float hy, float hz,
                                     float scale, float ox, float oy, float oz) throws Exception {
        float x = finite(hand.optDouble("x", Double.NaN));
        float y = finite(hand.optDouble("y", Double.NaN));
        float z = finite(hand.optDouble("z", Double.NaN));
        return vector(hx + (x - hx) * scale + ox,
                      hy + (y - hy) * scale + oy,
                      hz + (z - hz) * scale + oz);
    }

    private static JSONObject vector(float x, float y, float z) throws Exception {
        return new JSONObject().put("x", x).put("y", y).put("z", z);
    }

    private static float finite(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > 10000) {
            throw new IllegalArgumentException("Invalid tracking coordinate");
        }
        return (float) value;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
