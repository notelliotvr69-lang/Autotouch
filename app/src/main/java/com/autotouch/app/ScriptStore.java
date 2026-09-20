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
                return "-- Auto Level script\n-- Add your own touch logic here\nwait(1000)";
            case QUEST:
                return "-- Auto Quest script\nwait(1000)";
            case CHEST:
                return "-- Auto Money / Chest script\nwait(1000)";
            case FRUIT_FARM:
                return "-- Auto Fruit Farm script\nwait(1000)";
            case BUY_FRUIT:
                return "-- Auto Buy Fruit script\nwait(1000)";
            default:
                return "wait(1000)";
        }
    }
}
