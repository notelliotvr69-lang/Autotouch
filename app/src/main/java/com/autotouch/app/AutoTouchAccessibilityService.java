package com.autotouch.app;

import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public final class AutoTouchAccessibilityService extends AccessibilityService {
    private static AutoTouchAccessibilityService current;
    private AccessibilityButtonController.AccessibilityButtonCallback shortcutCallback;

    public static AutoTouchAccessibilityService instance() {
        return current;
    }

    @Override protected void onServiceConnected() {
        current = this;

        if (Build.VERSION.SDK_INT >= 26) {
            shortcutCallback = new AccessibilityButtonController.AccessibilityButtonCallback() {
                @Override public void onClicked(AccessibilityButtonController controller) {
                    showControllerFromShortcut();
                }
            };
            getAccessibilityButtonController().registerAccessibilityButtonCallback(shortcutCallback);
        }
    }

    private void showControllerFromShortcut() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Open AutoTouch and allow the floating controller first", Toast.LENGTH_LONG).show();
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(open);
            return;
        }

        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
        Toast.makeText(this, "AutoTouch controller opened", Toast.LENGTH_SHORT).show();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        if (Build.VERSION.SDK_INT >= 26 && shortcutCallback != null) {
            getAccessibilityButtonController().unregisterAccessibilityButtonCallback(shortcutCallback);
        }
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
