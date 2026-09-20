package com.autotouch.app;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small Lua-like automation language for AutoTouch.
 *
 * Supported commands:
 *   tap(x, y)
 *   tap_pct(xPercent, yPercent)
 *   swipe(x1, y1, x2, y2, durationMs)
 *   swipe_pct(x1Percent, y1Percent, x2Percent, y2Percent, durationMs)
 *   wait(ms)
 *   repeat(n)
 *     ...
 *   end
 *
 * Lines beginning with -- are comments.
 */
public final class ScriptEngine {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean cancelled;

    public interface Completion {
        void done(String error);
    }

    private interface Command {
        void run(ScriptEngine engine, Runnable next);
    }

    public void cancel() {
        cancelled = true;
        handler.removeCallbacksAndMessages(null);
    }

    public void run(String source, Completion completion) {
        cancel();
        cancelled = false;

        final List<Command> commands;
        try {
            commands = parse(source);
        } catch (IllegalArgumentException e) {
            completion.done(e.getMessage());
            return;
        }

        execute(commands, 0, completion);
    }

    private void execute(List<Command> commands, int index, Completion completion) {
        if (cancelled) {
            completion.done(null);
            return;
        }
        if (index >= commands.size()) {
            completion.done(null);
            return;
        }
        commands.get(index).run(this, () -> execute(commands, index + 1, completion));
    }

    private List<Command> parse(String source) {
        List<String> lines = new ArrayList<>();
        for (String raw : source.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("--")) continue;
            lines.add(line);
        }
        ParseResult result = parseBlock(lines, 0, false);
        if (result.nextIndex != lines.size()) {
            throw new IllegalArgumentException("Unexpected 'end'");
        }
        return result.commands;
    }

    private ParseResult parseBlock(List<String> lines, int start, boolean nested) {
        List<Command> out = new ArrayList<>();
        int i = start;

        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.equalsIgnoreCase("end")) {
                if (!nested) throw new IllegalArgumentException("Unexpected 'end' on line " + (i + 1));
                return new ParseResult(out, i + 1);
            }

            String lower = line.toLowerCase(Locale.US);
            if (lower.startsWith("repeat(")) {
                int count = oneInt(line, "repeat");
                if (count < 1 || count > 1000) throw new IllegalArgumentException("repeat() must be 1-1000");
                ParseResult block = parseBlock(lines, i + 1, true);
                List<Command> blockCopy = block.commands;
                out.add((engine, next) -> engine.runRepeated(blockCopy, count, 0, next));
                i = block.nextIndex;
                continue;
            }

            out.add(parseCommand(line, i + 1));
            i++;
        }

        if (nested) throw new IllegalArgumentException("Missing 'end'");
        return new ParseResult(out, i);
    }

    private void runRepeated(List<Command> commands, int count, int iteration, Runnable done) {
        if (cancelled || iteration >= count) {
            done.run();
            return;
        }
        execute(commands, 0, error -> {
            if (error != null || cancelled) done.run();
            else runRepeated(commands, count, iteration + 1, done);
        });
    }

    private Command parseCommand(String line, int lineNumber) {
        String lower = line.toLowerCase(Locale.US);

        try {
            if (lower.startsWith("tap(")) {
                int[] a = ints(line, "tap", 2);
                return (engine, next) -> engine.gestureTap(a[0], a[1], next);
            }
            if (lower.startsWith("tap_pct(")) {
                int[] a = ints(line, "tap_pct", 2);
                return (engine, next) -> {
                    int[] p = engine.percentToPixels(a[0], a[1]);
                    engine.gestureTap(p[0], p[1], next);
                };
            }
            if (lower.startsWith("swipe(")) {
                int[] a = ints(line, "swipe", 5);
                return (engine, next) -> engine.gestureSwipe(a[0], a[1], a[2], a[3], a[4], next);
            }
            if (lower.startsWith("swipe_pct(")) {
                int[] a = ints(line, "swipe_pct", 5);
                return (engine, next) -> {
                    int[] p1 = engine.percentToPixels(a[0], a[1]);
                    int[] p2 = engine.percentToPixels(a[2], a[3]);
                    engine.gestureSwipe(p1[0], p1[1], p2[0], p2[1], a[4], next);
                };
            }
            if (lower.startsWith("wait(")) {
                int ms = oneInt(line, "wait");
                if (ms < 0 || ms > 3_600_000) throw new IllegalArgumentException("wait() must be 0-3600000 ms");
                return (engine, next) -> engine.handler.postDelayed(next, ms);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number on line " + lineNumber);
        }

        throw new IllegalArgumentException("Unknown command on line " + lineNumber + ": " + line);
    }

    private void gestureTap(int x, int y, Runnable next) {
        AutoTouchAccessibilityService service = AutoTouchAccessibilityService.instance();
        if (service == null) {
            next.run();
            return;
        }
        service.tap(x, y, next);
    }

    private void gestureSwipe(int x1, int y1, int x2, int y2, int duration, Runnable next) {
        AutoTouchAccessibilityService service = AutoTouchAccessibilityService.instance();
        if (service == null) {
            next.run();
            return;
        }
        service.swipe(x1, y1, x2, y2, Math.max(1, duration), next);
    }

    private int[] percentToPixels(int xPercent, int yPercent) {
        AutoTouchAccessibilityService service = AutoTouchAccessibilityService.instance();
        if (service == null) return new int[]{0, 0};
        int width = service.getResources().getDisplayMetrics().widthPixels;
        int height = service.getResources().getDisplayMetrics().heightPixels;
        int x = Math.round(width * clampPercent(xPercent) / 100f);
        int y = Math.round(height * clampPercent(yPercent) / 100f);
        return new int[]{x, y};
    }

    private int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static int oneInt(String line, String name) {
        return ints(line, name, 1)[0];
    }

    private static int[] ints(String line, String name, int expected) {
        String prefix = name + "(";
        if (!line.toLowerCase(Locale.US).startsWith(prefix) || !line.endsWith(")")) {
            throw new IllegalArgumentException("Bad " + name + "() syntax");
        }
        String inside = line.substring(prefix.length(), line.length() - 1).trim();
        String[] parts = inside.isEmpty() ? new String[0] : inside.split(",");
        if (parts.length != expected) throw new IllegalArgumentException(name + "() expects " + expected + " arguments");
        int[] values = new int[expected];
        for (int i = 0; i < expected; i++) values[i] = Integer.parseInt(parts[i].trim());
        return values;
    }

    private record ParseResult(List<Command> commands, int nextIndex) {}
}
