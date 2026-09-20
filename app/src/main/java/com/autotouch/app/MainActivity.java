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
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status;
    private Switch autoLevel, autoQuest, autoChest, autoFruitFarm, autoBuyFruit;
    private EditText fruitInterval, loopDelay;
    private Spinner scriptModule;
    private EditText scriptEditor;
    private final String[] scriptKeys = {
            ScriptStore.LEVEL,
            ScriptStore.QUEST,
            ScriptStore.CHEST,
            ScriptStore.FRUIT_FARM,
            ScriptStore.BUY_FRUIT
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(26));
        root.setBackgroundColor(Color.rgb(247, 242, 250));
        scroll.addView(root);

        root.addView(text("AutoTouch", 31, true));
        root.addView(text("Feature toggles + Lua-like touch scripts", 15, false));

        status = text("", 14, false);
        status.setPadding(0, dp(14), 0, dp(10));
        root.addView(status);

        root.addView(section("Permissions"));
        root.addView(button("Allow floating controller", v -> openOverlaySettings()));
        root.addView(button("Enable gesture accessibility service", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));

        root.addView(section("Automation"));
        AutomationConfig config = AutomationConfig.load(this);

        autoLevel = toggle("Auto Level", "Runs the Auto Level script", config.autoLevel);
        autoQuest = toggle("Auto Quest", "Runs the Auto Quest script", config.autoQuest);
        autoChest = toggle("Auto Money / Chest Farm", "Runs the Money / Chest script", config.autoChest);
        autoFruitFarm = toggle("Auto Fruit Farm", "Runs the Fruit Farm script", config.autoFruitFarm);
        autoBuyFruit = toggle("Auto Buy Fruit", "Runs its script only when the timer is due", config.autoBuyFruit);

        root.addView(autoLevel);
        root.addView(autoQuest);
        root.addView(autoChest);
        root.addView(autoFruitFarm);
        root.addView(autoBuyFruit);

        root.addView(section("Timers"));
        fruitInterval = labeledNumberField("Buy fruit every (minutes)", config.buyFruitIntervalMinutes);
        loopDelay = labeledNumberField("Delay between module cycles (ms)", config.loopDelayMs);

        root.addView(section("Advanced scripts"));
        root.addView(text("These scripts run through Android accessibility. They are not injected into Roblox.", 13, false));

        scriptModule = new Spinner(this);
        scriptModule.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Auto Level", "Auto Quest", "Money / Chest", "Fruit Farm", "Buy Fruit"}));
        root.addView(scriptModule);

        scriptEditor = new EditText(this);
        scriptEditor.setMinLines(10);
        scriptEditor.setGravity(Gravity.TOP | Gravity.START);
        scriptEditor.setTextSize(14);
        scriptEditor.setTypeface(android.graphics.Typeface.MONOSPACE);
        scriptEditor.setHorizontallyScrolling(true);
        root.addView(scriptEditor);

        root.addView(text("Commands: tap(x,y), tap_pct(x,y), swipe(...), swipe_pct(...), wait(ms), repeat(n) ... end", 12, false));

        LinearLayout scriptButtons = new LinearLayout(this);
        scriptButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button load = button("LOAD", v -> loadSelectedScript());
        Button saveScript = button("SAVE SCRIPT", v -> saveSelectedScript());
        scriptButtons.addView(load);
        scriptButtons.addView(saveScript);
        root.addView(scriptButtons);

        root.addView(section("Controller"));
        root.addView(button("SAVE SETTINGS", v -> saveConfig()));
        root.addView(button("START FLOATING CONTROLLER", v -> startController()));
        root.addView(button("STOP", v -> stopController()));

        setContentView(scroll);
        loadSelectedScript();

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 20);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void loadSelectedScript() {
        scriptEditor.setText(ScriptStore.load(this, scriptKeys[scriptModule.getSelectedItemPosition()]));
    }

    private void saveSelectedScript() {
        ScriptStore.save(this, scriptKeys[scriptModule.getSelectedItemPosition()], scriptEditor.getText().toString());
        Toast.makeText(this, "Script saved", Toast.LENGTH_SHORT).show();
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean access = isAccessibilityEnabled();
        status.setText("Overlay: " + (overlay ? "ready" : "permission needed") +
                "  •  Accessibility: " + (access ? "ready" : "permission needed"));
        status.setTextColor((overlay && access)
                ? Color.rgb(20, 110, 55)
                : Color.rgb(160, 70, 20));
    }

    private void openOverlaySettings() {
        startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        ));
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        ComponentName component = new ComponentName(this, AutoTouchAccessibilityService.class);
        if (enabled == null) return false;
        for (String value : enabled.split(":")) {
            if (component.equals(ComponentName.unflattenFromString(value))) return true;
        }
        return false;
    }

    private void saveConfig() {
        try {
            AutomationConfig config = new AutomationConfig(
                    autoLevel.isChecked(),
                    autoQuest.isChecked(),
                    autoChest.isChecked(),
                    autoFruitFarm.isChecked(),
                    autoBuyFruit.isChecked(),
                    positiveInt(fruitInterval),
                    positiveInt(loopDelay)
            );
            config.save(this);
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Timers must be whole numbers greater than 0", Toast.LENGTH_LONG).show();
        }
    }

    private void startController() {
        saveConfig();
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings();
            return;
        }
        if (!isAccessibilityEnabled()) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
    }

    private void stopController() {
        stopService(new Intent(this, OverlayService.class));
    }

    private Switch toggle(String title, String subtitle, boolean checked) {
        Switch s = new Switch(this);
        s.setText(title + "\n" + subtitle);
        s.setTextSize(17);
        s.setTextColor(Color.rgb(35, 30, 40));
        s.setChecked(checked);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setPadding(dp(4), dp(10), dp(4), dp(10));
        return s;
    }

    private EditText labeledNumberField(String label, int value) {
        root.addView(text(label, 14, true));
        EditText e = new EditText(this);
        e.setText(String.valueOf(value));
        e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setTextSize(18);
        root.addView(e);
        return e;
    }

    private int positiveInt(EditText field) {
        int value = Integer.parseInt(field.getText().toString().trim());
        if (value <= 0) throw new NumberFormatException();
        return value;
    }

    private TextView section(String value) {
        TextView v = text(value, 21, true);
        v.setPadding(0, dp(22), 0, dp(7));
        return v;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(35, 30, 40));
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(5), 0, dp(5));
        return v;
    }

    private Button button(String value, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(value);
        b.setOnClickListener(listener);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
