package com.vrflex.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private EditText host, pairPort, pairingCode, connectPort;
    private TextView status, details;
    private Button pairButton, connectButton, diagnosticButton, disconnectButton;
    private final int bg=Color.rgb(8,8,8), panel=Color.rgb(23,23,23), orange=Color.rgb(255,140,0), white=Color.rgb(245,245,245), muted=Color.rgb(180,180,180);
    private AdbSession adb;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,28,24,28);
        root.setBackgroundColor(bg);
        scroll.addView(root);

        TextView title=new TextView(this); title.setText("VRFLEX MOBILE"); title.setTextSize(27); title.setTextColor(orange); title.setTypeface(null,Typeface.BOLD); root.addView(title);
        TextView subtitle=new TextView(this); subtitle.setText("QUEST ADB • WIRELESS DEBUGGING"); subtitle.setTextColor(muted); subtitle.setTextSize(12); subtitle.setPadding(0,5,0,18); root.addView(subtitle);
        addPhoneHostCheck(root);
        TextView help=new TextView(this); help.setText("Connect your phone and Quest to the same Wi-Fi network. On Quest, open Settings → System → Developer → Wireless Debugging."); help.setTextColor(white); help.setTextSize(15); help.setPadding(0,0,0,16); root.addView(help);

        host = field("Quest IP address", "quest_ip"); root.addView(host);
        pairPort = field("Pairing port (shown on Pair device screen)", null); root.addView(pairPort);
        pairingCode = field("6-digit pairing code", null); pairingCode.setInputType(2); root.addView(pairingCode);
        pairButton = button("PAIR WITH QUEST"); addButton(root,pairButton); pairButton.setOnClickListener(v -> pairQuest());

        TextView divider = new TextView(this); divider.setText("AFTER PAIRING"); divider.setTextColor(orange); divider.setTypeface(null,Typeface.BOLD); divider.setPadding(0,22,0,8); root.addView(divider);
        TextView connectHelp = new TextView(this); connectHelp.setText("Return to the main Wireless Debugging screen and enter its separate connection port below."); connectHelp.setTextColor(muted); connectHelp.setTextSize(13); root.addView(connectHelp);
        connectPort = field("Connection port (main Wireless Debugging screen)", null); root.addView(connectPort);
        connectButton = button("CONNECT TO QUEST"); addButton(root,connectButton); connectButton.setOnClickListener(v -> connectQuest());

        status=new TextView(this); status.setText("Not connected"); status.setTextColor(orange); status.setTextSize(17); status.setPadding(0,22,0,6); root.addView(status);
        details=new TextView(this); details.setText("ADB: not paired\nDevice diagnostics: unavailable\nUSB: not required for wireless pairing"); details.setTextColor(muted); details.setTextSize(13); root.addView(details);
        diagnosticButton=button("RUN READ-ONLY DIAGNOSTICS"); addButton(root,diagnosticButton); diagnosticButton.setEnabled(false); diagnosticButton.setOnClickListener(v -> runDiagnostics());
        disconnectButton=button("DISCONNECT"); addButton(root,disconnectButton); disconnectButton.setEnabled(false); disconnectButton.setOnClickListener(v -> disconnect());

        TextView safety=new TextView(this); safety.setText("This build pairs with the Quest using Android Wireless Debugging and only runs read-only device-property checks. It does not root the headset, edit firmware, change system settings, or add a Universal Menu control. Pairing code is used only for the pairing attempt and is not saved."); safety.setTextColor(muted); safety.setTextSize(12); safety.setPadding(0,22,0,0); root.addView(safety);
        setContentView(scroll);
        host.setText(getPreferences(0).getString("quest_ip",""));
    }


    private void addPhoneHostCheck(LinearLayout root) {
        TextView heading = new TextView(this);
        heading.setText("PHONE XR HOST CHECK");
        heading.setTextColor(orange);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextSize(15);
        heading.setPadding(0, 18, 0, 6);
        root.addView(heading);

        StringBuilder report = new StringBuilder();
        report.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ")
              .append(android.os.Build.MODEL).append("\n");
        report.append("Android: ").append(android.os.Build.VERSION.RELEASE)
              .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        report.append("CPU ABI: ");
        String[] abis = android.os.Build.SUPPORTED_ABIS;
        for (int i = 0; i < abis.length; i++) {
            if (i > 0) report.append(", ");
            report.append(abis[i]);
        }
        report.append("\n");

        boolean vulkan = false;
        boolean vulkanVersion = false;
        for (android.content.pm.FeatureInfo feature : getPackageManager().getSystemAvailableFeatures()) {
            if (feature == null || feature.name == null) continue;
            if (feature.name.equals("android.hardware.vulkan.level")) vulkan = true;
            if (feature.name.equals("android.hardware.vulkan.version")) vulkanVersion = true;
        }
        report.append("Vulkan hardware feature: ").append(vulkan ? "present" : "not reported").append("\n");
        report.append("Vulkan version feature: ").append(vulkanVersion ? "present" : "not reported").append("\n");

        boolean xrBroker = false;
        try {
            xrBroker = getPackageManager().resolveContentProvider("org.khronos.openxr.runtime_broker", 0) != null
                    || getPackageManager().resolveContentProvider("org.khronos.openxr.system_runtime_broker", 0) != null;
        } catch (Exception ignored) {}
        report.append("OpenXR runtime broker: ").append(xrBroker ? "provider found" : "not found").append("\n\n");
        if (xrBroker) {
            report.append("A broker was detected, but this alone does not prove Quest-game compatibility. Runtime extensions and headset tracking still need testing.");
        } else {
            report.append("No Android OpenXR runtime broker is exposed. Quest games that require Meta's OpenXR runtime cannot run as VR games on this phone just by installing their APKs. Vulkan support alone cannot replace the Quest runtime.");
        }

        TextView result = new TextView(this);
        result.setText(report.toString());
        result.setTextColor(white);
        result.setTextSize(13);
        result.setPadding(14, 14, 14, 14);
        result.setBackground(round(panel, orange));
        root.addView(result, new LinearLayout.LayoutParams(-1, -2));
    }

    private EditText field(String hint, String preference) {
        EditText e=new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setHintTextColor(muted); e.setTextColor(white); e.setTextSize(14); e.setPadding(14,10,14,10); e.setBackground(round(panel,orange));
        if (preference != null) e.setTag(preference);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,54); p.topMargin=8; p.bottomMargin=4; e.setLayoutParams(p); return e;
    }

    private Button button(String text) {
        Button b=new Button(this); b.setText(text); b.setTextColor(Color.BLACK); b.setAllCaps(false); b.setTypeface(null,Typeface.BOLD); b.setBackground(round(orange,orange)); return b;
    }

    private void addButton(LinearLayout root, Button b) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,52); p.topMargin=10; root.addView(b,p);
    }

    private GradientDrawable round(int color,int stroke) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(14); d.setStroke(2,stroke); return d; }

    private void pairQuest() {
        String address=host.getText().toString().trim();
        String portText=pairPort.getText().toString().trim();
        String code=pairingCode.getText().toString().trim();
        if (address.isEmpty() || !validPort(portText) || !code.matches("\\d{6}")) {
            status.setText("Enter the Quest IP, pairing port, and six-digit code."); return;
        }
        getPreferences(0).edit().putString("quest_ip",address).apply();
        pairButton.setEnabled(false); connectButton.setEnabled(false); status.setText("Pairing…");
        io.execute(() -> {
            String result;
            try {
                adb=AdbSession.get(this);
                boolean ok=adb.pair(address,Integer.parseInt(portText),code);
                result=ok ? "PAIRING SUCCESSFUL ✓" : "Pairing was not accepted.";
            } catch(Exception e) { result="Pairing failed: "+safeMessage(e); }
            final String message=result;
            runOnUiThread(() -> { status.setText(message); pairButton.setEnabled(true); connectButton.setEnabled(true); pairingCode.setText(""); });
        });
    }

    private void connectQuest() {
        String address=host.getText().toString().trim(), portText=connectPort.getText().toString().trim();
        if(address.isEmpty() || !validPort(portText)) { status.setText("Enter the Quest IP and connection port."); return; }
        getPreferences(0).edit().putString("quest_ip",address).apply();
        connectButton.setEnabled(false); status.setText("Connecting to ADB…");
        io.execute(() -> {
            String result;
            try {
                adb=AdbSession.get(this);
                boolean ok=adb.connect(address,Integer.parseInt(portText));
                result=ok ? "ADB CONNECTED ✓" : "ADB did not establish a connection.";
            } catch(Exception e) { result="Connection failed: "+safeMessage(e); }
            final String message=result;
            runOnUiThread(() -> { status.setText(message); connectButton.setEnabled(true); diagnosticButton.setEnabled(message.startsWith("ADB CONNECTED")); disconnectButton.setEnabled(message.startsWith("ADB CONNECTED")); details.setText(message.startsWith("ADB CONNECTED") ? "ADB transport: TLS wireless debugging\nReady for read-only diagnostics." : "ADB: not connected"); });
        });
    }

    private void runDiagnostics() {
        diagnosticButton.setEnabled(false); status.setText("Reading Quest properties…");
        io.execute(() -> {
            String result;
            try { result=adb.diagnostics(); if(result.isEmpty()) result="No device properties returned."; }
            catch(Exception e) { result="Diagnostics failed: "+safeMessage(e); }
            final String output=result;
            runOnUiThread(() -> { details.setText("READ-ONLY QUEST DIAGNOSTICS\n"+output); status.setText("Diagnostics complete"); diagnosticButton.setEnabled(true); });
        });
    }

    private void disconnect() {
        disconnectButton.setEnabled(false); diagnosticButton.setEnabled(false);
        io.execute(() -> {
            try { if(adb!=null) adb.disconnect(); } catch(Exception ignored) {}
            runOnUiThread(() -> { status.setText("Disconnected"); details.setText("ADB: disconnected"); });
        });
    }

    private boolean validPort(String value) {
        try { int p=Integer.parseInt(value); return p>0 && p<=65535; } catch(Exception e) { return false; }
    }

    private String safeMessage(Exception e) {
        String m=e.getMessage(); if(m==null || m.trim().isEmpty()) return e.getClass().getSimpleName();
        return m.length()>150 ? m.substring(0,150) : m;
    }

    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }
}
