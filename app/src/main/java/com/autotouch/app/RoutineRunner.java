package com.autotouch.app;

import android.os.Handler;
import android.os.Looper;

/** Small state machine that advances through steps and repetitions after gesture completion. */
public final class RoutineRunner {
    public enum State { IDLE, WAITING, PERFORMING }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private State state = State.IDLE;
    private Routine routine;
    private int stepIndex;
    private int repetition;

    public State state() { return state; }

    public void start(Routine routine) {
        stop();
        this.routine = routine;
        stepIndex = repetition = 0;
        schedule();
    }

    private void schedule() {
        if (routine == null) return;
        state = State.WAITING;
        handler.postDelayed(this::perform, Math.max(0, routine.steps[stepIndex].delayMs()));
    }

    private void perform() {
        if (routine == null) return;
        state = State.PERFORMING;
        AutoTouchAccessibilityService service = AutoTouchAccessibilityService.instance();
        if (service == null) { stop(); return; }
        service.perform(routine.steps[stepIndex], this::advance);
    }

    private void advance() {
        if (routine == null) return;
        stepIndex++;
        if (stepIndex >= routine.steps.length) { stepIndex = 0; repetition++; }
        if (repetition >= routine.repetitions) stop(); else schedule();
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        routine = null;
        state = State.IDLE;
    }
}
