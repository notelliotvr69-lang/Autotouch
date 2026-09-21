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
    private PoseLandmarkerResult result;

    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        pointPaint.setColor(Color.YELLOW);
        pointPaint.setStrokeWidth(10f);
        setWillNotDraw(false);
    }

    public void setResult(PoseLandmarkerResult result) {
        this.result = result;
        postInvalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (result == null || result.landmarks().isEmpty()) return;

        List<NormalizedLandmark> lm = result.landmarks().get(0);
        for (NormalizedLandmark p : lm) {
            canvas.drawCircle(p.x() * getWidth(), p.y() * getHeight(), 5f, pointPaint);
        }
    }
}
