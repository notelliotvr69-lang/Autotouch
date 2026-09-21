package com.motofbt.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity implements PoseProcessor.Listener {
    private static final int CAMERA_REQUEST = 10;
    private static final int OSC_PORT = 9000;

    private PreviewView previewView;
    private PoseOverlayView overlayView;
    private TextView statusText;
    private EditText ipField;
    private EditText heightField;
    private CheckBox extraTrackers;
    private Button streamButton;

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private PoseProcessor poseProcessor;
    private final TrackerMapper mapper = new TrackerMapper();
    private final OscSender oscSender = new OscSender();

    private volatile PoseLandmarkerResult latestResult;
    private volatile boolean streaming = false;
    private volatile long lastUiUpdate = 0L;
    private volatile long lastOscSend = 0L;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        statusText = findViewById(R.id.statusText);
        ipField = findViewById(R.id.ipField);
        heightField = findViewById(R.id.heightField);
        extraTrackers = findViewById(R.id.extraTrackers);
        streamButton = findViewById(R.id.streamButton);
        Button calibrateButton = findViewById(R.id.calibrateButton);

        var prefs = getSharedPreferences("settings", MODE_PRIVATE);
        ipField.setText(prefs.getString("quest_ip", ""));
        heightField.setText(prefs.getString("height_cm", "170"));
        extraTrackers.setChecked(prefs.getBoolean("extras", false));

        calibrateButton.setOnClickListener(v -> calibrate());
        streamButton.setOnClickListener(v -> toggleStreaming());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST
            );
        }
    }

    private void startCamera() {
        try {
            poseProcessor = new PoseProcessor(this, this);
        } catch (Exception e) {
            statusText.setText("Pose model failed: " + e.getMessage());
            return;
        }

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                analysis.setAnalyzer(cameraExecutor, poseProcessor::process);

                provider.unbindAll();
                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                );

                statusText.setText("Camera ready • stand fully in frame");
            } catch (Exception e) {
                statusText.setText("Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void calibrate() {
        PoseLandmarkerResult result = latestResult;
        if (result == null) {
            statusText.setText("No body detected yet");
            return;
        }

        float heightMeters;
        try {
            heightMeters = Float.parseFloat(heightField.getText().toString().trim()) / 100f;
        } catch (Exception e) {
            statusText.setText("Enter your height in cm");
            return;
        }

        boolean ok = mapper.calibrate(result, heightMeters);
        if (ok) {
            saveSettings();
            statusText.setText("Calibrated • keep the phone still");
        } else {
            statusText.setText("Calibration failed • fit your full body in frame");
        }
    }

    private void toggleStreaming() {
        if (!streaming) {
            String ip = ipField.getText().toString().trim();
            if (ip.isEmpty()) {
                statusText.setText("Enter the Quest IP first");
                return;
            }
            if (!mapper.isCalibrated()) {
                statusText.setText("Calibrate first");
                return;
            }
            saveSettings();
            streaming = true;
            streamButton.setText("Stop OSC");
            statusText.setText("Streaming OSC → " + ip + ":" + OSC_PORT);
        } else {
            streaming = false;
            streamButton.setText("Start OSC");
            statusText.setText("OSC stopped");
        }
    }

    private void saveSettings() {
        getSharedPreferences("settings", MODE_PRIVATE)
                .edit()
                .putString("quest_ip", ipField.getText().toString().trim())
                .putString("height_cm", heightField.getText().toString().trim())
                .putBoolean("extras", extraTrackers.isChecked())
                .apply();
    }

    @Override public void onPose(PoseLandmarkerResult result) {
        latestResult = result;
        long now = android.os.SystemClock.uptimeMillis();

        if (now - lastUiUpdate > 80) {
            lastUiUpdate = now;
            runOnUiThread(() -> {
                overlayView.setResult(result);
                if (!streaming) {
                    statusText.setText(mapper.isCalibrated()
                            ? "Pose detected • calibrated"
                            : "Pose detected • tap Calibrate while standing straight");
                }
            });
        }

        if (!streaming || now - lastOscSend < 33) return;
        lastOscSend = now;

        List<TrackerMapper.Tracker> trackers =
                mapper.map(result, extraTrackers.isChecked());

        String host = ipField.getText().toString().trim();
        for (TrackerMapper.Tracker t : trackers) {
            String base = "/tracking/trackers/" + t.id;
            oscSender.send(host, OSC_PORT, base + "/position", t.x, t.y, t.z);
            oscSender.send(host, OSC_PORT, base + "/rotation", t.rx, t.ry, t.rz);
        }
    }

    @Override public void onError(String message) {
        runOnUiThread(() -> statusText.setText(message));
    }

    @Override protected void onDestroy() {
        streaming = false;
        cameraExecutor.shutdownNow();
        oscSender.close();
        if (poseProcessor != null) poseProcessor.close();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            statusText.setText("Camera permission is required for body tracking");
        }
    }
}
