package com.vrflex.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private EditText ip;
    private TextView status, details;
    private Button connect;
    private final int bg=Color.rgb(8,8,8), panel=Color.rgb(23,23,23), orange=Color.rgb(255,140,0), white=Color.rgb(245,245,245), muted=Color.rgb(180,180,180);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(26,30,26,24); root.setGravity(Gravity.CENTER_VERTICAL); root.setBackgroundColor(bg);
        TextView title=new TextView(this); title.setText("VRFLEX MOBILE"); title.setTextSize(27); title.setTextColor(orange); title.setTypeface(null,Typeface.BOLD); root.addView(title);
        TextView subtitle=new TextView(this); subtitle.setText("WIRELESS HOST • LOCAL NETWORK"); subtitle.setTextColor(muted); subtitle.setTextSize(12); subtitle.setPadding(0,5,0,20); root.addView(subtitle);
        TextView help=new TextView(this); help.setText("Put your phone and Quest on the same Wi-Fi network. Open VRFlex on Quest, go to General, and enter the Quest IP address below."); help.setTextColor(white); help.setTextSize(15); help.setPadding(0,0,0,18); root.addView(help);
        ip=new EditText(this); ip.setSingleLine(true); ip.setHint("Quest IP address"); ip.setHintTextColor(muted); ip.setTextColor(white); ip.setText(getPreferences(0).getString("quest_ip","")); ip.setPadding(14,12,14,12); ip.setBackground(round(panel,orange)); root.addView(ip);
        connect=new Button(this); connect.setText("CONNECT OVER WI-FI"); connect.setTextColor(Color.BLACK); connect.setAllCaps(false); connect.setTypeface(null,Typeface.BOLD); connect.setBackground(round(orange,orange)); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,56); cp.topMargin=16; root.addView(connect,cp);
        status=new TextView(this); status.setText("Not connected"); status.setTextColor(orange); status.setTextSize(17); status.setPadding(0,22,0,6); root.addView(status);
        details=new TextView(this); details.setText("Transport: local Wi-Fi TCP\nUSB-C: planned fallback\nMedia streaming: not implemented yet"); details.setTextColor(muted); details.setTextSize(13); root.addView(details);
        TextView safety=new TextView(this); safety.setText("Connection setup only. This build does not capture or stream your phone screen yet. No root, ADB, firmware changes, or system-file access."); safety.setTextColor(muted); safety.setTextSize(12); safety.setPadding(0,24,0,0); root.addView(safety);
        setContentView(root); connect.setOnClickListener(v->connectToQuest());
    }

    private GradientDrawable round(int color,int stroke) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(16); d.setStroke(2,stroke); return d; }

    private void connectToQuest() {
        String host=ip.getText().toString().trim(); if(host.isEmpty()) { status.setText("Enter the Quest IP address."); return; }
        getPreferences(0).edit().putString("quest_ip",host).apply(); connect.setEnabled(false); status.setText("Connecting…");
        io.execute(()->{
            String result; String info="Transport: local Wi-Fi TCP\nUSB-C: planned fallback\nMedia streaming: not implemented yet";
            try(Socket s=new Socket()) {
                s.connect(new InetSocketAddress(host,47821),4000); s.setSoTimeout(4000);
                PrintWriter out=new PrintWriter(s.getOutputStream(),true); BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream())); out.println("STATUS");
                String reply=in.readLine();
                if(reply!=null && reply.startsWith("READY|")) { String[] parts=reply.split("\\|",3); result="CONNECTED ✓"; if(parts.length==3) info="Quest profile: "+parts[2]+"\nWi-Fi link: established\nNext: video transport"; }
                else result="Unexpected response from Quest.";
            } catch(Exception e) { result="Connection failed. Check that both devices are on the same Wi-Fi and VRFlex is open on Quest."; }
            final String message=result, extra=info;
            runOnUiThread(()->{ status.setText(message); details.setText(extra); connect.setEnabled(true); });
        });
    }

    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }
}