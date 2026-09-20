package com.autotouch.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class OverlayService extends Service {
    public static final String ACTION_SHOW = "com.autotouch.app.SHOW_CONTROLLER";
    private static final String CHANNEL = "autotouch_controller";

    private WindowManager windowManager;
    private View panel;
    private TextView state;
    private AutomationRunner runner;
    private boolean automationRunning;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("AutoTouch controller")
                .setContentText("Floating controls are available")
                .setContentIntent(pending)
                .setOngoing(true)
                .build();

        startForeground(7, notification);

        runner = new AutomationRunner(this, text -> {
            if (state != null) state.setText(text);
        });

        showOverlay();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (state != null && !automationRunning) {
            AutomationConfig config = AutomationConfig.load(this);
            state.setText(config.enabledCount() + " enabled");
        }
        return START_STICKY;
    }

    private void startAutomation() {
        AutomationConfig current = AutomationConfig.load(this);
        if (current.enabledCount() == 0) {
            Toast.makeText(this, "Turn on at least one automation toggle first", Toast.LENGTH_SHORT).show();
            state.setText("No modules");
            return;
        }
        if (AutoTouchAccessibilityService.instance() == null) {
            Toast.makeText(this, "Accessibility service is not connected", Toast.LENGTH_LONG).show();
            state.setText("Accessibility off");
            return;
        }
        automationRunning = true;
        state.setText("Starting");
        runner.start();
    }

    private void stopAutomation() {
        automationRunning = false;
        runner.stop();
        state.setText("Stopped");
    }

    private void toggleAutomation() {
        if (automationRunning) stopAutomation();
        else startAutomation();
    }

    private void testTouch() {
        AutoTouchAccessibilityService service = AutoTouchAccessibilityService.instance();
        if (service == null) {
            state.setText("Accessibility off");
            Toast.makeText(this, "Enable AutoTouch accessibility first", Toast.LENGTH_LONG).show();
            return;
        }
        state.setText("Testing touch");
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int x = Math.round(width * 0.095f);
        int y = Math.round(height * 0.673f);
        service.tap(x, y, () -> {
            state.setText("Test sent");
            Toast.makeText(this, "Test tap sent to the Menu button position", Toast.LENGTH_SHORT).show();
        });
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(8), dp(5), dp(8), dp(5));
        box.setBackgroundColor(Color.argb(235, 45, 40, 55));

        AutomationConfig config = AutomationConfig.load(this);

        state = new TextView(this);
        state.setText(config.enabledCount() + " enabled");
        state.setTextColor(Color.WHITE);
        state.setTextSize(15);
        state.setPadding(dp(8), dp(8), dp(10), dp(8));

        Button play = new Button(this);
        play.setText("Start");
        play.setOnClickListener(v -> startAutomation());

        Button stop = new Button(this);
        stop.setText("Stop");
        stop.setOnClickListener(v -> stopAutomation());

        Button test = new Button(this);
        test.setText("Test");
        test.setOnClickListener(v -> testTouch());

        box.addView(state);
        box.addView(play);
        box.addView(stop);
        box.addView(test);
        panel = box;

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 180;

        state.setOnTouchListener(new View.OnTouchListener() {
            int initialX, initialY;
            float initialTouchX, initialTouchY;
            boolean moved;

            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = e.getRawX();
                        initialTouchY = e.getRawY();
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - initialTouchX;
                        float dy = e.getRawY() - initialTouchY;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                        params.x = initialX + (int) dx;
                        params.y = initialY + (int) dy;
                        windowManager.updateViewLayout(box, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!moved) toggleAutomation();
                        return true;

                    default:
                        return false;
                }
            }
        });

        windowManager.addView(box, params);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    "Floating controller",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        if (runner != null) runner.stop();
        if (panel != null) windowManager.removeView(panel);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
