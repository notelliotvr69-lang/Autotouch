package com.autotouch.app;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status;
    private EditText name, x1, y1, x2, y2, duration, interval, repeats;
    private Spinner type;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(20));
        root.setBackgroundColor(Color.rgb(247, 242, 250));

        TextView title = text("AutoTouch", 30); root.addView(title);
        root.addView(text("Automate only your own routine. You can stop at any time from the floating controller.", 15));
        status = text("", 14); status.setPadding(0, dp(14), 0, dp(8)); root.addView(status);
        Button overlayPermission = button("1. Allow floating controller", v -> openOverlaySettings());
        Button accessibilityPermission = button("2. Enable gesture accessibility service", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(overlayPermission); root.addView(accessibilityPermission);

        root.addView(text("Routine profile", 21));
        Routine saved = Routine.load(this); Routine.Step s = saved.steps[0];
        name = field("Profile name", saved.name); root.addView(name);
        type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Tap", "Swipe"}));
        type.setSelection(s.kind() == Routine.Kind.SWIPE ? 1 : 0); root.addView(type);
        x1 = field("Start X", s.x1()); y1 = field("Start Y", s.y1());
        x2 = field("End X (swipe)", s.x2()); y2 = field("End Y (swipe)", s.y2());
        duration = field("Swipe duration (ms)", s.durationMs());
        interval = field("Timer between actions (ms)", s.delayMs());
        repeats = field("Number of repetitions", saved.repetitions);
        for (EditText f : new EditText[]{x1,y1,x2,y2,duration,interval,repeats}) root.addView(f);

        Button save = button("Save profile", v -> saveProfile());
        Button start = button("Start floating controller", v -> startController());
        Button stop = button("Stop", v -> stopController());
        root.addView(save); root.addView(start); root.addView(stop);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(root); setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
    }

    @Override protected void onResume() { super.onResume(); refreshStatus(); }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this), access = isAccessibilityEnabled();
        status.setText("Overlay: " + (overlay ? "ready" : "permission needed") +
                "  •  Accessibility: " + (access ? "ready" : "permission needed"));
        status.setTextColor((overlay && access) ? Color.rgb(20, 110, 55) : Color.rgb(160, 70, 20));
    }

    private void openOverlaySettings() {
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName component = new ComponentName(this, AutoTouchAccessibilityService.class);
        if (enabled == null) return false;
        for (String value : enabled.split(":")) if (component.equals(ComponentName.unflattenFromString(value))) return true;
        return false;
    }

    private void saveProfile() {
        try {
            Routine.Step step = new Routine.Step(type.getSelectedItemPosition() == 1 ? Routine.Kind.SWIPE : Routine.Kind.TAP,
                    integer(x1), integer(y1), integer(x2), integer(y2), integer(duration), integer(interval));
            new Routine(name.getText().toString().trim().isEmpty() ? "My routine" : name.getText().toString().trim(),
                    new Routine.Step[]{step}, Math.max(1, integer(repeats))).save(this);
            Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) { Toast.makeText(this, "Enter whole numbers in every timer and coordinate", Toast.LENGTH_LONG).show(); }
    }

    private void startController() {
        saveProfile();
        if (!Settings.canDrawOverlays(this)) { openOverlaySettings(); return; }
        if (!isAccessibilityEnabled()) { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return; }
        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
    }
    private void stopController() { stopService(new Intent(this, OverlayService.class)); }
    private int integer(EditText field) { return Integer.parseInt(field.getText().toString()); }
    private EditText field(String hint, Object value) { EditText e = new EditText(this); e.setHint(hint); e.setText(String.valueOf(value)); e.setSingleLine(); return e; }
    private TextView text(String value, int sp) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(Color.rgb(35, 30, 40)); v.setPadding(0, dp(7), 0, dp(7)); return v; }
    private Button button(String value, View.OnClickListener listener) { Button b = new Button(this); b.setText(value); b.setOnClickListener(listener); return b; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
