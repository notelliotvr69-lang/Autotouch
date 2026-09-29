package com.questtools.questlink;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity implements SensorEventListener {
    private static final int PORT = 47990;
    private static final int STREAM_PORT = 47991;
    private static final String VERSION = "7.10";

    private static final int BG = Color.rgb(9, 11, 17);
    private static final int PANEL = Color.rgb(20, 23, 34);
    private static final int PANEL_2 = Color.rgb(27, 31, 45);
    private static final int LINE = Color.rgb(52, 59, 78);
    private static final int TEXT = Color.rgb(244, 247, 255);
    private static final int MUTED = Color.rgb(164, 172, 193);
    private static final int PURPLE = Color.rgb(126, 87, 255);
    private static final int PURPLE_2 = Color.rgb(95, 61, 226);
    private static final int GREEN = Color.rgb(101, 214, 139);
    private static final int ORANGE = Color.rgb(255, 183, 77);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private EditText ipBox;
    private TextView status;
    private TextView connectionPill;
    private TextView runtimePill;
    private TextView pcName;
    private TextView gtagModSummary;
    private LinearLayout gamesBox;
    private LinearLayout gtagModsBox;
    private Button connectButton;
    private Button refreshButton;
    private Button launchGtagButton;
    private Button streamButton;

    private FrameLayout appShell;
    private FrameLayout streamOverlay;
    private ImageView streamView;
    private TextView streamStatus;

    private volatile boolean streamRunning = false;
    private Socket streamSocket;
    private DataOutputStream streamOut;
    private Thread streamThread;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private long lastPoseSendNs = 0L;

    private String selectedSteamAppId = "";
    private String selectedSteamName = "";
    private TextView selectedGameLabel;
    private TextView worldScaleValue;
    private TextView renderScaleValue;
    private SeekBar worldScale;
    private SeekBar renderScale;
    private Button applySettingsButton;
    private Button openSteamVrButton;

    private boolean connected = false;
    private boolean questLinkRuntimeActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("questlink", MODE_PRIVATE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (rotationSensor == null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        buildUi();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView text(String value, float size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(TEXT);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView muted(String value, float size) {
        TextView t = text(value, size);
        t.setTextColor(MUTED);
        return t;
    }

    private TextView heading(String value, float size) {
        TextView t = text(value, size);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView pill(String value, int color) {
        TextView p = text(value, 13);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextColor(color);
        p.setPadding(dp(13), dp(7), dp(13), dp(7));
        p.setBackground(rounded(Color.argb(38, Color.red(color), Color.green(color), Color.blue(color)), 18, Color.argb(100, Color.red(color), Color.green(color), Color.blue(color))));
        return p;
    }

    private Button actionButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setBackgroundTintList(ColorStateList.valueOf(primary ? PURPLE : PANEL_2));
        return b;
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(22), dp(20), dp(22), dp(20));
        p.setBackground(rounded(PANEL, 18, LINE));
        return p;
    }

    private void addSpace(LinearLayout parent, int h) {
        Space s = new Space(this);
        parent.addView(s, new LinearLayout.LayoutParams(1, dp(h)));
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(34), dp(26), dp(34), dp(42));
        root.setBackgroundColor(BG);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = heading("Q", 24);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(rounded(PURPLE, 18, Color.TRANSPARENT));
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(16), 0, 0, 0);
        TextView title = heading("QuestLink", 30);
        TextView sub = muted("PCVR launcher • Quest client V" + VERSION, 14);
        brand.addView(title);
        brand.addView(sub);
        header.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        connectionPill = pill("DISCONNECTED", ORANGE);
        header.addView(connectionPill);
        root.addView(header);

        addSpace(root, 22);

        // Connection hero
        LinearLayout connectPanel = panel();

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout pcInfo = new LinearLayout(this);
        pcInfo.setOrientation(LinearLayout.VERTICAL);
        pcName = heading("No PC connected", 20);
        TextView pcHint = muted("Connect over your local Wi-Fi/LAN.", 14);
        pcInfo.addView(pcName);
        pcInfo.addView(pcHint);
        topRow.addView(pcInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        runtimePill = pill("RUNTIME UNKNOWN", MUTED);
        topRow.addView(runtimePill);
        connectPanel.addView(topRow);

        addSpace(connectPanel, 18);

        LinearLayout ipRow = new LinearLayout(this);
        ipRow.setOrientation(LinearLayout.HORIZONTAL);
        ipRow.setGravity(Gravity.CENTER_VERTICAL);

        ipBox = new EditText(this);
        ipBox.setText(prefs.getString("pc_ip", ""));
        ipBox.setHint("PC IP — 192.168.x.x");
        ipBox.setTextColor(TEXT);
        ipBox.setHintTextColor(MUTED);
        ipBox.setSingleLine(true);
        ipBox.setTextSize(18);
        ipBox.setPadding(dp(16), 0, dp(16), 0);
        ipBox.setImeOptions(EditorInfo.IME_ACTION_DONE);
        ipBox.setBackground(rounded(PANEL_2, 13, LINE));
        ipRow.addView(ipBox, new LinearLayout.LayoutParams(0, dp(52), 1f));

        Space gap = new Space(this);
        ipRow.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));

        connectButton = actionButton("Connect", true);
        connectButton.setOnClickListener(v -> connectAndLoad());
        ipRow.addView(connectButton, new LinearLayout.LayoutParams(dp(150), dp(52)));

        connectPanel.addView(ipRow);

        addSpace(connectPanel, 14);

        status = muted("Ready to connect.", 14);
        connectPanel.addView(status);

        root.addView(connectPanel, fullWrap());

        addSpace(root, 24);

        // Launcher heading
        LinearLayout libraryHeader = new LinearLayout(this);
        libraryHeader.setOrientation(LinearLayout.HORIZONTAL);
        libraryHeader.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout libraryTitleBox = new LinearLayout(this);
        libraryTitleBox.setOrientation(LinearLayout.VERTICAL);
        libraryTitleBox.addView(heading("PCVR Library", 24));
        libraryTitleBox.addView(muted("Launch PC VR games from inside the headset.", 14));
        libraryHeader.addView(libraryTitleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        refreshButton = actionButton("Refresh", false);
        refreshButton.setEnabled(false);
        refreshButton.setOnClickListener(v -> refreshEverything());
        libraryHeader.addView(refreshButton, new LinearLayout.LayoutParams(dp(135), dp(46)));

        root.addView(libraryHeader);

        addSpace(root, 12);

        HorizontalScrollView gameScroll = new HorizontalScrollView(this);
        gameScroll.setHorizontalScrollBarEnabled(false);
        gamesBox = new LinearLayout(this);
        gamesBox.setOrientation(LinearLayout.HORIZONTAL);
        gamesBox.setPadding(0, dp(2), dp(12), dp(8));
        gameScroll.addView(gamesBox);
        root.addView(gameScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));

        showEmptyLibrary("Connect to your PC to load PCVR games.");

        addSpace(root, 24);

        // Gorilla Tag detail panel
        LinearLayout gtagPanel = panel();

        LinearLayout gtagHead = new LinearLayout(this);
        gtagHead.setOrientation(LinearLayout.HORIZONTAL);
        gtagHead.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout gtagNames = new LinearLayout(this);
        gtagNames.setOrientation(LinearLayout.VERTICAL);
        gtagNames.addView(heading("Gorilla Tag", 23));
        gtagModSummary = muted("Mods: not scanned", 14);
        gtagNames.addView(gtagModSummary);
        gtagHead.addView(gtagNames, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        launchGtagButton = actionButton("Launch + Stream", true);
        launchGtagButton.setEnabled(false);
        launchGtagButton.setOnClickListener(v -> launchAndStreamGtag());
        gtagHead.addView(launchGtagButton, new LinearLayout.LayoutParams(dp(245), dp(52)));

        gtagPanel.addView(gtagHead);
        addSpace(gtagPanel, 14);

        gtagModsBox = new LinearLayout(this);
        gtagModsBox.setOrientation(LinearLayout.VERTICAL);
        gtagModsBox.addView(muted("BepInEx/plugin details will appear here.", 13));
        gtagPanel.addView(gtagModsBox);

        addSpace(gtagPanel, 14);
        streamButton = actionButton("Connect to Running QuestLink Stream", false);
        streamButton.setEnabled(false);
        streamButton.setOnClickListener(v -> startStreamPreviewWithRetry(1));
        gtagPanel.addView(streamButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        root.addView(gtagPanel, fullWrap());

        addSpace(root, 24);

        // Advanced controls
        LinearLayout advanced = panel();
        advanced.addView(heading("Advanced PC Controls", 21));
        advanced.addView(muted("SteamVR world/render scale remains available for OpenVR games.", 13));
        addSpace(advanced, 12);

        selectedGameLabel = muted("Selected Steam game: none", 14);
        advanced.addView(selectedGameLabel);

        worldScaleValue = text("World scale: 100%", 14);
        advanced.addView(worldScaleValue);

        worldScale = new SeekBar(this);
        worldScale.setMin(10);
        worldScale.setMax(1000);
        worldScale.setProgress(100);
        worldScale.setProgressTintList(ColorStateList.valueOf(PURPLE));
        worldScale.setThumbTintList(ColorStateList.valueOf(PURPLE));
        worldScale.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                worldScaleValue.setText("World scale: " + progress + "%");
            }
        });
        advanced.addView(worldScale);

        renderScaleValue = text("Render scale: 100%", 14);
        advanced.addView(renderScaleValue);

        renderScale = new SeekBar(this);
        renderScale.setMin(20);
        renderScale.setMax(300);
        renderScale.setProgress(100);
        renderScale.setProgressTintList(ColorStateList.valueOf(PURPLE));
        renderScale.setThumbTintList(ColorStateList.valueOf(PURPLE));
        renderScale.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                renderScaleValue.setText("Render scale: " + progress + "%");
            }
        });
        advanced.addView(renderScale);

        LinearLayout advancedButtons = new LinearLayout(this);
        advancedButtons.setOrientation(LinearLayout.HORIZONTAL);

        applySettingsButton = actionButton("Apply to selected game", false);
        applySettingsButton.setEnabled(false);
        applySettingsButton.setOnClickListener(v -> applySteamVrSettings());
        advancedButtons.addView(applySettingsButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Space bGap = new Space(this);
        advancedButtons.addView(bGap, new LinearLayout.LayoutParams(dp(12), 1));

        openSteamVrButton = actionButton("Open SteamVR", false);
        openSteamVrButton.setEnabled(false);
        openSteamVrButton.setOnClickListener(v -> openSteamVr());
        advancedButtons.addView(openSteamVrButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        advanced.addView(advancedButtons);
        root.addView(advanced, fullWrap());

        addSpace(root, 18);

        TextView bridge = muted(
                "V7.10 can receive the runtime's first live stereo stream preview and send Quest orientation back to the PC runtime. This is the bridge test build, not final low-latency immersive VR yet.",
                12);
        bridge.setGravity(Gravity.CENTER);
        root.addView(bridge);

        scroll.addView(root);

        appShell = new FrameLayout(this);
        appShell.setBackgroundColor(BG);
        appShell.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        buildStreamOverlay();
        appShell.addView(streamOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(appShell);
    }

    private void buildStreamOverlay() {
        streamOverlay = new FrameLayout(this);
        streamOverlay.setBackgroundColor(Color.BLACK);
        streamOverlay.setVisibility(View.GONE);

        streamView = new ImageView(this);
        streamView.setBackgroundColor(Color.BLACK);
        streamView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        streamOverlay.addView(streamView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(18), dp(14), dp(18), dp(14));
        topBar.setBackgroundColor(Color.argb(180, 0, 0, 0));

        Button exit = actionButton("Exit Stream", false);
        exit.setOnClickListener(v -> stopStreamPreview());
        topBar.addView(exit, new LinearLayout.LayoutParams(dp(150), dp(46)));

        streamStatus = text("Waiting for QuestLink runtime stream...", 14);
        streamStatus.setPadding(dp(16), 0, 0, 0);
        topBar.addView(streamStatus, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        streamOverlay.addView(topBar, topLp);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    private String ip() {
        return ipBox.getText().toString().trim();
    }

    private void setConnectionUi(boolean isConnected, String pc) {
        connected = isConnected;

        if (isConnected) {
            connectionPill.setText("CONNECTED");
            connectionPill.setTextColor(GREEN);
            connectionPill.setBackground(rounded(Color.argb(38, 101, 214, 139), 18, Color.argb(110, 101, 214, 139)));
            pcName.setText(pc);
        } else {
            connectionPill.setText("DISCONNECTED");
            connectionPill.setTextColor(ORANGE);
            connectionPill.setBackground(rounded(Color.argb(38, 255, 183, 77), 18, Color.argb(110, 255, 183, 77)));
            pcName.setText("No PC connected");
            runtimePill.setText("RUNTIME UNKNOWN");
            runtimePill.setTextColor(MUTED);
            questLinkRuntimeActive = false;
        }

        refreshButton.setEnabled(isConnected);
        openSteamVrButton.setEnabled(isConnected);
        launchGtagButton.setEnabled(isConnected && questLinkRuntimeActive);
        if (streamButton != null) streamButton.setEnabled(isConnected && questLinkRuntimeActive);
    }

    private void connectAndLoad() {
        if (ip().isEmpty()) {
            status.setText("Enter the PC's local IP first.");
            return;
        }

        prefs.edit().putString("pc_ip", ip()).apply();
        status.setText("Connecting to QuestLink PC...");
        connectButton.setEnabled(false);

        request("ping", null, result -> {
            connectButton.setEnabled(true);
            String pc = result.optString("pc", "QuestLink PC");
            String v = result.optString("version", "?");
            setConnectionUi(true, pc + " • PC V" + v);
            status.setText("Connected. Loading runtime, games, and GTAG mods...");
            refreshEverything();
        });
    }

    private void refreshEverything() {
        if (!connected) return;
        loadRuntimeStatus();
        loadGtagMods();
        loadGames();
    }

    private void loadRuntimeStatus() {
        request("runtime_status", null, result -> {
            questLinkRuntimeActive = result.optBoolean("active", false);
            runtimePill.setText(questLinkRuntimeActive ? "QUESTLINK RUNTIME ACTIVE" : "OTHER RUNTIME ACTIVE");
            runtimePill.setTextColor(questLinkRuntimeActive ? GREEN : ORANGE);
            runtimePill.setBackground(rounded(
                    questLinkRuntimeActive ? Color.argb(38, 101, 214, 139) : Color.argb(38, 255, 183, 77),
                    18,
                    questLinkRuntimeActive ? Color.argb(110, 101, 214, 139) : Color.argb(110, 255, 183, 77)));
            launchGtagButton.setEnabled(connected && questLinkRuntimeActive);
            if (streamButton != null) streamButton.setEnabled(connected && questLinkRuntimeActive);
        });
    }

    private void showEmptyLibrary(String message) {
        gamesBox.removeAllViews();
        LinearLayout empty = panel();
        empty.setGravity(Gravity.CENTER);
        TextView t = muted(message, 15);
        t.setGravity(Gravity.CENTER);
        empty.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(360), dp(280));
        lp.setMargins(0, 0, dp(14), 0);
        gamesBox.addView(empty, lp);
    }

    private void loadGames() {
        status.setText("Scanning PCVR library...");

        request("list_games", null, result -> {
            JSONArray games = result.optJSONArray("games");
            gamesBox.removeAllViews();

            if (games == null || games.length() == 0) {
                showEmptyLibrary("No PCVR games were detected on the PC.");
                status.setText("Connected — no PCVR games found.");
                return;
            }

            for (int i = 0; i < games.length(); i++) {
                JSONObject game = games.optJSONObject(i);
                if (game == null) continue;
                addGameCard(game);
            }

            status.setText("Connected — " + games.length() + " PCVR game(s) ready.");
        });
    }

    private void addGameCard(JSONObject game) {
        String appid = game.optString("AppId", game.optString("appId", ""));
        String name = game.optString("Name", game.optString("name", "PCVR Game"));
        String support = game.optString("VrSupport", game.optString("vrSupport", "PCVR"));
        String source = game.optString("Source", game.optString("source", "Steam"));
        boolean bepin = game.optBoolean("BepInExInstalled", game.optBoolean("bepInExInstalled", false));

        LinearLayout card = panel();
        card.setGravity(Gravity.TOP);

        TextView sourcePill = pill(source.toUpperCase(Locale.US), source.equalsIgnoreCase("Steam") ? Color.rgb(103, 169, 255) : PURPLE);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.addView(sourcePill, badgeLp);

        addSpace(card, 14);

        TextView nameView = heading(name, 22);
        nameView.setMaxLines(2);
        card.addView(nameView);

        TextView supportView = muted(support, 13);
        supportView.setMaxLines(2);
        card.addView(supportView);

        if (name.equalsIgnoreCase("Gorilla Tag")) {
            addSpace(card, 9);
            TextView b = text(bepin ? "BepInEx detected ✓" : "BepInEx not detected", 13);
            b.setTextColor(bepin ? GREEN : MUTED);
            card.addView(b);
        }

        Space flex = new Space(this);
        card.addView(flex, new LinearLayout.LayoutParams(1, 0, 1f));

        Button launch = actionButton(
                name.equalsIgnoreCase("Gorilla Tag") ? "Launch with QuestLink" : "Launch on PC",
                name.equalsIgnoreCase("Gorilla Tag"));
        launch.setOnClickListener(v -> {
            if (name.equalsIgnoreCase("Gorilla Tag")) {
                launchAndStreamGtag();
            } else {
                launch(appid, name);
            }
        });
        card.addView(launch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        if (source.equalsIgnoreCase("Steam")) {
            addSpace(card, 8);
            Button select = actionButton("SteamVR settings", false);
            select.setOnClickListener(v -> selectForSettings(appid, name));
            card.addView(select, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(310), dp(300));
        lp.setMargins(0, 0, dp(14), 0);
        gamesBox.addView(card, lp);
    }

    private void loadGtagMods() {
        request("gtag_mods", null, result -> {
            boolean found = result.optBoolean("gtag_found", false);
            boolean bepin = result.optBoolean("bepinex", false);
            int plugins = result.optInt("plugin_count", 0);
            int patchers = result.optInt("patcher_count", 0);

            gtagModsBox.removeAllViews();

            if (!found) {
                gtagModSummary.setText("Gorilla Tag not found on the PC.");
                gtagModSummary.setTextColor(ORANGE);
                gtagModsBox.addView(muted("Install/detect Gorilla Tag in Steam first.", 13));
                return;
            }

            if (!bepin) {
                gtagModSummary.setText("BepInEx not detected");
                gtagModSummary.setTextColor(ORANGE);
                gtagModsBox.addView(muted("QuestLink did not find a BepInEx plugins folder.", 13));
                return;
            }

            gtagModSummary.setText("BepInEx detected • " + plugins + " plugin DLL(s) • " + patchers + " patcher DLL(s)");
            gtagModSummary.setTextColor(GREEN);

            JSONArray entries = result.optJSONArray("entries");
            if (entries == null || entries.length() == 0) {
                gtagModsBox.addView(muted("No plugin DLLs found.", 13));
                return;
            }

            int max = Math.min(entries.length(), 10);
            for (int i = 0; i < max; i++) {
                String entry = entries.optString(i, "");
                if (entry.isEmpty()) continue;
                TextView row = text("• " + entry, 13);
                row.setTextColor(Color.rgb(207, 213, 230));
                row.setPadding(0, dp(3), 0, dp(3));
                gtagModsBox.addView(row);
            }

            if (entries.length() > max) {
                gtagModsBox.addView(muted("+" + (entries.length() - max) + " more detected on the PC", 12));
            }
        });
    }

    private void launchAndStreamGtag() {
        try {
            JSONObject extra = new JSONObject();
            extra.put("appid", "1533390");
            status.setText("Launching Gorilla Tag with QuestLink runtime...");

            request("launch_game", extra, result -> {
                status.setText(result.optString("message", "Gorilla Tag launch command sent."));
                loadRuntimeStatus();
                loadGtagMods();

                // The runtime stream server starts when the game creates its OpenXR session.
                // Retry for a while so the user does not need to time the connection manually.
                startStreamPreviewWithRetry(30);
            });
        } catch (Exception ex) {
            status.setText("Launch error: " + ex.getMessage());
        }
    }

    private void startStreamPreviewWithRetry(int maxAttempts) {
        if (streamRunning) return;
        if (ip().isEmpty()) {
            status.setText("Connect to the PC first.");
            return;
        }

        streamRunning = true;
        streamOverlay.setVisibility(View.VISIBLE);
        streamStatus.setText("Waiting for Gorilla Tag / runtime stream on port " + STREAM_PORT + "...");
        streamView.setImageDrawable(null);

        streamThread = new Thread(() -> {
            Exception lastError = null;

            for (int attempt = 1; attempt <= Math.max(1, maxAttempts) && streamRunning; attempt++) {
                try {
                    Socket socket = new Socket();
                    socket.setTcpNoDelay(true);
                    socket.connect(new InetSocketAddress(ip(), STREAM_PORT), 1500);
                    socket.setSoTimeout(15000);

                    streamSocket = socket;
                    streamOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                    ui.post(() -> {
                        streamStatus.setText("LIVE • QuestLink stereo bridge");
                        startPoseTracking();
                    });

                    readStreamLoop(socket);
                    lastError = null;
                    break;
                } catch (Exception ex) {
                    lastError = ex;
                    closeStreamSocketOnly();

                    final int a = attempt;
                    ui.post(() -> streamStatus.setText(
                            "Waiting for runtime stream... attempt " + a + "/" + Math.max(1, maxAttempts)));

                    if (!streamRunning) break;
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }

            if (streamRunning && lastError != null) {
                String message = lastError.getMessage() == null
                        ? lastError.getClass().getSimpleName()
                        : lastError.getMessage();
                ui.post(() -> streamStatus.setText("Stream connection failed: " + message));
            }
        }, "QuestLinkStream");

        streamThread.start();
    }

    private void readStreamLoop(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        byte[] magic = new byte[4];

        while (streamRunning && !socket.isClosed()) {
            in.readFully(magic);
            if (magic[0] != 'Q' || magic[1] != 'L' || magic[2] != 'F' || magic[3] != '1') {
                throw new IOException("Bad QuestLink stream packet");
            }

            int width = in.readInt();
            int height = in.readInt();
            long frameId = in.readLong();
            int payloadSize = in.readInt();

            if (width <= 0 || height <= 0 || width > 4096 || height > 4096 ||
                    payloadSize <= 0 || payloadSize > 16 * 1024 * 1024) {
                throw new IOException("Invalid QuestLink frame");
            }

            byte[] jpeg = new byte[payloadSize];
            in.readFully(jpeg);

            Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            if (bitmap == null) continue;

            ui.post(() -> {
                if (!streamRunning) {
                    bitmap.recycle();
                    return;
                }

                streamView.setImageBitmap(bitmap);
                streamStatus.setText(
                        "LIVE • " + width + "×" + height +
                        " • frame " + frameId +
                        " • Quest pose → PC");
            });
        }
    }

    private void startPoseTracking() {
        if (sensorManager == null || rotationSensor == null) {
            streamStatus.setText(streamStatus.getText() + " • no rotation sensor");
            return;
        }

        sensorManager.unregisterListener(this);
        sensorManager.registerListener(
                this,
                rotationSensor,
                SensorManager.SENSOR_DELAY_GAME);
    }

    private void stopPoseTracking() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!streamRunning || event.sensor != rotationSensor || streamOut == null) return;

        long now = System.nanoTime();
        if (now - lastPoseSendNs < 16_000_000L) return;
        lastPoseSendNs = now;

        float[] q = new float[4];
        try {
            SensorManager.getQuaternionFromVector(q, event.values);
        } catch (Exception ignored) {
            return;
        }

        // Android returns quaternion as [w, x, y, z].
        sendHeadPose(q[1], q[2], q[3], q[0], 0.0f, 1.6f, 0.0f);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void sendHeadPose(
            float x, float y, float z, float w,
            float px, float py, float pz) {

        DataOutputStream out = streamOut;
        if (out == null) return;

        synchronized (this) {
            try {
                out.writeBytes("QLH1");
                out.writeFloat(x);
                out.writeFloat(y);
                out.writeFloat(z);
                out.writeFloat(w);
                out.writeFloat(px);
                out.writeFloat(py);
                out.writeFloat(pz);
                out.flush();
            } catch (IOException ignored) {
            }
        }
    }

    private void closeStreamSocketOnly() {
        DataOutputStream out = streamOut;
        streamOut = null;
        if (out != null) {
            try { out.close(); } catch (Exception ignored) {}
        }

        Socket socket = streamSocket;
        streamSocket = null;
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void stopStreamPreview() {
        streamRunning = false;
        stopPoseTracking();
        closeStreamSocketOnly();

        if (streamOverlay != null) streamOverlay.setVisibility(View.GONE);
        if (status != null) status.setText("QuestLink stream stopped.");
    }

    private void selectForSettings(String appid, String name) {
        selectedSteamAppId = appid;
        selectedSteamName = name;
        selectedGameLabel.setText("Selected Steam game: " + name);
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
        status.setText("Starting SteamVR on PC...");
        request("open_steamvr", null, result ->
                status.setText(result.optString("message", "SteamVR launch requested.")));
    }

    private void launch(String appid, String name) {
        try {
            JSONObject extra = new JSONObject();
            extra.put("appid", appid);
            status.setText("Launching " + name + "...");

            request("launch_game", extra, result -> {
                status.setText(result.optString("message", "Launch command sent."));
                if (name.equalsIgnoreCase("Gorilla Tag")) {
                    loadRuntimeStatus();
                    loadGtagMods();
                }
            });
        } catch (Exception ex) {
            status.setText("Launch error: " + ex.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        stopStreamPreview();
        super.onDestroy();
    }

    private interface Success {
        void run(JSONObject result);
    }

    private void request(String cmd, JSONObject extra, Success success) {
        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip(), PORT), 4500);
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

                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                out.write(req.toString());
                out.write("\n");
                out.flush();

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line = in.readLine();
                JSONObject result = new JSONObject(line == null ? "{}" : line);

                ui.post(() -> {
                    if (result.optBoolean("ok", false)) {
                        success.run(result);
                    } else {
                        String error = result.optString("error", "Unknown QuestLink error");
                        status.setText("QuestLink error: " + error);
                        if (cmd.equals("ping")) {
                            connectButton.setEnabled(true);
                            setConnectionUi(false, "");
                        }
                    }
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    status.setText("Could not reach PC: " + ex.getMessage());
                    connectButton.setEnabled(true);
                    if (cmd.equals("ping")) setConnectionUi(false, "");
                });
            }
        }).start();
    }
}
