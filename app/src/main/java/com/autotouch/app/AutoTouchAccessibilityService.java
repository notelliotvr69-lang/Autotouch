package com.autotouch.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public final class AutoTouchAccessibilityService extends AccessibilityService {
    private static AutoTouchAccessibilityService current;
    public static AutoTouchAccessibilityService instance() { return current; }

    @Override protected void onServiceConnected() { current = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { if (current == this) current = null; super.onDestroy(); }

    public void perform(Routine.Step step, Runnable finished) {
        Path path = new Path();
        path.moveTo(step.x1(), step.y1());
        long duration = 50;
        if (step.kind() == Routine.Kind.SWIPE) {
            path.lineTo(step.x2(), step.y2());
            duration = Math.max(1, step.durationMs());
        }
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration)).build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { finished.run(); }
            @Override public void onCancelled(GestureDescription g) { finished.run(); }
        }, null);
        if (!accepted) finished.run();
    }
}
