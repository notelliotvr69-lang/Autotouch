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
    interface Listener {
        void onPose(PoseLandmarkerResult result);
        void onError(String message);
    }

    private final PoseLandmarker landmarker;
    private final Listener listener;

    PoseProcessor(Context context, Listener listener) {
        this.listener = listener;

        BaseOptions baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build();

        PoseLandmarker.PoseLandmarkerOptions options =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setMinPoseDetectionConfidence(0.5f)
                        .setMinPosePresenceConfidence(0.5f)
                        .setMinTrackingConfidence(0.5f)
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
            imageProxy.close();
            listener.onError(e.getMessage() == null ? "Camera frame error" : e.getMessage());
        }
    }

    void close() {
        landmarker.close();
    }
}
