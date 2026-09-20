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
    private static final String CHANNEL = "autotouch_controller";
    private WindowManager windowManager;
    private View panel;
    private TextView state;
    private boolean running;

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
        showOverlay();
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
        state.setPadding(0, 0, dp(6), 0);

        Button play = new Button(this);
        play.setText("Start");
        play.setOnClickListener(v -> {
            AutomationConfig current = AutomationConfig.load(this);
            if (current.enabledCount() == 0) {
                Toast.makeText(this, "Turn on at least one automation toggle first", Toast.LENGTH_SHORT).show();
                return;
            }
            running = true;
            state.setText("Running • " + current.enabledCount());
            Toast.makeText(this, current.enabledSummary(), Toast.LENGTH_SHORT).show();
        });

        Button stop = new Button(this);
        stop.setText("Stop");
        stop.setOnClickListener(v -> {
            running = false;
            state.setText("Stopped");
        });

        box.addView(state);
        box.addView(play);
        box.addView(stop);
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

            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = e.getRawX();
                    initialTouchY = e.getRawY();
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = initialX + (int) (e.getRawX() - initialTouchX);
                    params.y = initialY + (int) (e.getRawY() - initialTouchY);
                    windowManager.updateViewLayout(box, params);
                    return true;
                }
                return false;
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
        running = false;
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
