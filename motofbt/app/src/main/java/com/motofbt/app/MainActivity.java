package com.motofbt.app;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
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
    private Spinner trackingModeSpinner;
    private Spinner qualitySpinner;
    private Spinner smoothingSpinner;
    private CheckBox floorLock;
    private Button streamButton;
    private Button cameraButton;

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private ProcessCameraProvider cameraProvider;
    private PoseProcessor poseProcessor;
    private final TrackerMapper mapper = new TrackerMapper();
    private final OscSender oscSender = new OscSender();

    private volatile PoseLandmarkerResult latestResult;
    private volatile boolean streaming = false;
    private volatile long lastUiUpdate = 0L;
    private volatile long lastOscSend = 0L;
    private volatile int lensFacing = CameraSelector.LENS_FACING_BACK;
    private volatile int trackingMode = TrackerMapper.MODE_3;
    private volatile int smoothingMode = TrackerMapper.SMOOTH_BALANCED;
    private volatile boolean floorLockEnabled = true;
    private volatile int qualityMode = PoseProcessor.QUALITY_BALANCED;

    private long fpsWindowStart = 0L;
    private int fpsFrames = 0;
    private int measuredFps = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        statusText = findViewById(R.id.statusText);
        ipField = findViewById(R.id.ipField);
        heightField = findViewById(R.id.heightField);
        trackingModeSpinner = findViewById(R.id.trackingModeSpinner);
        qualitySpinner = findViewById(R.id.qualitySpinner);
        smoothingSpinner = findViewById(R.id.smoothingSpinner);
        floorLock = findViewById(R.id.floorLock);
        streamButton = findViewById(R.id.streamButton);
        cameraButton = findViewById(R.id.cameraButton);
        Button calibrateButton = findViewById(R.id.calibrateButton);

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        ipField.setText(prefs.getString("quest_ip", ""));
        heightField.setText(prefs.getString("height_cm", "170"));

        lensFacing = prefs.getInt("lens", CameraSelector.LENS_FACING_FRONT);
        int savedTracking = prefs.getInt("tracking_mode", 1);
        int savedQuality = prefs.getInt("quality", 1);
        int savedSmoothing = prefs.getInt("smoothing", 1);
        boolean savedFloorLock = prefs.getBoolean("floor_lock", true);

        trackingModeSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "3 trackers • hip + feet",
                        "6 trackers • + chest + knees",
                        "8 trackers • + elbows"
                }
        ));
        qualitySpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "Fast • Lite model",
                        "Balanced • Full model",
                        "Accurate • Full model + stricter lock"
                }
        ));
        smoothingSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "Responsive • less smoothing",
                        "Balanced",
                        "Stable • strongest smoothing"
                }
        ));

        trackingModeSpinner.setSelection(savedTracking);
        qualitySpinner.setSelection(savedQuality);
        smoothingSpinner.setSelection(savedSmoothing);
        floorLock.setChecked(savedFloorLock);

        trackingMode = trackerModeFromIndex(savedTracking);
        qualityMode = savedQuality;
        smoothingMode = savedSmoothing;
        floorLockEnabled = savedFloorLock;

        updateCameraButton();

        calibrateButton.setOnClickListener(v -> calibrate());
        streamButton.setOnClickListener(v -> toggleStreaming());
        cameraButton.setOnClickListener(v -> switchCamera());

        trackingModeSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            trackingMode = trackerModeFromIndex(position);
            mapper.resetFilters();
            saveSettings();
        }));

        smoothingSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            smoothingMode = position;
            mapper.resetFilters();
            saveSettings();
        }));

        qualitySpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            if (qualityMode != position) {
                qualityMode = position;
                saveSettings();
                if (cameraProvider != null) {
                    rebuildPoseProcessorAndCamera();
                }
            }
        }));

        floorLock.setOnCheckedChangeListener((button, checked) -> {
            floorLockEnabled = checked;
            mapper.resetFilters();
            saveSettings();
        });

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
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                rebuildPoseProcessorAndCamera();
            } catch (Exception e) {
                statusText.setText("Camera error: " + safeMessage(e));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void rebuildPoseProcessorAndCamera() {
        streaming = false;
        streamButton.setText("Start OSC");
        mapper.resetFilters();

        if (poseProcessor != null) {
            poseProcessor.close();
            poseProcessor = null;
        }

        try {
            poseProcessor = new PoseProcessor(this, this, qualityMode);
        } catch (Exception e) {
            statusText.setText("Pose model failed: " + safeMessage(e));
            return;
        }

        bindCamera();
    }

    private void bindCamera() {
        if (cameraProvider == null || poseProcessor == null) return;

        try {
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build();

            PoseProcessor activeProcessor = poseProcessor;
            analysis.setAnalyzer(cameraExecutor, activeProcessor::process);

            CameraSelector selector = new CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build();

            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, selector, preview, analysis);

            boolean front = lensFacing == CameraSelector.LENS_FACING_FRONT;
            overlayView.setMirror(front);
            updateCameraButton();
            statusText.setText((front ? "Front" : "Back")
                    + " camera ready • fit your whole body in frame");
        } catch (Exception e) {
            statusText.setText("Camera bind error: " + safeMessage(e));
        }
    }

    private void switchCamera() {
        if (streaming) {
            streaming = false;
            streamButton.setText("Start OSC");
        }

        int wanted = lensFacing == CameraSelector.LENS_FACING_FRONT
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;

        try {
            CameraSelector wantedSelector = new CameraSelector.Builder()
                    .requireLensFacing(wanted)
                    .build();

            if (cameraProvider != null && cameraProvider.hasCamera(wantedSelector)) {
                lensFacing = wanted;
                mapper.resetFilters();
                saveSettings();
                bindCamera();
            } else {
                statusText.setText("That camera is not available on this phone");
            }
        } catch (Exception e) {
            statusText.setText("Camera switch failed: " + safeMessage(e));
        }
    }

    private void updateCameraButton() {
        if (cameraButton == null) return;
        cameraButton.setText(lensFacing == CameraSelector.LENS_FACING_FRONT
                ? "Camera: FRONT"
                : "Camera: BACK");
    }

    private void calibrate() {
        PoseLandmarkerResult result = latestResult;
        if (result == null || result.worldLandmarks().isEmpty()) {
            statusText.setText("No full body detected yet");
            return;
        }

        float heightMeters;
        try {
            heightMeters = Float.parseFloat(heightField.getText().toString().trim()) / 100f;
            if (heightMeters < 0.8f || heightMeters > 2.5f) throw new NumberFormatException();
        } catch (Exception e) {
            statusText.setText("Enter a valid height in cm");
            return;
        }

        boolean ok = mapper.calibrate(result, heightMeters);
        if (ok) {
            saveSettings();
            statusText.setText("Calibrated • keep the phone fixed in place");
        } else {
            statusText.setText("Calibration failed • stand straight with feet visible");
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
            statusText.setText("Streaming "
                    + trackingMode
                    + " trackers → "
                    + ip
                    + ":"
                    + OSC_PORT);
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
                .putInt("lens", lensFacing)
                .putInt("tracking_mode", trackingModeSpinner == null
                        ? 1 : trackingModeSpinner.getSelectedItemPosition())
                .putInt("quality", qualitySpinner == null
                        ? 1 : qualitySpinner.getSelectedItemPosition())
                .putInt("smoothing", smoothingSpinner == null
                        ? 1 : smoothingSpinner.getSelectedItemPosition())
                .putBoolean("floor_lock", floorLockEnabled)
                .apply();
    }

    private int trackerModeFromIndex(int position) {
        if (position == 2) return TrackerMapper.MODE_8;
        if (position == 1) return TrackerMapper.MODE_6;
        return TrackerMapper.MODE_3;
    }

    @Override public void onPose(PoseLandmarkerResult result) {
        latestResult = result;
        long now = android.os.SystemClock.uptimeMillis();

        if (fpsWindowStart == 0L) fpsWindowStart = now;
        fpsFrames++;
        if (now - fpsWindowStart >= 1000L) {
            measuredFps = fpsFrames;
            fpsFrames = 0;
            fpsWindowStart = now;
        }

        if (now - lastUiUpdate > 100L) {
            lastUiUpdate = now;
            runOnUiThread(() -> {
                overlayView.setResult(result);
                if (!streaming) {
                    String camera = lensFacing == CameraSelector.LENS_FACING_FRONT
                            ? "FRONT" : "BACK";
                    statusText.setText("Pose locked • "
                            + measuredFps
                            + " fps • "
                            + camera
                            + " • "
                            + trackingMode
                            + " trackers"
                            + (mapper.isCalibrated() ? " • calibrated" : " • tap Calibrate"));
                }
            });
        }

        if (!streaming || now - lastOscSend < 30L) return;
        lastOscSend = now;

        List<TrackerMapper.Tracker> trackers =
                mapper.map(result, trackingMode, smoothingMode, floorLockEnabled);

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

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @Override protected void onDestroy() {
        streaming = false;
        cameraExecutor.shutdownNow();
        oscSender.close();
        if (cameraProvider != null) cameraProvider.unbindAll();
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

    private static final class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {
        interface Callback {
            void selected(int position);
        }

        private final Callback callback;

        SimpleItemSelectedListener(Callback callback) {
            this.callback = callback;
        }

        @Override public void onItemSelected(
                android.widget.AdapterView<?> parent,
                android.view.View view,
                int position,
                long id
        ) {
            callback.selected(position);
        }

        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }
}
