package com.motofbt.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;

import androidx.camera.core.ImageProxy;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

final class PoseProcessor {
    static final int QUALITY_FAST = 0;
    static final int QUALITY_BALANCED = 1;
    static final int QUALITY_ACCURATE = 2;

    interface Listener {
        void onPose(PoseLandmarkerResult result);
        void onError(String message);
    }

    private final PoseLandmarker landmarker;
    private final Listener listener;

    PoseProcessor(Context context, Listener listener, int quality) {
        this.listener = listener;

        String model = quality == QUALITY_FAST
                ? "pose_landmarker_lite.task"
                : "pose_landmarker_full.task";

        float detection;
        float presence;
        float tracking;

        if (quality == QUALITY_ACCURATE) {
            detection = 0.65f;
            presence = 0.65f;
            tracking = 0.72f;
        } else if (quality == QUALITY_BALANCED) {
            detection = 0.55f;
            presence = 0.55f;
            tracking = 0.62f;
        } else {
            detection = 0.45f;
            presence = 0.45f;
            tracking = 0.50f;
        }

        BaseOptions baseOptions = BaseOptions.builder()
                .setModelAssetPath(model)
                .build();

        PoseLandmarker.PoseLandmarkerOptions options =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setMinPoseDetectionConfidence(detection)
                        .setMinPosePresenceConfidence(presence)
                        .setMinTrackingConfidence(tracking)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setResultListener((result, input) -> listener.onPose(result))
                        .setErrorListener(error ->
                                listener.onError(error.getMessage() == null
                                        ? "Pose tracking error"
                                        : error.getMessage()))
                        .build();

        landmarker = PoseLandmarker.createFromOptions(context, options);
    }

    void process(ImageProxy imageProxy) {
        long timestamp = SystemClock.uptimeMillis();
        int rotation = imageProxy.getImageInfo().getRotationDegrees();

        try {
            Bitmap buffer = Bitmap.createBitmap(
                    imageProxy.getWidth(),
                    imageProxy.getHeight(),
                    Bitmap.Config.ARGB_8888
            );

            imageProxy.getPlanes()[0].getBuffer().rewind();
            buffer.copyPixelsFromBuffer(imageProxy.getPlanes()[0].getBuffer());
            imageProxy.close();

            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);

            Bitmap rotated = Bitmap.createBitmap(
                    buffer, 0, 0, buffer.getWidth(), buffer.getHeight(), matrix, true
            );

            MPImage image = new BitmapImageBuilder(rotated).build();
            landmarker.detectAsync(image, timestamp);
        } catch (Exception e) {
            try {
                imageProxy.close();
            } catch (Exception ignored) {
            }
            listener.onError(e.getMessage() == null ? "Camera frame error" : e.getMessage());
        }
    }

    void close() {
        landmarker.close();
    }
}
