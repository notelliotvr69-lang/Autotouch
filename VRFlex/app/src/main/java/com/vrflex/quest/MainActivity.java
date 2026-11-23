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
    private String selectedGame = "gorilla";
    private final int bg = Color.rgb(10,12,20), panel = Color.rgb(24,27,40),
        accent = Color.rgb(125,92,255), text = Color.rgb(245,246,255),
        muted = Color.rgb(164,171,194);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("vrflex", MODE_PRIVATE);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        buildShell(); showTab(0);
    }
    private GradientDrawable shape(int c,int r) {
        GradientDrawable d=new GradientDrawable(); d.setColor(c); d.setCornerRadius(r); return d;
    }
    private TextView label(String s,int z,boolean bold) {
        TextView t=new TextView(this); t.setText(s); t.setTextColor(text); t.setTextSize(z);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }
    private void buildShell() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(20,18,20,18); root.setBackgroundColor(bg);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label("VRFLEX",25,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView ver=label("QUEST 3 • v0.2",12,true); ver.setTextColor(muted); header.addView(ver); root.addView(header);
        TextView sub=label("Game profiles • comfort controls",14,false); sub.setTextColor(muted); root.addView(sub);
        LinearLayout tabs=new LinearLayout(this); tabs.setPadding(0,16,0,12);
        String[] names={"Library","Movement","Arms","General"};
        for(int i=0;i<names.length;i++){final int n=i; Button b=new Button(this); b.setText(names[i]); b.setTextSize(11); b.setAllCaps(false); b.setTextColor(text); b.setBackground(shape(panel,14));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,50,1); p.setMargins(0,0,5,0); tabs.addView(b,p);
            b.setOnClickListener(v->{activeTab=n;showTab(n);});}
        root.addView(tabs);
        ScrollView scroll=new ScrollView(this); content=new LinearLayout(this); content.setOrientation(1); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView foot=label("Profiles save locally • Game integrations are separate",10,false); foot.setTextColor(muted); foot.setPadding(0,12,0,0); root.addView(foot);
        setContentView(root);
    }
    private void card(String title,String desc) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(1); box.setPadding(18,16,18,16); box.setBackground(shape(panel,20));
        box.addView(label(title,17,true)); TextView d=label(desc,12,false); d.setTextColor(muted); box.addView(d);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,12); content.addView(box,p);
    }
    private String key(String k){return selectedGame+"_"+k;}
    private void toggle(String title,String desc,String k,boolean init) {
        card(title,desc); Switch s=new Switch(this); s.setText("Enabled"); s.setTextColor(text);
        s.setChecked(prefs.getBoolean(key(k),init)); s.setOnCheckedChangeListener((b,v)->prefs.edit().putBoolean(key(k),v).apply()); content.addView(s); space();
    }
    private void slider(String title,String k,int min,int max,int init,String unit) {
        card(title,"Saved in the "+gameName()+" profile. Requires a compatible game integration to take effect.");
        int val=prefs.getInt(key(k),init); TextView now=label(val+unit,15,true); content.addView(now);
        SeekBar seek=new SeekBar(this); seek.setMax(max-min); seek.setProgress(val-min); content.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean u){int v=p+min;now.setText(v+unit);if(u)prefs.edit().putInt(key(k),v).apply();}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        }); space();
    }
    private void space(){View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,8));}
    private String gameName(){return selectedGame.equals("gorilla")?"Gorilla Tag":"GILLA";}
    private void chooseGame(String id) {
        selectedGame=id; activeTab=1; showTab(1);
    }
    private void gameCard(String id,String name,String detail) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(1); box.setPadding(18,16,18,16); box.setBackground(shape(panel,20));
        box.addView(label(name,19,true)); TextView d=label(detail,12,false);d.setTextColor(muted);box.addView(d);
        Button b=new Button(this);b.setText("Open "+name+" profile");b.setAllCaps(false);b.setTextColor(text);b.setBackground(shape(accent,14));box.addView(b);
        b.setOnClickListener(v->chooseGame(id));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,14);content.addView(box,p);
    }
    private void showTab(int tab) {
        content.removeAllViews();
        if(tab==0){
            card("GAME LIBRARY","Choose a game to manage its independent VRFlex profile.");
            gameCard("gorilla","Gorilla Tag","Quest game • Per-game settings profile");
            gameCard("gilla","GILLA","Quest game by Chase • Per-game settings profile");
            card("Integration status","The library and profile storage are implemented. Game hooks, the in-game Y-button overlay, and live movement are not installed yet; each game needs a compatible integration.");
        } else if(tab==1){
            card(gameName()+" profile","Settings below are stored separately for this game.");
            card("Selected game: "+gameName(),"Profile key: "+selectedGame);
            toggle("Enable VRFlex profile","Turn this game's saved profile on or off.","enabled",false);
            toggle("Sitting mode","Saved preference for a compatible seated-mode integration.","sitting",false);
            slider("Forward / backward offset","offset",-100,100,0," cm");
            slider("Movement speed","speed",1,100,25,"%");
            card("Controls","Planned: hold controller trigger to move; release to stop. Hold Y to show the panel and release Y to hide it. These controls need a game-side integration.");
            Button back=new Button(this);back.setText("Back to game library");back.setOnClickListener(v->showTab(0));content.addView(back);
        } else if(tab==2){
            card("Arm settings",gameName()+" profile");
            slider("Left arm length","left_arm",-50,50,0,"%");
            slider("Right arm length","right_arm",-50,50,0,"%");
        } else {
            card("General","VRFlex preferences are stored locally on this headset.");
            Button reset=new Button(this);reset.setText("Reset all profiles");reset.setAllCaps(false);
            reset.setOnClickListener(v->{prefs.edit().clear().apply();showTab(3);});content.addView(reset);
            card("Compatibility note","A companion APK cannot automatically alter another game's tracking. Live controls require a supported integration for each game.");
        }
    }
}