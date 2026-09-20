package com.autotouch.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public final class AutoTouchAccessibilityService extends AccessibilityService {
    private static AutoTouchAccessibilityService current;

    public static AutoTouchAccessibilityService instance() {
        return current;
    }

    @Override protected void onServiceConnected() {
        current = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    public void tap(int x, int y, Runnable finished) {
        Path path = new Path();
        path.moveTo(x, y);
        dispatch(path, 50, finished);
    }

    public void swipe(int x1, int y1, int x2, int y2, long durationMs, Runnable finished) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        dispatch(path, Math.max(1, durationMs), finished);
    }

    public void perform(Routine.Step step, Runnable finished) {
        if (step.kind() == Routine.Kind.SWIPE) {
            swipe(step.x1(), step.y1(), step.x2(), step.y2(), step.durationMs(), finished);
        } else {
            tap(step.x1(), step.y1(), finished);
        }
    }

    private void dispatch(Path path, long duration, Runnable finished) {
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration))
                .build();

        boolean accepted = dispatchGesture(
                gesture,
                new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription g) {
                        finished.run();
                    }

                    @Override public void onCancelled(GestureDescription g) {
                        finished.run();
                    }
                },
                null
        );

        if (!accepted) finished.run();
    }
}
