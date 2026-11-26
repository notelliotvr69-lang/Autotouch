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
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout content;
    private int activeTab = 0;
    private String selectedGame = "gorilla";
    private String selectedGameName = "Gorilla Tag";
    private ServerSocket companionServer;
    private static final int COMPANION_PORT=47821;
    // VRFlex theme: near-black surfaces, orange controls, light text.
    private final int bg = Color.rgb(8,8,8), panel = Color.rgb(23,23,23),
        accent = Color.rgb(255,140,0), text = Color.rgb(245,245,245),
        muted = Color.rgb(180,180,180);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("vrflex", MODE_PRIVATE);
        selectedGame=prefs.getString("selected_game","gorilla");
        selectedGameName=prefs.getString("selected_game_name","Gorilla Tag");
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        buildShell(); startCompanionServer(); showTab(0);
    }
    private GradientDrawable shape(int c,int r) {
        GradientDrawable d=new GradientDrawable(); d.setColor(c); d.setCornerRadius(r);
        d.setStroke(2, Color.rgb(55,55,55)); return d;
    }
    private TextView label(String s,int z,boolean bold) {
        TextView t=new TextView(this); t.setText(s); t.setTextColor(text); t.setTextSize(z);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }
    private void buildShell() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(20,18,20,18);
        GradientDrawable frame=shape(bg,10); frame.setStroke(4,Color.rgb(255,140,0)); root.setBackground(frame);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label("VRFLEX",25,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView ver=label("QUEST 3 • v0.2",12,true); ver.setTextColor(muted); header.addView(ver); root.addView(header);
        TextView sub=label("Game profiles • comfort controls",14,false); sub.setTextColor(muted); root.addView(sub);
        LinearLayout tabs=new LinearLayout(this); tabs.setPadding(0,16,0,12);
        String[] names={"Games (☰)","Mod Menu (Y)","Arms","General"};
        for(int i=0;i<names.length;i++){final int n=i; Button b=new Button(this); b.setText(names[i]); b.setTextSize(11); b.setAllCaps(false); b.setTextColor(Color.BLACK); b.setBackground(shape(accent,14));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,50,1); p.setMargins(0,0,5,0); tabs.addView(b,p);
            b.setOnClickListener(v->{activeTab=n;showTab(n);});}
        root.addView(tabs);
        ScrollView scroll=new ScrollView(this); content=new LinearLayout(this); content.setOrientation(1); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView foot=label("Profiles save locally • Y = Mod Menu • ☰ = Games",10,false); foot.setTextColor(muted); foot.setPadding(0,12,0,0); root.addView(foot);
        setContentView(root);
    }
    private void card(String title,String desc) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(1); box.setPadding(18,16,18,16); box.setBackground(shape(panel,20));
        box.addView(label(title,17,true)); TextView d=label(desc,12,false); d.setTextColor(muted); box.addView(d);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,12); content.addView(box,p);
    }
    private String key(String k){return selectedGame+"_"+k;}
    private void toggle(String title,String desc,String k,boolean init) {
        card(title,desc); Switch s=new Switch(this); s.setText("Enabled"); s.setTextColor(text); s.setThumbTintList(android.content.res.ColorStateList.valueOf(accent)); s.setTrackTintList(android.content.res.ColorStateList.valueOf(Color.rgb(95,55,12)));
        s.setChecked(prefs.getBoolean(key(k),init)); s.setOnCheckedChangeListener((b,v)->prefs.edit().putBoolean(key(k),v).apply()); content.addView(s); space();
    }
    private void slider(String title,String k,int min,int max,int init,String unit) {
        card(title,"Saved in the "+gameName()+" profile. Requires a compatible game integration to take effect.");
        int val=prefs.getInt(key(k),init); TextView now=label(val+unit,15,true); content.addView(now);
        SeekBar seek=new SeekBar(this); seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent)); seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent)); seek.setMax(max-min); seek.setProgress(val-min); content.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean u){int v=p+min;now.setText(v+unit);if(u)prefs.edit().putInt(key(k),v).apply();}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        }); space();
    }
    private void space(){View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,8));}
    private String gameName(){return selectedGame.equals("gorilla")?"Gorilla Tag":selectedGame.equals("gilla")?"GILLA":selectedGameName;}
    private void chooseGame(String id) {
        selectedGame=id; selectedGameName=id.equals("gorilla")?"Gorilla Tag":id.equals("gilla")?"GILLA":selectedGameName;
        prefs.edit().putString("selected_game",selectedGame).putString("selected_game_name",selectedGameName).apply();
        activeTab=1; showTab(1);
    }
    private void gameCard(String id,String name,String detail) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(1); box.setPadding(18,16,18,16); box.setBackground(shape(panel,20));
        box.addView(label(name,19,true)); TextView d=label(detail,12,false);d.setTextColor(muted);box.addView(d);
        Button b=new Button(this);b.setText("Open "+name+" profile");b.setAllCaps(false);b.setTextColor(Color.BLACK);b.setBackground(shape(accent,14));box.addView(b);
        b.setOnClickListener(v->chooseGame(id));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,14);content.addView(box,p);
    }
    private void selectProfile(String pkg,String name) {
        selectedGame=pkg; selectedGameName=name;
        prefs.edit().putString("selected_game",pkg).putString("selected_game_name",name).apply();
    }
    private void launchGame(String pkg,String name) {
        // Save the selected profile before handing focus to the game.
        selectProfile(pkg,name);
        Intent game=getPackageManager().getLaunchIntentForPackage(pkg);
        if(game!=null) {
            try { startActivity(game); }
            catch(Exception e) { Toast.makeText(this,"Could not launch "+name,Toast.LENGTH_SHORT).show(); }
        } else Toast.makeText(this,"No launch activity found for "+name,Toast.LENGTH_SHORT).show();
    }
    private void addProfileControls() {
        toggle("Enable VRFlex profile","Enable this game's saved profile.","enabled",false);
        toggle("Sitting mode","Saved preference for a compatible seated-mode integration.","sitting",false);
        slider("Forward / backward offset","offset",-100,100,0," cm");
        slider("Movement speed","speed",1,100,25,"%");
        slider("Controller prediction","prediction",0,200,33,"");
        slider("Shared arm length","arm_length",0,200,100,"%");
    }
    private void showTab(int tab) {
        content.removeAllViews();
        if(tab==0){
            card("GAME SWITCHER","Left Menu (☰) is the intended game-switcher shortcut. Choose a game below to load its profile and launch it.");
            gameCard("gorilla","Gorilla Tag","Quest game • Per-game settings profile");
            gameCard("gilla","GILLA","Quest game by Chase • Per-game settings profile");
            card("INSTALLED GAMES & APPS","Choose a profile or switch directly to another installed app. VRFlex saves the selected profile before launching.");
            Intent launcher=new Intent(Intent.ACTION_MAIN); launcher.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> installed=getPackageManager().queryIntentActivities(launcher,0);
            Collections.sort(installed,new Comparator<ResolveInfo>() {
                public int compare(ResolveInfo a,ResolveInfo b) {
                    return a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString());
                }
            });
            Set<String> profiles=new HashSet<>(prefs.getStringSet("game_profiles",new HashSet<>()));
            for(ResolveInfo app:installed) {
                if(app.activityInfo==null || app.activityInfo.packageName.equals(getPackageName())) continue;
                final String pkg=app.activityInfo.packageName;
                final String name=app.loadLabel(getPackageManager()).toString();
                LinearLayout item=new LinearLayout(this); item.setOrientation(1); item.setPadding(14,12,14,12); item.setBackground(shape(panel,16));
                item.addView(label(name,15,true));
                TextView detail=label(pkg+" • Separate saved profile",10,false); detail.setTextColor(muted); item.addView(detail);
                Button add=new Button(this); add.setAllCaps(false); add.setText(profiles.contains(pkg)?"Open VRFlex profile":"Add VRFlex profile");
                add.setTextColor(Color.BLACK); add.setBackground(shape(accent,12));
                add.setOnClickListener(v->{profiles.add(pkg);selectProfile(pkg,name);activeTab=1;showTab(1);});
                item.addView(add);
                Button launch=new Button(this); launch.setAllCaps(false); launch.setText("Launch "+name);
                launch.setTextColor(Color.BLACK); launch.setBackground(shape(accent,12));
                launch.setOnClickListener(v->launchGame(pkg,name));
                item.addView(launch);
                LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.setMargins(0,0,0,8);content.addView(item,ip);
            }
            card("CURRENT PROFILE SETTINGS","The same selected-game profile is available here and in the Mod Menu tab.");
            addProfileControls();
            card("BUTTON LAYOUT","Left Y = Mod Menu. Left Menu (☰) = Games / game switcher. Quest-wide controller interception still requires OS-supported input access or a game integration.");
        } else if(tab==1){
            card(gameName()+" MOD MENU","Left Y is the intended shortcut for this menu. Settings are stored in the active game profile.");
            card("Selected game: "+gameName(),"Profile key: "+selectedGame);
            addProfileControls();
            card("BUTTON LAYOUT","Left Y → Mod Menu. Left Menu (☰) → Games / game switcher. Quest-wide button delivery needs OS-supported input access or a game integration.");
            Button back=new Button(this);back.setText("Back to game library");back.setTextColor(text);back.setBackground(shape(Color.rgb(35,35,35),12));back.setOnClickListener(v->showTab(0));content.addView(back);
        } else if(tab==2){
            card("Arm settings",gameName()+" profile");
            slider("Controller prediction","prediction",0,200,33,"");
            slider("Shared arm length","arm_length",0,200,100,"%");
            card("SHORTCUTS","Left Y opens Mod Menu; left Menu (☰) opens Games when the active integration exposes those inputs.");
        } else {
            card("General","VRFlex preferences are stored locally on this headset.");
            card("Phone companion","Connect your Android phone and Quest to the same Wi-Fi network. Enter this headset IP in VRFlex Mobile. Keep VRFlex open while connecting.");
            card("Quest IP address",""+localIp()+":"+COMPANION_PORT);
            Button reset=new Button(this);reset.setText("Reset all profiles");reset.setAllCaps(false);reset.setTextColor(text);reset.setBackground(shape(Color.rgb(35,35,35),12));
            reset.setOnClickListener(v->{prefs.edit().clear().apply();showTab(3);});content.addView(reset);
            card("Compatibility note","A companion APK cannot automatically alter another game's tracking. Live controls require a supported integration for each game.");
        }
    }

    private String localIp() {
        try {
            for (Enumeration<NetworkInterface> e=NetworkInterface.getNetworkInterfaces();e.hasMoreElements();) {
                NetworkInterface n=e.nextElement();
                for (Enumeration<InetAddress> a=n.getInetAddresses();a.hasMoreElements();) {
                    InetAddress ip=a.nextElement();
                    if(!ip.isLoopbackAddress() && ip instanceof java.net.Inet4Address) return ip.getHostAddress();
                }
            }
        } catch(Exception ignored) {}
        return "Connect to Wi-Fi";
    }
    private void startCompanionServer() {
        Thread t=new Thread(() -> {
            try(ServerSocket server=new ServerSocket(COMPANION_PORT)) {
                while(!server.isClosed()) {
                    try(Socket client=server.accept();
                        BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream()));
                        PrintWriter out=new PrintWriter(client.getOutputStream(),true)) {
                        String command=in.readLine();
                        if("PING".equals(command)) out.println("VRFLEX_READY");
                        else if("STATUS".equals(command)) out.println("READY|"+selectedGame+"|"+gameName());
                        else out.println("UNSUPPORTED");
                    } catch(Exception ignored) {}
                }
            } catch(Exception ignored) {}
        });
        t.setDaemon(true); t.start();
    }
}
