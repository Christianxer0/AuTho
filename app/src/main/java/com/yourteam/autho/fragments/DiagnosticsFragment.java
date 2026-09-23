package com.yourteam.autho.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.yourteam.autho.R;
import com.yourteam.autho.utils.NativeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiagnosticsFragment extends Fragment {

    private static final int ASENSOR_TYPE_ACCELEROMETER = 1; // mirrors android/sensor.h

    private TextView tvTestStatus, tvResults;
    private TextView tvLcdStatus, tvSpeakerStatus, tvMicStatus, tvCameraStatus;
    private TextView tvSensorStatus, tvFlashStatus, tvVibrationStatus;
    private Button btnLcdTest, btnSpeakerTest, btnMicTest, btnCameraTest;
    private Button btnSensorTest, btnFlashTest, btnVibrationTest, btnRunAllTests;

    private final List<String> testResults = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Native tests BLOCK for their duration -> never run them on the main thread.
    private final ExecutorService testExecutor = Executors.newSingleThreadExecutor();

    private boolean isRunning = false;

    private static final int REQUEST_PERMISSIONS = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.VIBRATE
    };

    private interface TestDone { void onDone(); }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_diagnostics, container, false);
        initViews(view);
        setupListeners();
        checkPermissions();
        return view;
    }

    private void initViews(View view) {
        tvTestStatus = view.findViewById(R.id.tvTestStatus);
        tvResults = view.findViewById(R.id.tvResults);
        tvLcdStatus = view.findViewById(R.id.tvLcdStatus);
        tvSpeakerStatus = view.findViewById(R.id.tvSpeakerStatus);
        tvMicStatus = view.findViewById(R.id.tvMicStatus);
        tvCameraStatus = view.findViewById(R.id.tvCameraStatus);
        tvSensorStatus = view.findViewById(R.id.tvSensorStatus);
        tvFlashStatus = view.findViewById(R.id.tvFlashStatus);
        tvVibrationStatus = view.findViewById(R.id.tvVibrationStatus);
        btnLcdTest = view.findViewById(R.id.btnLcdTest);
        btnSpeakerTest = view.findViewById(R.id.btnSpeakerTest);
        btnMicTest = view.findViewById(R.id.btnMicTest);
        btnCameraTest = view.findViewById(R.id.btnCameraTest);
        btnSensorTest = view.findViewById(R.id.btnSensorTest);
        btnFlashTest = view.findViewById(R.id.btnFlashTest);
        btnVibrationTest = view.findViewById(R.id.btnVibrationTest);
        btnRunAllTests = view.findViewById(R.id.btnRunAllTests);
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
                            "Permission denied: " + permissions[i], Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // ---------------- UI helpers ----------------

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
        String line = (passed ? "✅ " : "❌ ") + name + ": " + (passed ? "PASS" : "FAIL");
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
        tvTestStatus.setText(running ? " Running tests..." : "✅ Ready");
        btnRunAllTests.setEnabled(!running);
    }

    private boolean beginTest(String label, TextView statusTv) {
        if (isRunning) return false;
        setRunning(true);
        tvTestStatus.setText(label);
        statusTv.setText(" Testing...");
        statusTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        return true;
    }

    private void finishTest(TextView statusTv, boolean ok, String name, String details,
                            TestDone done) {
        setStatus(statusTv, ok ? " PASS" : " FAIL", ok);
        addResult(name, ok, details);
        setRunning(false);
        if (done != null) done.onDone();
    }

    // ---------------- Individual tests (native calls on background thread) ----------------

    private void runLcdTest(TestDone done) {
        if (!beginTest("🖥️ Testing LCD...", tvLcdStatus)) return;
        testExecutor.execute(() -> {
            boolean ok = false;
            String details = null;
            try {
                ok = NativeHelper.testDisplay(0, 1000);
                if (!ok) details = "fbdev blocked (API 26+) or no root";
            } catch (Throwable t) {
                details = t.getMessage();
            }
            final boolean fOk = ok;
            final String fDetails = details;
            handler.post(() -> finishTest(tvLcdStatus, fOk, "LCD Display", fDetails, done));
        });
    }

    private void runSpeakerTest(TestDone done) {
        if (!beginTest("🔊 Testing Speaker...", tvSpeakerStatus)) return;
        testExecutor.execute(() -> {
            boolean ok;
            try {
                ok = NativeHelper.testAudio(440, 1000);
            } catch (Throwable t) {
                ok = false;
            }
            final boolean fOk = ok;
            handler.post(() -> finishTest(tvSpeakerStatus, fOk, "Speaker", null, done));
        });
    }

    private void runMicTest(TestDone done) {
        if (!beginTest("🎤 Testing Microphone (speak now)...", tvMicStatus)) return;
        testExecutor.execute(() -> {
            float[] r = null;
            try {
                r = NativeHelper.testMicrophone(2000);
            } catch (Throwable ignored) { }
            // r[0]=RMS  r[1]=peak  r[2]=mean|amp|  r[3]=frames
            final boolean ok = r != null && r.length >= 2 && r[0] > 0.03f;
            final String details = ok
                    ? String.format("RMS %.3f, Peak %.2f", r[0], r[1])
                    : "No input detected";
            handler.post(() -> finishTest(tvMicStatus, ok, "Microphone", details, done));
        });
    }

    private void runCameraTest(TestDone done) {
        if (!beginTest("📸 Testing Camera...", tvCameraStatus)) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            finishTest(tvCameraStatus, false, "Camera", "Permission denied", done);
            return;
        }
        testExecutor.execute(() -> {
            PackageManager pm = requireContext().getPackageManager();
            boolean hasFeature = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
            boolean nativeOk = false;
            try {
                nativeOk = NativeHelper.testCamera(0, 800);
            } catch (Throwable ignored) { }
            final boolean ok = hasFeature && nativeOk;
            final String details = ok ? "HAL responsive"
                    : (!hasFeature ? "No camera hardware" : "Camera open failed");
            handler.post(() -> finishTest(tvCameraStatus, ok, "Camera", details, done));
        });
    }

    private void runSensorTest(TestDone done) {
        if (!beginTest("📡 Testing Sensors...", tvSensorStatus)) return;
        testExecutor.execute(() -> {
            float[] v = null;
            try {
                v = NativeHelper.testSensor(ASENSOR_TYPE_ACCELEROMETER, 1000);
            } catch (Throwable ignored) { }
            // v[0]=event count  v[1]=rate Hz  v[2..4]=x,y,z
            final boolean ok = v != null && v.length >= 5 && v[0] > 0;
            final String details = ok
                    ? String.format("%d events, x=%.2f y=%.2f z=%.2f",
                    (int) v[0], v[2], v[3], v[4])
                    : "No sensor events";
            handler.post(() -> finishTest(tvSensorStatus, ok, "Sensors", details, done));
        });
    }

    private void runFlashTest(TestDone done) {
        if (!beginTest("💡 Testing Flashlight...", tvFlashStatus)) return;
        testExecutor.execute(() -> {
            boolean ok = false;
            String details = null;
            try {
                CameraManager cm = (CameraManager) requireContext()
                        .getSystemService(Context.CAMERA_SERVICE);
                if (cm != null && cm.getCameraIdList().length > 0
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    String id = null;
                    for (String cid : cm.getCameraIdList()) {
                        Boolean hasFlash = cm.getCameraCharacteristics(cid)
                                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                        if (hasFlash != null && hasFlash) { id = cid; break; }
                    }
                    if (id != null) {
                        cm.setTorchMode(id, true);
                        Thread.sleep(800);
                        cm.setTorchMode(id, false);
                        ok = true;
                    } else {
                        details = "No flash unit";
                    }
                }
            } catch (CameraAccessException | InterruptedException e) {
                // Fallback: native sysfs LED toggle (may need root)
                try {
                    ok = NativeHelper.testFlashlight(true);
                    Thread.sleep(800);
                    NativeHelper.testFlashlight(false);
                } catch (Throwable ignored) { ok = false; }
            }
            final boolean fOk = ok;
            final String fDetails = details;
            handler.post(() -> finishTest(tvFlashStatus, fOk, "Flashlight", fDetails, done));
        });
    }

    private void runVibrationTest(TestDone done) {
        if (!beginTest("📳 Testing Vibration...", tvVibrationStatus)) return;
        testExecutor.execute(() -> {
            boolean ok;
            try {
                ok = NativeHelper.testVibration(500);
            } catch (Throwable t) {
                ok = false;
            }
            final boolean fOk = ok;
            final String details = ok ? null : "No vibrator sysfs node (root?)";
            handler.post(() -> finishTest(tvVibrationStatus, fOk, "Vibration", details, done));
        });
    }

    // ---------------- Run All (sequential chaining, no fixed delays) ----------------

    private void runAllTests() {
        if (isRunning) return;
        clearResults();
        setRunning(true);
        tvTestStatus.setText(" Running all tests...");

        runLcdTest(() -> runSpeakerTest(() -> runMicTest(() -> runCameraTest(() ->
                runSensorTest(() -> runFlashTest(() -> runVibrationTest(() ->
                        handler.post(() -> {
                            tvTestStatus.setText(" All tests complete!");
                            btnRunAllTests.setEnabled(true);
                            isRunning = false;
                        }))))))));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        testExecutor.shutdownNow();
    }
}