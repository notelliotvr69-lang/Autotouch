package com.motofbt.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;

public final class PoseOverlayView extends View {
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private PoseLandmarkerResult result;
    private boolean mirror;

    private static final int[][] BONES = {
            {11, 12},
            {11, 13}, {13, 15},
            {12, 14}, {14, 16},
            {11, 23}, {12, 24},
            {23, 24},
            {23, 25}, {25, 27}, {27, 29}, {29, 31},
            {24, 26}, {26, 28}, {28, 30}, {30, 32}
    };

    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        pointPaint.setColor(Color.YELLOW);
        pointPaint.setStrokeWidth(9f);
        linePaint.setColor(Color.argb(190, 0, 255, 160));
        linePaint.setStrokeWidth(4f);
        setWillNotDraw(false);
    }

    public void setMirror(boolean mirror) {
        this.mirror = mirror;
        postInvalidate();
    }

    public void setResult(PoseLandmarkerResult result) {
        this.result = result;
        postInvalidate();
    }

    private float x(NormalizedLandmark p) {
        float value = p.x();
        if (mirror) value = 1f - value;
        return value * getWidth();
    }

    private float y(NormalizedLandmark p) {
        return p.y() * getHeight();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (result == null || result.landmarks().isEmpty()) return;

        List<NormalizedLandmark> lm = result.landmarks().get(0);
        if (lm.size() < 33) return;

        for (int[] bone : BONES) {
            NormalizedLandmark a = lm.get(bone[0]);
            NormalizedLandmark b = lm.get(bone[1]);
            canvas.drawLine(x(a), y(a), x(b), y(b), linePaint);
        }

        for (NormalizedLandmark p : lm) {
            canvas.drawCircle(x(p), y(p), 4.5f, pointPaint);
        }
    }
}
