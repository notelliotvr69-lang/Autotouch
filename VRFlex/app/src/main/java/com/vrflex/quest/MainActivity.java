package com.vrflex.quest;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout content;
    private int activeTab = 0;
    private final int bg = Color.rgb(10, 12, 20);
    private final int panel = Color.rgb(24, 27, 40);
    private final int accent = Color.rgb(125, 92, 255);
    private final int text = Color.rgb(245, 246, 255);
    private final int muted = Color.rgb(164, 171, 194);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("vrflex", MODE_PRIVATE);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        buildShell();
        showTab(0);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(radius);
        return d;
    }

    private TextView label(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextColor(text); t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 22, 28, 22);
        root.setBackgroundColor(bg);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("VRFLEX", 25, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView version = label("QUEST 3  •  v0.1.0", 12, true);
        version.setTextColor(muted); header.addView(version);
        root.addView(header);
        TextView subtitle = label("A safe, standalone Quest utility", 14, false);
        subtitle.setTextColor(muted);
        root.addView(subtitle);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(0, 18, 0, 14);
        String[] names = {"Movement", "Arms", "General"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            Button b = new Button(this);
            b.setText(names[i]); b.setAllCaps(false); b.setTextColor(text);
            b.setBackground(shape(panel, 18));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 54, 1);
            p.setMargins(0, 0, 8, 0);
            tabs.addView(b, p);
            b.setOnClickListener(v -> { activeTab = index; showTab(index); });
        }
        root.addView(tabs);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView foot = label("Prototype build • App-local features only • No system or Guardian changes", 11, false);
        foot.setTextColor(muted); foot.setPadding(0, 14, 0, 0);
        root.addView(foot);
        setContentView(root);
    }

    private void card(String title, String description) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setPadding(20, 18, 20, 18);
        box.setBackground(shape(panel, 22));
        TextView h = label(title, 18, true);
        TextView d = label(description, 13, false); d.setTextColor(muted);
        box.addView(h); box.addView(d);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, 14); content.addView(box, p);
    }

    private void addToggle(String title, String desc, String key, boolean initial) {
        card(title, desc);
        Switch sw = new Switch(this); sw.setText("Enabled"); sw.setTextColor(text);
        sw.setChecked(prefs.getBoolean(key, initial));
        sw.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(key, checked).apply());
        content.addView(sw);
        space();
    }

    private void addSlider(String title, String key, int min, int max, int initial, String unit) {
        card(title, "Saved setting preview. Does not currently alter tracking in other apps.");
        int value = prefs.getInt(key, initial);
        TextView current = label(value + unit, 16, true);
        content.addView(current);
        SeekBar seek = new SeekBar(this); seek.setMax(max - min); seek.setProgress(value - min);
        content.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int progress, boolean user) {
                int v = progress + min; current.setText(v + unit);
                if (user) prefs.edit().putInt(key, v).apply();
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        space();
    }

    private void space() {
        View v = new View(this);
        content.addView(v, new LinearLayout.LayoutParams(1, 12));
    }

    private void showTab(int tab) {
        content.removeAllViews();
        if (tab == 0) {
            card("Movement", "Configure a future app-local reference-space offset. Guardian boundaries are never modified.");
            addToggle("Movement offset", "Master switch for the app-local movement feature.", "move_enabled", false);
            addSlider("Offset amount", "move_offset", -100, 100, 0, " cm");
            addSlider("Adjustment speed", "move_speed", 1, 100, 25, "%");
            card("Status", "This first build saves your settings only. OpenXR pose adjustment is not implemented yet.");
        } else if (tab == 1) {
            card("Arms", "Controller pose adjustments are planned for supported app-local OpenXR sessions.");
            addSlider("Left arm length", "left_arm", -50, 50, 0, "%");
            addSlider("Right arm length", "right_arm", -50, 50, 0, "%");
            card("Compatibility", "A normal Quest app cannot rewrite controller poses inside unrelated apps. No cross-app tracking hooks are used.");
        } else {
            card("General", "Preferences are stored locally on this headset.");
            addToggle("Sitting mode", "Saved preference for a future supported seated reference-space mode.", "sitting", false);
            Button reset = new Button(this); reset.setText("Reset all settings"); reset.setAllCaps(false);
            reset.setOnClickListener(v -> { prefs.edit().clear().apply(); showTab(2); });
            content.addView(reset);
            card("Safety", "VRFlex does not require root, bootloader unlocking, firmware flashing, or Guardian changes.");
        }
    }
}
