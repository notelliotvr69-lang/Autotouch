package com.autotouch.app;

import android.content.Context;
import android.content.SharedPreferences;

/** A reusable automation profile represented as an immutable sequence of states. */
public final class Routine {
    public enum Kind { TAP, SWIPE }
    public record Step(Kind kind, int x1, int y1, int x2, int y2, long durationMs, long delayMs) {}

    public final String name;
    public final Step[] steps;
    public final int repetitions;

    public Routine(String name, Step[] steps, int repetitions) {
        this.name = name;
        this.steps = steps.clone();
        this.repetitions = Math.max(1, repetitions);
    }

    public static Routine load(Context context) {
        SharedPreferences p = context.getSharedPreferences("profile", Context.MODE_PRIVATE);
        Kind kind = p.getBoolean("swipe", false) ? Kind.SWIPE : Kind.TAP;
        Step step = new Step(kind, p.getInt("x1", 300), p.getInt("y1", 600),
                p.getInt("x2", 700), p.getInt("y2", 600), p.getLong("duration", 300),
                p.getLong("delay", 1000));
        return new Routine(p.getString("name", "My routine"), new Step[]{step}, p.getInt("repetitions", 10));
    }

    public void save(Context context) {
        Step s = steps[0];
        context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit()
                .putString("name", name).putBoolean("swipe", s.kind == Kind.SWIPE)
                .putInt("x1", s.x1).putInt("y1", s.y1).putInt("x2", s.x2).putInt("y2", s.y2)
                .putLong("duration", s.durationMs).putLong("delay", s.delayMs)
                .putInt("repetitions", repetitions).apply();
    }
}
