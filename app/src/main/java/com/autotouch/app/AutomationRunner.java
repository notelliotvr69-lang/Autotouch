package com.autotouch.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

public final class AutomationRunner {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ScriptEngine engine = new ScriptEngine();

    private boolean running;
    private int moduleIndex;

    public interface Listener {
        void onStatus(String text);
    }

    private final Listener listener;

    public AutomationRunner(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        stop();
        if (AutoTouchAccessibilityService.instance() == null) {
            listener.onStatus("Accessibility off");
            return;
        }
        running = true;
        moduleIndex = 0;
        listener.onStatus("Starting");
        nextCycle();
    }

    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        engine.cancel();
        listener.onStatus("Stopped");
    }

    private void nextCycle() {
        if (!running) return;

        if (AutoTouchAccessibilityService.instance() == null) {
            listener.onStatus("Accessibility off");
            stop();
            return;
        }

        AutomationConfig config = AutomationConfig.load(context);
        List<String> modules = enabledModules(config);
        if (modules.isEmpty()) {
            listener.onStatus("No modules");
            stop();
            return;
        }

        if (moduleIndex >= modules.size()) {
            moduleIndex = 0;
            handler.postDelayed(this::nextCycle, config.loopDelayMs);
            return;
        }

        String module = modules.get(moduleIndex++);
        if (ScriptStore.BUY_FRUIT.equals(module) && !buyFruitDue(config)) {
            nextCycle();
            return;
        }

        String script = ScriptStore.load(context, module);
        listener.onStatus(displayName(module));
        engine.run(script, error -> {
            if (!running) return;
            if (error != null) {
                listener.onStatus(error);
                handler.postDelayed(this::nextCycle, 1000);
                return;
            }
            if (ScriptStore.BUY_FRUIT.equals(module)) markBuyFruitRun();
            nextCycle();
        });
    }

    public void runOnce(String module, Listener oneShotListener) {
        engine.cancel();
        if (AutoTouchAccessibilityService.instance() == null) {
            oneShotListener.onStatus("Accessibility off");
            return;
        }
        oneShotListener.onStatus("Running " + displayName(module));
        engine.run(ScriptStore.load(context, module), error -> {
            if (error == null) oneShotListener.onStatus("Done");
            else oneShotListener.onStatus(error);
        });
    }

    private List<String> enabledModules(AutomationConfig c) {
        List<String> out = new ArrayList<>();
        if (c.autoLevel) out.add(ScriptStore.LEVEL);
        if (c.autoQuest) out.add(ScriptStore.QUEST);
        if (c.autoChest) out.add(ScriptStore.CHEST);
        if (c.autoFruitFarm) out.add(ScriptStore.FRUIT_FARM);
        if (c.autoBuyFruit) out.add(ScriptStore.BUY_FRUIT);
        return out;
    }

    private boolean buyFruitDue(AutomationConfig c) {
        long last = context.getSharedPreferences("runner", Context.MODE_PRIVATE)
                .getLong("last_buy_fruit", 0L);
        long interval = c.buyFruitIntervalMinutes * 60_000L;
        return System.currentTimeMillis() - last >= interval;
    }

    private void markBuyFruitRun() {
        context.getSharedPreferences("runner", Context.MODE_PRIVATE)
                .edit().putLong("last_buy_fruit", System.currentTimeMillis()).apply();
    }

    public static String displayName(String key) {
        switch (key) {
            case ScriptStore.LEVEL: return "Auto Level";
            case ScriptStore.QUEST: return "Auto Quest";
            case ScriptStore.CHEST: return "Money / Chest";
            case ScriptStore.FRUIT_FARM: return "Fruit Farm";
            case ScriptStore.BUY_FRUIT: return "Buy Fruit";
            default: return key;
        }
    }
}
