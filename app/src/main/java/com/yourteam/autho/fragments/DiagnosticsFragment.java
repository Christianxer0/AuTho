package com.yourteam.autho.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.yourteam.autho.R;
import com.yourteam.autho.activities.DisplayTestActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiagnosticsFragment extends Fragment implements SensorEventListener {

    private TextView tvTestStatus, tvResults;
    private TextView tvLcdStatus, tvSpeakerStatus, tvMicStatus, tvCameraStatus;
    private TextView tvSensorStatus, tvFlashStatus, tvVibrationStatus;
    private Button btnLcdTest, btnSpeakerTest, btnMicTest, btnCameraTest;
    private Button btnSensorTest, btnFlashTest, btnVibrationTest, btnRunAllTests;

    private final List<String> testResults = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ExecutorService testExecutor = Executors.newSingleThreadExecutor();

    private boolean isRunning = false;

    // Sensor sampling
    private SensorManager sensorManager;
    private volatile int sensorEventCount = 0;
    private volatile float lastX, lastY, lastZ;

    // LCD test result launcher
    private ActivityResultLauncher<Intent> displayTestLauncher;
    private TestDone lcdCallback;

    private static final int REQUEST_PERMISSIONS = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.VIBRATE
    };

    private interface TestDone { void onDone(); }

    // ============================================================
    // LIFECYCLE
    // ============================================================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register result launcher for DisplayTestActivity
        displayTestLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    boolean passed = result.getResultCode() == Activity.RESULT_OK;
                    finishTest(tvLcdStatus, passed, "LCD Display",
                            passed ? "All colors verified" : "User reported issues",
                            lcdCallback);
                    lcdCallback = null;
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_diagnostics, container, false);
        initViews(view);
        setupListeners();
        checkPermissions();
        sensorManager = (SensorManager) requireContext()
                .getSystemService(Context.SENSOR_SERVICE);
        return view;
    }

    private void initViews(View view) {
        tvTestStatus      = view.findViewById(R.id.tvTestStatus);
        tvResults         = view.findViewById(R.id.tvResults);
        tvLcdStatus       = view.findViewById(R.id.tvLcdStatus);
        tvSpeakerStatus   = view.findViewById(R.id.tvSpeakerStatus);
        tvMicStatus       = view.findViewById(R.id.tvMicStatus);
        tvCameraStatus    = view.findViewById(R.id.tvCameraStatus);
        tvSensorStatus    = view.findViewById(R.id.tvSensorStatus);
        tvFlashStatus     = view.findViewById(R.id.tvFlashStatus);
        tvVibrationStatus = view.findViewById(R.id.tvVibrationStatus);

        btnLcdTest        = view.findViewById(R.id.btnLcdTest);
        btnSpeakerTest    = view.findViewById(R.id.btnSpeakerTest);
        btnMicTest        = view.findViewById(R.id.btnMicTest);
        btnCameraTest     = view.findViewById(R.id.btnCameraTest);
        btnSensorTest     = view.findViewById(R.id.btnSensorTest);
        btnFlashTest      = view.findViewById(R.id.btnFlashTest);
        btnVibrationTest  = view.findViewById(R.id.btnVibrationTest);
        btnRunAllTests    = view.findViewById(R.id.btnRunAllTests);

        resetAllStatuses();
    }

    private void setupListeners() {
        btnLcdTest.setOnClickListener(v -> runLcdTest(null));
        btnSpeakerTest.setOnClickListener(v -> runSpeakerTest(null));
        btnMicTest.setOnClickListener(v -> runMicTest(null));
        btnCameraTest.setOnClickListener(v -> runCameraTest(null));
        btnSensorTest.setOnClickListener(v -> runSensorTest(null));
        btnFlashTest.setOnClickListener(v -> runFlashTest(null));
        btnVibrationTest.setOnClickListener(v -> runVibrationTest(null));
        btnRunAllTests.setOnClickListener(v -> runAllTests());
    }

    private void checkPermissions() {
        List<String> missing = new ArrayList<>();
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), p)
                    != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(),
                    missing.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(requireContext(),
                            "Permission denied: " + permissions[i],
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // ============================================================
    // UI HELPERS
    // ============================================================

    private void resetAllStatuses() {
        TextView[] statuses = {tvLcdStatus, tvSpeakerStatus, tvMicStatus, tvCameraStatus,
                tvSensorStatus, tvFlashStatus, tvVibrationStatus};
        for (TextView tv : statuses) {
            tv.setText("⏳ Ready");
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        }
    }

    private void setStatus(TextView tv, String text, boolean success) {
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(requireContext(),
                success ? R.color.status_green : R.color.status_red));
    }

    private void addResult(String name, boolean passed, String details) {
        String line = (passed ? "✅ " : "❌ ") + name + ": "
                + (passed ? "PASS" : "FAIL");
        if (details != null && !details.isEmpty()) line += " — " + details;
        testResults.add(line);
        StringBuilder sb = new StringBuilder();
        for (String r : testResults) sb.append(r).append('\n');
        tvResults.setText(sb.toString());
    }

    private void clearResults() {
        testResults.clear();
        tvResults.setText("No tests run yet");
        resetAllStatuses();
    }

    private void setRunning(boolean running) {
        isRunning = running;
        tvTestStatus.setText(running ? "🔄 Running tests..." : "✅ Ready");
        btnRunAllTests.setEnabled(!running);
    }

    private boolean beginTest(String label, TextView statusTv) {
        if (isRunning) return false;
        setRunning(true);
        tvTestStatus.setText(label);
        statusTv.setText("🔄 Testing...");
        statusTv.setTextColor(ContextCompat.getColor(requireContext(),
                R.color.text_secondary));
        return true;
    }

    private void finishTest(TextView statusTv, boolean ok, String name,
                            String details, TestDone done) {
        if (!isAdded() || getContext() == null) return;
        setStatus(statusTv, ok ? "✅ PASS" : "❌ FAIL", ok);
        addResult(name, ok, details);
        setRunning(false);
        if (done != null) done.onDone();
    }

    // ============================================================
    // 1. LCD DISPLAY TEST — launches full-screen color Activity
    // ============================================================

    private void runLcdTest(TestDone done) {
        if (!beginTest("🖥️ Testing LCD...", tvLcdStatus)) return;

        // Store callback for when Activity returns
        lcdCallback = done;

        Intent intent = new Intent(requireContext(), DisplayTestActivity.class);
        displayTestLauncher.launch(intent);
    }

    // ============================================================
    // 2. SPEAKER TEST — AudioTrack (works everywhere)
    // ============================================================

    private void runSpeakerTest(TestDone done) {
        if (!beginTest("🔊 Testing Speaker...", tvSpeakerStatus)) return;
        testExecutor.execute(() -> {
            boolean ok = playTestTone(440, 1000);
            final boolean fOk = ok;
            handler.post(() -> finishTest(tvSpeakerStatus, fOk, "Speaker",
                    fOk ? "440 Hz tone played" : "AudioTrack failed", done));
        });
    }

    private boolean playTestTone(int frequency, int durationMs) {
        AudioTrack audioTrack = null;
        try {
            int sampleRate = 44100;
            int minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize * 2,
                    AudioTrack.MODE_STATIC);

            int numSamples = (int) (durationMs * sampleRate / 1000.0);
            short[] samples = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                samples[i] = (short) (32767 * Math.sin(
                        2.0 * Math.PI * i * frequency / sampleRate));
            }

            audioTrack.write(samples, 0, samples.length);
            audioTrack.play();
            Thread.sleep(durationMs);
            audioTrack.stop();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (audioTrack != null) {
                try { audioTrack.release(); } catch (Exception ignored) {}
            }
        }
    }

    // ============================================================
    // 3. MICROPHONE TEST — AudioRecord (lower threshold, 3 s)
    // ============================================================

    private void runMicTest(TestDone done) {
        if (!beginTest("🎤 Testing Microphone (speak now)...", tvMicStatus)) return;
        testExecutor.execute(() -> {
            float peak = recordAndMeasureMic(3000);
            // Lowered threshold — any input > 0.005 passes
            final boolean ok = peak > 0.005f;
            final String details = ok
                    ? String.format(Locale.US, "Peak %.3f", peak)
                    : String.format(Locale.US, "No input (peak %.4f)", peak);
            handler.post(() -> finishTest(tvMicStatus, ok, "Microphone", details, done));
        });
    }

    private float recordAndMeasureMic(int durationMs) {
        AudioRecord audioRecord = null;
        try {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                return 0f;
            }

            int sampleRate = 44100;
            int minBufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize * 2);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                return 0f;
            }

            short[] buffer = new short[minBufferSize];
            audioRecord.startRecording();

            float peak = 0;
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < durationMs) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    for (int i = 0; i < read; i++) {
                        float amp = Math.abs(buffer[i]) / 32767f;
                        if (amp > peak) peak = amp;
                    }
                }
            }
            audioRecord.stop();
            return peak;
        } catch (Exception e) {
            return 0f;
        } finally {
            if (audioRecord != null) {
                try { audioRecord.release(); } catch (Exception ignored) {}
            }
        }
    }

    // ============================================================
    // 4. CAMERA TEST — CameraManager enumeration
    // ============================================================

    private void runCameraTest(TestDone done) {
        if (!beginTest("📸 Testing Camera...", tvCameraStatus)) return;

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            finishTest(tvCameraStatus, false, "Camera",
                    "Permission denied", done);
            return;
        }

        testExecutor.execute(() -> {
            String result = checkCamera();
            final boolean ok = result != null;
            final String details = ok ? result : "No camera detected";
            handler.post(() -> finishTest(tvCameraStatus, ok, "Camera", details, done));
        });
    }

    private String checkCamera() {
        try {
            CameraManager manager = (CameraManager) requireContext()
                    .getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return null;

            String[] ids = manager.getCameraIdList();
            if (ids.length == 0) return null;

            StringBuilder sb = new StringBuilder();
            sb.append(ids.length).append(" camera(s): ");
            for (String id : ids) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    switch (facing) {
                        case CameraCharacteristics.LENS_FACING_FRONT:
                            sb.append("Front ");
                            break;
                        case CameraCharacteristics.LENS_FACING_BACK:
                            sb.append("Back ");
                            break;
                        default:
                            sb.append("External ");
                            break;
                    }
                }
            }
            return sb.toString().trim();
        } catch (CameraAccessException e) {
            return null;
        }
    }

    // ============================================================
    // 5. SENSOR TEST — SensorManager listener
    // ============================================================

    private void runSensorTest(TestDone done) {
        if (!beginTest("📡 Testing Sensors...", tvSensorStatus)) return;

        if (sensorManager == null) {
            finishTest(tvSensorStatus, false, "Sensors",
                    "SensorManager unavailable", done);
            return;
        }

        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accel == null) {
            finishTest(tvSensorStatus, false, "Sensors",
                    "No accelerometer", done);
            return;
        }

        sensorEventCount = 0;
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);

        handler.postDelayed(() -> {
            sensorManager.unregisterListener(this);
            boolean ok = sensorEventCount > 5;
            String details = ok
                    ? String.format(Locale.US, "%d events, x=%.2f y=%.2f z=%.2f",
                    sensorEventCount, lastX, lastY, lastZ)
                    : "Only " + sensorEventCount + " events";
            finishTest(tvSensorStatus, ok, "Sensors", details, done);
        }, 2000);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        sensorEventCount++;
        if (event.values.length >= 3) {
            lastX = event.values[0];
            lastY = event.values[1];
            lastZ = event.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // ============================================================
    // 6. FLASHLIGHT TEST — CameraManager.setTorchMode()
    // ============================================================

    private void runFlashTest(TestDone done) {
        if (!beginTest("💡 Testing Flashlight...", tvFlashStatus)) return;
        testExecutor.execute(() -> {
            boolean ok = false;
            String details = null;
            try {
                CameraManager cm = (CameraManager) requireContext()
                        .getSystemService(Context.CAMERA_SERVICE);
                if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    String id = null;
                    for (String cid : cm.getCameraIdList()) {
                        Boolean hasFlash = cm.getCameraCharacteristics(cid)
                                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                        if (hasFlash != null && hasFlash) {
                            id = cid;
                            break;
                        }
                    }
                    if (id != null) {
                        cm.setTorchMode(id, true);
                        Thread.sleep(1200);
                        cm.setTorchMode(id, false);
                        ok = true;
                    } else {
                        details = "No flash unit";
                    }
                }
            } catch (Exception e) {
                details = e.getMessage();
            }
            final boolean fOk = ok;
            final String fDetails = details;
            handler.post(() -> finishTest(tvFlashStatus, fOk, "Flashlight",
                    fDetails, done));
        });
    }

    // ============================================================
    // 7. VIBRATION TEST — Vibrator API (NOT native sysfs)
    // ============================================================

    private void runVibrationTest(TestDone done) {
        if (!beginTest("📳 Testing Vibration...", tvVibrationStatus)) return;
        testExecutor.execute(() -> {
            boolean ok = false;
            try {
                Vibrator vibrator;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.os.VibratorManager vm =
                            (android.os.VibratorManager) requireContext()
                                    .getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                    vibrator = (vm != null) ? vm.getDefaultVibrator() : null;
                } else {
                    vibrator = (Vibrator) requireContext()
                            .getSystemService(Context.VIBRATOR_SERVICE);
                }

                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(
                                800, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(800);
                    }
                    Thread.sleep(1000);
                    ok = true;
                }
            } catch (Exception ignored) { }
            final boolean fOk = ok;
            final String details = fOk ? "Motor vibrated" : "No vibrator hardware";
            handler.post(() -> finishTest(tvVibrationStatus, fOk, "Vibration",
                    details, done));
        });
    }

    // ============================================================
    // RUN ALL TESTS — sequential chain
    // ============================================================

    private void runAllTests() {
        if (isRunning) return;
        clearResults();
        setRunning(true);
        tvTestStatus.setText("🔄 Running all tests...");

        runLcdTest(() -> runSpeakerTest(() -> runMicTest(() -> runCameraTest(() ->
                runSensorTest(() -> runFlashTest(() -> runVibrationTest(() ->
                        handler.post(() -> {
                            tvTestStatus.setText("✅ All tests complete!");
                            btnRunAllTests.setEnabled(true);
                            isRunning = false;
                        }))))))));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        handler.removeCallbacksAndMessages(null);
        testExecutor.shutdownNow();
    }
}