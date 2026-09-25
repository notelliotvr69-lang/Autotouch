package com.questtools.questlink;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
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
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());

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

        TextView title = text("QuestLink", 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = text("Native PCVR launcher — IP pairing, no Python.", 17);
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
                "Only installed Steam games reported as VR Only or VR Supported are listed.",
                14);
        filterNote.setTextColor(Color.GRAY);
        root.addView(filterNote);

        gamesBox = new LinearLayout(this);
        gamesBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(gamesBox);

        scroll.addView(root);
        setContentView(scroll);
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
            loadGames();
        });
    }

    private void loadGames() {
        status.setText("Scanning installed PCVR games...");
        request("list_games", null, result -> {
            JSONArray games = result.optJSONArray("games");
            gamesBox.removeAllViews();

            if (games == null || games.length() == 0) {
                gamesBox.addView(text("No verified PCVR Steam games found.", 17));
                status.setText("Connected — no PCVR games found.");
                return;
            }

            for (int i = 0; i < games.length(); i++) {
                JSONObject game = games.optJSONObject(i);
                if (game == null) continue;

                String appid = game.optString("AppId", game.optString("appId", ""));
                String name = game.optString("Name", game.optString("name", "Steam VR game"));
                String support = game.optString("VrSupport", game.optString("vrSupport", "VR"));

                Button button = new Button(this);
                button.setAllCaps(false);
                button.setText(name + "  •  " + support);
                button.setOnClickListener(v -> launch(appid, name));
                gamesBox.addView(button);
            }

            status.setText("Connected — " + games.length() + " PCVR game(s).");
        });
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
                socket.setSoTimeout(15000);

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
                });
            }
        }).start();
    }
}
