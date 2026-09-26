package com.questtools.questlink;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PORT = 47990;

    private EditText ipBox;
    private TextView status;
    private LinearLayout gamesBox;
    private Button refreshButton;
    private Button openSteamVrButton;
    private Button applySettingsButton;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private String selectedSteamAppId = "";
    private String selectedSteamName = "";
    private TextView selectedGameLabel;
    private TextView worldScaleValue;
    private TextView renderScaleValue;
    private SeekBar worldScale;
    private SeekBar renderScale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("questlink", MODE_PRIVATE);
        buildUi();
    }

    private TextView text(String value, float size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(8, 8, 8, 8);
        return t;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(38, 28, 38, 40);
        root.setBackgroundColor(Color.rgb(15, 17, 22));

        TextView title = text("QuestLink V7.7", 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = text("Quest + PCVR control panel — IP pairing, no Python.", 17);
        subtitle.setTextColor(Color.LTGRAY);
        root.addView(subtitle);

        ipBox = new EditText(this);
        ipBox.setText(prefs.getString("pc_ip", ""));
        ipBox.setHint("PC IP — example 192.168.1.42");
        ipBox.setTextColor(Color.WHITE);
        ipBox.setHintTextColor(Color.GRAY);
        ipBox.setSingleLine(true);
        ipBox.setTextSize(20);
        root.addView(ipBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button connect = new Button(this);
        connect.setText("CONNECT TO PC");
        connect.setOnClickListener(v -> connectAndLoad());
        root.addView(connect);

        refreshButton = new Button(this);
        refreshButton.setText("REFRESH PCVR GAMES");
        refreshButton.setEnabled(false);
        refreshButton.setOnClickListener(v -> loadGames());
        root.addView(refreshButton);

        status = text("Not connected", 17);
        status.setTextColor(Color.LTGRAY);
        root.addView(status);

        TextView heading = text("PCVR Games", 22);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(heading);

        TextView filterNote = text(
                "Installed Steam PCVR games plus detected Meta Horizon PCVR-library apps are listed. Gorilla Tag also reports BepInEx status.",
                14);
        filterNote.setTextColor(Color.GRAY);
        root.addView(filterNote);

        gamesBox = new LinearLayout(this);
        gamesBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(gamesBox);

        TextView settingsHeading = text("SteamVR Controls", 22);
        settingsHeading.setTypeface(Typeface.DEFAULT_BOLD);
        settingsHeading.setPadding(8, 28, 8, 8);
        root.addView(settingsHeading);

        selectedGameLabel = text("Selected game: none", 16);
        selectedGameLabel.setTextColor(Color.LTGRAY);
        root.addView(selectedGameLabel);

        worldScaleValue = text("World scale: 100%", 16);
        root.addView(worldScaleValue);

        worldScale = new SeekBar(this);
        worldScale.setMin(10);
        worldScale.setMax(1000);
        worldScale.setProgress(100);
        worldScale.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                worldScaleValue.setText("World scale: " + progress + "%");
            }
        });
        root.addView(worldScale);

        renderScaleValue = text("Render scale: 100%", 16);
        root.addView(renderScaleValue);

        renderScale = new SeekBar(this);
        renderScale.setMin(20);
        renderScale.setMax(300);
        renderScale.setProgress(100);
        renderScale.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                renderScaleValue.setText("Render scale: " + progress + "%");
            }
        });
        root.addView(renderScale);

        applySettingsButton = new Button(this);
        applySettingsButton.setText("APPLY TO SELECTED STEAMVR GAME");
        applySettingsButton.setEnabled(false);
        applySettingsButton.setOnClickListener(v -> applySteamVrSettings());
        root.addView(applySettingsButton);

        openSteamVrButton = new Button(this);
        openSteamVrButton.setText("OPEN STEAMVR ON PC");
        openSteamVrButton.setEnabled(false);
        openSteamVrButton.setOnClickListener(v -> openSteamVr());
        root.addView(openSteamVrButton);

        TextView settingsNote = text(
                "World/render scale apply to Steam/OpenVR games. Meta-native PCVR apps do not use these SteamVR per-game settings.",
                14);
        settingsNote.setTextColor(Color.GRAY);
        root.addView(settingsNote);

        scroll.addView(root);
        setContentView(scroll);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    private String ip() {
        return ipBox.getText().toString().trim();
    }

    private void connectAndLoad() {
        if (ip().isEmpty()) {
            status.setText("Enter the PC's local IP.");
            return;
        }

        prefs.edit().putString("pc_ip", ip()).apply();
        status.setText("Connecting...");

        request("ping", null, result -> {
            status.setText("Connected to " + result.optString("pc", "PC")
                    + " — QuestLink PC V" + result.optString("version", "?"));
            refreshButton.setEnabled(true);
            openSteamVrButton.setEnabled(true);
            loadGames();
        });
    }

    private void loadGames() {
        status.setText("Scanning installed PCVR games...");
        request("list_games", null, result -> {
            JSONArray games = result.optJSONArray("games");
            gamesBox.removeAllViews();

            if (games == null || games.length() == 0) {
                gamesBox.addView(text("No PCVR games found.", 17));
                status.setText("Connected — no PCVR games found.");
                return;
            }

            for (int i = 0; i < games.length(); i++) {
                JSONObject game = games.optJSONObject(i);
                if (game == null) continue;

                String appid = game.optString("AppId", game.optString("appId", ""));
                String name = game.optString("Name", game.optString("name", "PCVR game"));
                String support = game.optString("VrSupport", game.optString("vrSupport", "PCVR"));
                String source = game.optString("Source", game.optString("source", "Steam"));
                boolean bepin = game.optBoolean(
                        "BepInExInstalled",
                        game.optBoolean("bepInExInstalled", false));

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(8, 12, 8, 18);

                TextView gameTitle = text(name + "  •  " + support + "  •  " + source, 17);
                gameTitle.setTypeface(Typeface.DEFAULT_BOLD);
                card.addView(gameTitle);

                if (name.equalsIgnoreCase("Gorilla Tag")) {
                    TextView bep = text(
                            bepin ? "BepInEx: detected ✓" : "BepInEx: not detected",
                            14);
                    bep.setTextColor(bepin ? Color.rgb(120, 220, 140) : Color.LTGRAY);
                    card.addView(bep);
                }

                Button launch = new Button(this);
                launch.setAllCaps(false);
                launch.setText("Launch " + name);
                launch.setOnClickListener(v -> launch(appid, name));
                card.addView(launch);

                if (source.equalsIgnoreCase("Steam")) {
                    Button select = new Button(this);
                    select.setAllCaps(false);
                    select.setText("Use for SteamVR sliders");
                    select.setOnClickListener(v -> selectForSettings(appid, name));
                    card.addView(select);
                }

                gamesBox.addView(card);
            }

            status.setText("Connected — " + games.length() + " PCVR game(s).");
        });
    }

    private void selectForSettings(String appid, String name) {
        selectedSteamAppId = appid;
        selectedSteamName = name;
        selectedGameLabel.setText("Selected game: " + name);
        applySettingsButton.setEnabled(true);
        status.setText(name + " selected for SteamVR controls.");
    }

    private void applySteamVrSettings() {
        if (selectedSteamAppId.isEmpty()) {
            status.setText("Select a Steam game first.");
            return;
        }

        try {
            JSONObject extra = new JSONObject();
            extra.put("appid", selectedSteamAppId);
            extra.put("world_scale", worldScale.getProgress());
            extra.put("render_scale", renderScale.getProgress());

            status.setText("Applying SteamVR settings to " + selectedSteamName + "...");
            request("set_steamvr_settings", extra, result ->
                    status.setText(result.optString("message", "SteamVR settings saved.")));
        } catch (Exception ex) {
            status.setText("Settings error: " + ex.getMessage());
        }
    }

    private void openSteamVr() {
        status.setText("Starting SteamVR...");
        request("open_steamvr", null, result ->
                status.setText(result.optString("message", "SteamVR launch requested.")));
    }

    private void launch(String appid, String name) {
        try {
            JSONObject extra = new JSONObject();
            extra.put("appid", appid);
            status.setText("Launching " + name + "...");
            request("launch_game", extra, result ->
                    status.setText(result.optString("message", "Launch command sent.")));
        } catch (Exception ex) {
            status.setText(ex.getMessage());
        }
    }

    private interface Success {
        void run(JSONObject result);
    }

    private void request(String cmd, JSONObject extra, Success success) {
        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip(), PORT), 4000);
                socket.setSoTimeout(45000);

                JSONObject req = new JSONObject();
                req.put("cmd", cmd);

                if (extra != null) {
                    Iterator<String> keys = extra.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        req.put(key, extra.get(key));
                    }
                }

                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream()));
                out.write(req.toString());
                out.write("\n");
                out.flush();

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                String line = in.readLine();
                JSONObject result = new JSONObject(line == null ? "{}" : line);

                ui.post(() -> {
                    if (result.optBoolean("ok", false)) {
                        success.run(result);
                    } else {
                        status.setText("QuestLink error: "
                                + result.optString("error", "Unknown error"));
                    }
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    status.setText("Could not connect: " + ex.getMessage());
                    refreshButton.setEnabled(false);
                    openSteamVrButton.setEnabled(false);
                });
            }
        }).start();
    }
}
