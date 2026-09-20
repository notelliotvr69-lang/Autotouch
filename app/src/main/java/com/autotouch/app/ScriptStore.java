package com.autotouch.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class ScriptStore {
    public static final String LEVEL = "level";
    public static final String QUEST = "quest";
    public static final String CHEST = "chest";
    public static final String FRUIT_FARM = "fruit_farm";
    public static final String BUY_FRUIT = "buy_fruit";

    private ScriptStore() {}

    public static String load(Context context, String key) {
        SharedPreferences p = context.getSharedPreferences("scripts", Context.MODE_PRIVATE);
        return p.getString(key, defaultScript(key));
    }

    public static void save(Context context, String key, String script) {
        context.getSharedPreferences("scripts", Context.MODE_PRIVATE)
                .edit().putString(key, script).apply();
    }

    public static String defaultScript(String key) {
        switch (key) {
            case LEVEL:
                return "-- Auto Level starter\n-- Needs more HUD/NPC calibration screenshots\nwait(1000)";
            case QUEST:
                return "-- Auto Quest starter\n-- Needs quest-menu calibration screenshots\nwait(1000)";
            case CHEST:
                return "-- Money / Chest starter for the HUD you sent\n-- Moves forward in short bursts so Start causes a real in-game action\nrepeat(6)\nswipe_pct(10,88,10,78,300)\nwait(250)\nend";
            case FRUIT_FARM:
                return "-- Fruit Farm starter\n-- Needs fruit-screen calibration screenshots\nwait(1000)";
            case BUY_FRUIT:
                return "-- Buy Fruit starter\n-- Needs shop-menu calibration screenshots\nwait(1000)";
            default:
                return "wait(1000)";
        }
    }
}
