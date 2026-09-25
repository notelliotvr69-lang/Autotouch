package com.questlink.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int WEB_PORT = 8787;
    private static final int DISCOVERY_PORT = 8788;
    private static final String VERSION = "0.8.0";

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayList<VrApp> vrApps = new ArrayList<>();

    private SharedPreferences prefs;
    private TextView status;
    private TextView pcStatus;
    private TextView pcvrStatus;
    private TextView libraryStatus;
    private EditText pairCode;
    private LinearLayout libraryContainer;

    private String deviceId;
    private String pcHost = "";
    private int pcPort = WEB_PORT;
    private String deviceToken = "";
    private volatile boolean heartbeat = false;
    private volatile boolean discoveryDone = false;

    private static class VrApp {
        final String label;
        final String packageName;
        final String activityName;

        VrApp(String label, String packageName, String activityName) {
            this.label = label;
            this.packageName = packageName;
            this.activityName = activityName;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("questlink", MODE_PRIVATE);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (deviceId == null || deviceId.isEmpty()) deviceId = UUID.randomUUID().toString();

        pcHost = prefs.getString("pcHost", "");
        pcPort = prefs.getInt("pcPort", WEB_PORT);
        deviceToken = prefs.getString("deviceToken", "");

        setContentView(buildUi());
        refreshVrLibrary();

        if (!pcHost.isEmpty() && !deviceToken.isEmpty()) {
            pcStatus.setText("Saved QuestLink PC");
            startHeartbeat();
            refreshPcvrStatus();
        } else {
            findPc();
        }
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(13, 17, 23));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(22));

        root.addView(text("QuestLink", 30, true));
        TextView ver = text("Quest client v" + VERSION, 13, false);
        ver.setTextColor(Color.rgb(139, 148, 158));
        root.addView(ver);
        root.addView(text("VR MODE launches the Quest copy. PCVR MODE launches the mapped PC copy.", 17, false));

        pcStatus = text("Looking for QuestLink PC...", 18, true);
        status = text("Not paired", 16, false);
        root.addView(pcStatus);
        root.addView(status);

        LinearLayout connection = row();
        Button find = button("FIND PC");
        find.setOnClickListener(v -> findPc());
        connection.addView(find, weighted());
        Button reconnect = button("RECONNECT");
        reconnect.setOnClickListener(v -> {
            if (pcHost.isEmpty()) findPc();
            else {
                startHeartbeat();
                refreshPcvrStatus();
            }
        });
        connection.addView(reconnect, weighted());
        root.addView(connection, matchWrap());

        pairCode = new EditText(this);
        pairCode.setHint("6-digit pairing code from PC");
        pairCode.setTextColor(Color.WHITE);
        pairCode.setHintTextColor(Color.GRAY);
        pairCode.setTextSize(18);
        pairCode.setSingleLine(true);
        pairCode.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(pairCode, matchWrap());

        Button pair = button("PAIR");
        pair.setOnClickListener(v -> pair());
        root.addView(pair, matchWrap());

        root.addView(divider());
        root.addView(text("PCVR CONTROLS", 22, true));

        pcvrStatus = text("Pair with the PC to load PCVR settings.", 14, false);
        pcvrStatus.setTextColor(Color.rgb(139, 148, 158));
        root.addView(pcvrStatus);

        LinearLayout launchRow = row();
        Button launchSteam = button("LAUNCH STEAMVR");
        launchSteam.setOnClickListener(v -> pcvrAction("launch_steamvr", null));
        launchRow.addView(launchSteam, weighted());
        Button refreshStatus = button("REFRESH STATUS");
        refreshStatus.setOnClickListener(v -> refreshPcvrStatus());
        launchRow.addView(refreshStatus, weighted());
        root.addView(launchRow, matchWrap());

        root.addView(text("World scale", 16, true));
        LinearLayout world = row();
        world.addView(presetButton("0.75x", "worldScale", 0.75), weighted());
        world.addView(presetButton("1.00x", "worldScale", 1.00), weighted());
        world.addView(presetButton("1.25x", "worldScale", 1.25), weighted());
        root.addView(world, matchWrap());

        root.addView(text("Render scale", 16, true));
        LinearLayout render = row();
        render.addView(presetButton("75%", "supersampleScale", 0.75), weighted());
        render.addView(presetButton("100%", "supersampleScale", 1.00), weighted());
        render.addView(presetButton("125%", "supersampleScale", 1.25), weighted());
        root.addView(render, matchWrap());

        LinearLayout toggles = row();
        Button smoothing = button("TOGGLE SMOOTHING");
        smoothing.setOnClickListener(v -> pcvrAction("toggle_motion_smoothing", null));
        toggles.addView(smoothing, weighted());
        Button graph = button("TOGGLE PERF GRAPH");
        graph.setOnClickListener(v -> pcvrAction("toggle_perf_graph", null));
        toggles.addView(graph, weighted());
        root.addView(toggles, matchWrap());

        root.addView(divider());

        LinearLayout header = row();
        header.addView(text("VR APP LIBRARY", 22, true), weighted());
        Button refresh = button("REFRESH");
        refresh.setOnClickListener(v -> refreshVrLibrary());
        header.addView(refresh);
        root.addView(header, matchWrap());

        libraryStatus = text("Scanning installed VR apps...", 14, false);
        libraryStatus.setTextColor(Color.rgb(139, 148, 158));
        root.addView(libraryStatus);

        libraryContainer = new LinearLayout(this);
        libraryContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(libraryContainer, matchWrap());

        scroll.addView(root);
        return scroll;
    }

    private Button presetButton(String label, String key, double value) {
        Button b = button(label);
        b.setOnClickListener(v -> {
            try {
                JSONObject extra = new JSONObject();
                extra.put(key, value);
                pcvrAction("settings", extra);
            } catch (Exception e) {
                status.setText("Setting error: " + e.getMessage());
            }
        });
        return b;
    }

    private void findPc() {
        discoveryDone = false;
        pcStatus.setText("Searching for QuestLink PC...");
        io.execute(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.setSoTimeout(1500);
                byte[] request = "QUESTLINK_DISCOVER_V1".getBytes("UTF-8");
                DatagramPacket out = new DatagramPacket(
                    request,
                    request.length,
                    InetAddress.getByName("255.255.255.255"),
                    DISCOVERY_PORT
                );

                for (int attempt = 0; attempt < 4 && !discoveryDone; attempt++) {
                    socket.send(out);
                    byte[] data = new byte[512];
                    DatagramPacket in = new DatagramPacket(data, data.length);
                    try {
                        socket.receive(in);
                        String message = new String(in.getData(), 0, in.getLength(), "UTF-8");
                        if (message.startsWith("QUESTLINK_HERE_V1|")) {
                            String[] parts = message.split("\\|");
                            if (parts.length >= 2) {
                                acceptPc(in.getAddress().getHostAddress(), Integer.parseInt(parts[1]));
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                ui.post(() -> {
                    if (!discoveryDone) {
                        pcStatus.setText("PC not found. Keep QuestLink open and use the same Wi-Fi.");
                    }
                });
            } catch (Exception e) {
                ui.post(() -> pcStatus.setText("Discovery error: " + e.getMessage()));
            } finally {
                if (socket != null) socket.close();
            }
        });
    }

    private synchronized void acceptPc(String host, int port) {
        if (discoveryDone) return;
        discoveryDone = true;
        pcHost = host;
        pcPort = port;
        prefs.edit().putString("pcHost", host).putInt("pcPort", port).apply();

        ui.post(() -> {
            pcStatus.setText("QuestLink PC found automatically");
            status.setText(deviceToken.isEmpty() ? "Enter the pairing code shown on the PC." : "Reconnecting...");
        });

        if (!deviceToken.isEmpty()) {
            startHeartbeat();
            refreshPcvrStatus();
        }
    }

    private void pair() {
        if (pcHost.isEmpty()) {
            status.setText("Find the PC first.");
            findPc();
            return;
        }

        String code = pairCode.getText().toString().trim();
        if (code.length() != 6) {
            status.setText("Enter the 6-digit code shown on the PC.");
            return;
        }

        status.setText("Pairing...");
        io.execute(() -> {
            try {
                String url = baseUrl()
                    + "/api/pair?id=" + Uri.encode(deviceId)
                    + "&name=" + Uri.encode("Quest")
                    + "&code=" + Uri.encode(code);

                JSONObject result = new JSONObject(httpGet(url));
                if (!result.optBoolean("ok", false)) {
                    throw new Exception(result.optString("error", "Pair failed"));
                }

                deviceToken = result.getString("device_token");
                prefs.edit()
                    .putString("pcHost", pcHost)
                    .putInt("pcPort", pcPort)
                    .putString("deviceToken", deviceToken)
                    .apply();

                ui.post(() -> status.setText("Paired"));
                startHeartbeat();
                refreshPcvrStatus();
                uploadLibrary();
            } catch (Exception e) {
                ui.post(() -> status.setText("Pair failed: " + e.getMessage()));
            }
        });
    }

    private synchronized void startHeartbeat() {
        heartbeat = false;
        heartbeat = true;

        new Thread(() -> {
            while (heartbeat) {
                if (pcHost.isEmpty() || deviceToken.isEmpty()) break;

                try {
                    String url = baseUrl()
                        + "/api/heartbeat?id=" + Uri.encode(deviceId)
                        + "&token=" + Uri.encode(deviceToken);

                    JSONObject result = new JSONObject(httpGet(url));
                    if (!result.optBoolean("ok", false)) throw new Exception("Pairing expired");
                    ui.post(() -> status.setText("Connected"));
                } catch (Exception e) {
                    ui.post(() -> status.setText("PC connection unavailable"));
                }

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void refreshPcvrStatus() {
        if (pcHost.isEmpty() || deviceToken.isEmpty()) {
            pcvrStatus.setText("Pair with the PC to load PCVR settings.");
            return;
        }

        pcvrStatus.setText("Loading PCVR status...");
        io.execute(() -> {
            try {
                String url = baseUrl()
                    + "/api/pcvr/status?id=" + Uri.encode(deviceId)
                    + "&token=" + Uri.encode(deviceToken);

                JSONObject result = new JSONObject(httpGet(url));
                if (!result.optBoolean("ok", false)) {
                    throw new Exception(result.optString("error", "Status failed"));
                }

                String backend = result.optString("backend_mode", "PCVR");
                double world = result.optDouble("worldScale", 1.0);
                double render = result.optDouble("supersampleScale", 1.0);
                boolean smoothing = result.optBoolean("motionSmoothing", false);
                boolean graph = result.optBoolean("showPerfGraph", false);

                String line = backend
                    + " • world " + decimal(world) + "x"
                    + " • render " + Math.round(render * 100) + "%"
                    + " • smoothing " + (smoothing ? "on" : "off")
                    + " • graph " + (graph ? "on" : "off");

                ui.post(() -> pcvrStatus.setText(line));
            } catch (Exception e) {
                ui.post(() -> pcvrStatus.setText("PCVR status unavailable: " + e.getMessage()));
            }
        });
    }

    private void pcvrAction(String action, JSONObject extra) {
        if (pcHost.isEmpty() || deviceToken.isEmpty()) {
            status.setText("Pair with the PC first.");
            return;
        }

        status.setText("Sending PCVR command...");
        io.execute(() -> {
            try {
                JSONObject payload = authPayload();
                payload.put("action", action);

                if (extra != null) {
                    java.util.Iterator<String> keys = extra.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        payload.put(key, extra.get(key));
                    }
                }

                JSONObject result = new JSONObject(
                    httpPostJson(baseUrl() + "/api/pcvr/control", payload)
                );

                if (!result.optBoolean("ok", false)) {
                    throw new Exception(result.optString("error", "Command failed"));
                }

                ui.post(() -> {
                    status.setText(result.optString("message", "PCVR command sent"));
                    refreshPcvrStatus();
                });
            } catch (Exception e) {
                ui.post(() -> status.setText("PCVR command failed: " + e.getMessage()));
            }
        });
    }

    private void refreshVrLibrary() {
        io.execute(() -> {
            ArrayList<VrApp> apps = scanVrApps();
            ui.post(() -> {
                vrApps.clear();
                vrApps.addAll(apps);
                renderLibrary();
                libraryStatus.setText(apps.size() + " launchable VR/game apps found");
                if (!deviceToken.isEmpty() && !pcHost.isEmpty()) uploadLibrary();
            });
        });
    }

    private ArrayList<VrApp> scanVrApps() {
        PackageManager pm = getPackageManager();
        Map<String, VrApp> found = new LinkedHashMap<>();

        queryCategory(pm, "com.oculus.intent.category.VR", found);
        queryCategory(pm, "org.khronos.openxr.intent.category.IMMERSIVE_HMD", found);

        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> all = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL);
        for (ResolveInfo info : all) {
            if (info.activityInfo == null) continue;

            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;

            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            if (appInfo == null || appInfo.category != ApplicationInfo.CATEGORY_GAME) continue;

            found.put(
                pkg,
                new VrApp(String.valueOf(info.loadLabel(pm)), pkg, info.activityInfo.name)
            );
        }

        ArrayList<VrApp> apps = new ArrayList<>(found.values());
        apps.sort(Comparator.comparing(a -> a.label.toLowerCase()));
        return apps;
    }

    private void queryCategory(PackageManager pm, String category, Map<String, VrApp> found) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addCategory(category);

        for (ResolveInfo info : pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;

            found.put(
                pkg,
                new VrApp(String.valueOf(info.loadLabel(pm)), pkg, info.activityInfo.name)
            );
        }
    }

    private void renderLibrary() {
        libraryContainer.removeAllViews();

        if (vrApps.isEmpty()) {
            libraryContainer.addView(
                text("No visible installed VR apps found. This scans installed apps, not your cloud Meta purchase library.", 15, false)
            );
            return;
        }

        for (VrApp app : vrApps) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackgroundColor(Color.rgb(22, 27, 34));

            card.addView(text(app.label, 18, true));

            TextView pkg = text(app.packageName, 11, false);
            pkg.setTextColor(Color.rgb(139, 148, 158));
            card.addView(pkg);

            LinearLayout buttons = row();

            Button vrMode = button("VR MODE");
            vrMode.setOnClickListener(v -> launchLocal(app));
            buttons.addView(vrMode, weighted());

            Button pcvrMode = button("PCVR MODE");
            pcvrMode.setOnClickListener(v -> launchPcvr(app));
            buttons.addView(pcvrMode, weighted());

            card.addView(buttons, matchWrap());

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, dp(7), 0, dp(7));
            libraryContainer.addView(card, params);
        }
    }

    private void launchLocal(VrApp app) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(app.packageName);

            if (launch == null) {
                launch = new Intent(Intent.ACTION_MAIN);
                launch.setComponent(new ComponentName(app.packageName, app.activityName));
            }

            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } catch (Exception e) {
            status.setText("Could not launch " + app.label + ": " + e.getMessage());
        }
    }

    private void launchPcvr(VrApp app) {
        if (pcHost.isEmpty() || deviceToken.isEmpty()) {
            status.setText("Pair with the PC before using PCVR MODE.");
            return;
        }

        status.setText("Finding PC version of " + app.label + "...");

        io.execute(() -> {
            try {
                JSONObject payload = authPayload();
                payload.put("package", app.packageName);
                payload.put("label", app.label);

                JSONObject result = new JSONObject(
                    httpPostJson(baseUrl() + "/api/library/launch_pcvr", payload)
                );

                if (!result.optBoolean("ok", false)) {
                    throw new Exception(result.optString("error", "PCVR launch failed"));
                }

                String state = result.optString("status", "");

                if ("launched".equals(state)) {
                    String name = result.optString("name", app.label);
                    String backend = result.optString("backend_mode", "PCVR");
                    ui.post(() -> status.setText("Launching " + name + " via " + backend));
                    return;
                }

                if ("choose".equals(state)) {
                    JSONArray arr = result.optJSONArray("candidates");
                    if (arr == null || arr.length() == 0) {
                        throw new Exception("No Steam games were found on the PC.");
                    }

                    ArrayList<String> names = new ArrayList<>();
                    ArrayList<String> ids = new ArrayList<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject game = arr.getJSONObject(i);
                        names.add(game.getString("name"));
                        ids.add(game.getString("appid"));
                    }

                    ui.post(() -> showPcGameChooser(app, names, ids));
                    return;
                }

                throw new Exception("Unknown PCVR response.");
            } catch (Exception e) {
                ui.post(() -> status.setText("PCVR launch failed: " + e.getMessage()));
            }
        });
    }

    private void showPcGameChooser(VrApp app, ArrayList<String> names, ArrayList<String> ids) {
        new AlertDialog.Builder(this)
            .setTitle("Choose PC version for " + app.label)
            .setItems(
                names.toArray(new String[0]),
                (dialog, which) -> mapAndLaunch(app, ids.get(which))
            )
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void mapAndLaunch(VrApp app, String appid) {
        status.setText("Saving PC version and launching...");

        io.execute(() -> {
            try {
                JSONObject payload = authPayload();
                payload.put("package", app.packageName);
                payload.put("label", app.label);
                payload.put("appid", appid);

                JSONObject result = new JSONObject(
                    httpPostJson(baseUrl() + "/api/library/map_launch", payload)
                );

                if (!result.optBoolean("ok", false)) {
                    throw new Exception(result.optString("error", "PCVR launch failed"));
                }

                String name = result.optString("name", app.label);
                ui.post(() -> status.setText("Launching PCVR: " + name));
            } catch (Exception e) {
                ui.post(() -> status.setText("PCVR launch failed: " + e.getMessage()));
            }
        });
    }

    private void uploadLibrary() {
        if (pcHost.isEmpty() || deviceToken.isEmpty()) return;

        ArrayList<VrApp> snapshot = new ArrayList<>(vrApps);

        io.execute(() -> {
            try {
                JSONArray apps = new JSONArray();

                for (VrApp app : snapshot) {
                    JSONObject item = new JSONObject();
                    item.put("label", app.label);
                    item.put("package", app.packageName);
                    item.put("activity", app.activityName);
                    apps.put(item);
                }

                JSONObject payload = authPayload();
                payload.put("apps", apps);
                httpPostJson(baseUrl() + "/api/library/quest", payload);
            } catch (Exception ignored) {}
        });
    }

    private JSONObject authPayload() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("device_id", deviceId);
        payload.put("token", deviceToken);
        return payload;
    }

    private String baseUrl() {
        return "http://" + pcHost + ":" + pcPort;
    }

    private String httpGet(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(4000);
        connection.setRequestMethod("GET");

        int code = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(code >= 400 ? connection.getErrorStream() : connection.getInputStream())
        );

        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);

        reader.close();
        connection.disconnect();
        return out.toString();
    }

    private String httpPostJson(String urlText, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(6000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        byte[] bytes = payload.toString().getBytes("UTF-8");
        connection.setFixedLengthStreamingMode(bytes.length);

        OutputStream output = connection.getOutputStream();
        output.write(bytes);
        output.flush();
        output.close();

        int code = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(code >= 400 ? connection.getErrorStream() : connection.getInputStream())
        );

        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);

        reader.close();
        connection.disconnect();
        return out.toString();
    }

    private String decimal(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.format(java.util.Locale.US, "%.0f", value);
        }
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(50, 58, 68));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1)
        );
        params.setMargins(0, dp(16), 0, dp(16));
        divider.setLayoutParams(params);
        return divider;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setPadding(0, dp(8), 0, dp(8));
        if (bold) {
            view.setTypeface(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            );
        }
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        return button;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        );
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(7), 0, dp(7));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        heartbeat = false;
        io.shutdownNow();
        super.onDestroy();
    }
}
