package com.vrflex.mobile;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import java.net.Socket;\nimport java.net.InetSocketAddress;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class MainActivity extends Activity {
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private EditText ip; private TextView status; private Button connect;
 public void onCreate(Bundle b) {
  super.onCreate(b);
  LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(26,30,26,24); root.setGravity(Gravity.CENTER_VERTICAL); root.setBackgroundColor(0xff0a0c14);
  TextView title=new TextView(this); title.setText("VRFLEX MOBILE"); title.setTextSize(27); title.setTextColor(0xfff5f6ff); title.setTypeface(null,Typeface.BOLD); root.addView(title);
  TextView help=new TextView(this); help.setText("Connect your phone and Quest to the same Wi-Fi. In VRFlex on Quest, open General to find its IP address."); help.setTextColor(0xffa4abc1); help.setPadding(0,14,0,20); root.addView(help);
  ip=new EditText(this); ip.setSingleLine(true); ip.setHint("Quest IP address"); ip.setTextColor(0xfff5f6ff); ip.setText(getPreferences(0).getString("quest_ip","")); root.addView(ip);
  connect=new Button(this); connect.setText("Connect to Quest"); root.addView(connect);
  status=new TextView(this); status.setText("Not connected"); status.setTextColor(0xffa4abc1); status.setTextSize(16); status.setPadding(0,18,0,0); root.addView(status);
  TextView safe=new TextView(this); safe.setText("Local network handshake only. No USB ADB, root, firmware changes, or system-file access."); safe.setTextColor(0xffa4abc1); safe.setPadding(0,24,0,0); root.addView(safe);
  setContentView(root); connect.setOnClickListener(v->connectToQuest());
 }
 private void connectToQuest() {
  String host=ip.getText().toString().trim(); if(host.isEmpty()){status.setText("Enter the Quest IP address.");return;}
  getPreferences(0).edit().putString("quest_ip",host).apply();
  connect.setEnabled(false); status.setText("Connecting…");
  io.execute(()->{
   String result;
   try(Socket s=new Socket()) {
    s.connect(new InetSocketAddress(host,47821),4000);
    s.setSoTimeout(4000); PrintWriter out=new PrintWriter(s.getOutputStream(),true);
    BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream())); out.println("STATUS");
    String reply=in.readLine();
    if(reply!=null && reply.startsWith("READY|")) { String[] parts=reply.split("\\|",3); result="Connected to VRFlex ✓"+(parts.length==3?"\nSelected profile: "+parts[2]:""); }
    else result="Unexpected response from Quest.";
   } catch(Exception e) { result="Connection failed. Check Wi-Fi, IP, and keep VRFlex open on Quest."; }
   final String message=result; runOnUiThread(()->{status.setText(message);connect.setEnabled(true);});
  });
 }
 protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
