package com.autotouch.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class AutomationConfig {
    public final boolean autoLevel;
    public final boolean autoQuest;
    public final boolean autoChest;
    public final boolean autoFruitFarm;
    public final boolean autoBuyFruit;
    public final int buyFruitIntervalMinutes;
    public final int loopDelayMs;

    public AutomationConfig(
            boolean autoLevel,
            boolean autoQuest,
            boolean autoChest,
            boolean autoFruitFarm,
            boolean autoBuyFruit,
            int buyFruitIntervalMinutes,
            int loopDelayMs
    ) {
        this.autoLevel = autoLevel;
        this.autoQuest = autoQuest;
        this.autoChest = autoChest;
        this.autoFruitFarm = autoFruitFarm;
        this.autoBuyFruit = autoBuyFruit;
        this.buyFruitIntervalMinutes = Math.max(1, buyFruitIntervalMinutes);
        this.loopDelayMs = Math.max(1, loopDelayMs);
    }

    public int enabledCount() {
        int count = 0;
        if (autoLevel) count++;
        if (autoQuest) count++;
        if (autoChest) count++;
        if (autoFruitFarm) count++;
        if (autoBuyFruit) count++;
        return count;
    }

    public String enabledSummary() {
        StringBuilder out = new StringBuilder();
        append(out, autoLevel, "Level");
        append(out, autoQuest, "Quest");
        append(out, autoChest, "Money/Chest");
        append(out, autoFruitFarm, "Fruit Farm");
        append(out, autoBuyFruit, "Buy Fruit");
        return out.length() == 0 ? "No modules enabled" : out.toString();
    }

    private static void append(StringBuilder out, boolean enabled, String name) {
        if (!enabled) return;
        if (out.length() > 0) out.append(", ");
        out.append(name);
    }

    public void save(Context context) {
        context.getSharedPreferences("automation_config", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("auto_level", autoLevel)
                .putBoolean("auto_quest", autoQuest)
                .putBoolean("auto_chest", autoChest)
                .putBoolean("auto_fruit_farm", autoFruitFarm)
                .putBoolean("auto_buy_fruit", autoBuyFruit)
                .putInt("buy_fruit_interval_minutes", buyFruitIntervalMinutes)
                .putInt("loop_delay_ms", loopDelayMs)
                .apply();
    }

    public static AutomationConfig load(Context context) {
        SharedPreferences p = context.getSharedPreferences("automation_config", Context.MODE_PRIVATE);
        return new AutomationConfig(
                p.getBoolean("auto_level", false),
                p.getBoolean("auto_quest", false),
                p.getBoolean("auto_chest", false),
                p.getBoolean("auto_fruit_farm", false),
                p.getBoolean("auto_buy_fruit", false),
                p.getInt("buy_fruit_interval_minutes", 120),
                p.getInt("loop_delay_ms", 1000)
        );
    }
}
