package com.yourteam.autho.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.yourteam.autho.R;
import com.yourteam.autho.utils.NativeHelper;
import com.yourteam.autho.utils.NativeMonitorCallback;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DashboardFragment extends Fragment implements NativeMonitorCallback {

    private static final int MONITOR_INTERVAL_MS = 250;   // 4 Hz = realtime feel

    // UI Components
    private TextView tvWelcome, tvDeviceInfo;
    private TextView tvCpuUsage, tvCpuTemp;
    private ProgressBar progressCpu;
    private TextView tvRamUsage, tvRamDetails;
    private ProgressBar progressRam;
    private TextView tvBatteryLevel, tvBatteryStatus;
    private ProgressBar progressBattery;
    private TextView tvStorageUsage, tvStorageDetails;
    private ProgressBar progressStorage;
    private TextView tvWifiSSID, tvWifiSignal, tvWifiIP;

    // All UI updates are marshalled here — the callback fires on a native thread
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        NativeHelper.init(requireContext());
        initViews(view);
        setupWelcomeMessage();
        return view;
    }

    private void initViews(@NonNull View view) {
        tvWelcome = view.findViewById(R.id.tvWelcome);
        tvDeviceInfo = view.findViewById(R.id.tvDeviceInfo);
        tvCpuUsage = view.findViewById(R.id.tvCpuUsage);
        tvCpuTemp = view.findViewById(R.id.tvCpuTemp);
        progressCpu = view.findViewById(R.id.progressCpu);
        tvRamUsage = view.findViewById(R.id.tvRamUsage);
        tvRamDetails = view.findViewById(R.id.tvRamDetails);
        progressRam = view.findViewById(R.id.progressRam);
        tvBatteryLevel = view.findViewById(R.id.tvBatteryLevel);
        tvBatteryStatus = view.findViewById(R.id.tvBatteryStatus);
        progressBattery = view.findViewById(R.id.progressBattery);
        tvStorageUsage = view.findViewById(R.id.tvStorageUsage);
        tvStorageDetails = view.findViewById(R.id.tvStorageDetails);
        progressStorage = view.findViewById(R.id.progressStorage);
        tvWifiSSID = view.findViewById(R.id.tvWifiSSID);
        tvWifiSignal = view.findViewById(R.id.tvWifiSignal);
        tvWifiIP = view.findViewById(R.id.tvWifiIP);
    }

    private void setupWelcomeMessage() {
        String username = null;
        if (getActivity() != null && getActivity().getIntent() != null) {
            username = getActivity().getIntent().getStringExtra("username");
        }
        tvWelcome.setText(username != null && !username.isEmpty()
                ? "Welcome, " + username + "!" : "Welcome Back!");

        String model = NativeHelper.getDeviceModel();
        int cores = NativeHelper.getCpuCoreCount();
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
        tvDeviceInfo.setText(model + " · " + cores + " cores · " + time);
    }

    // ==================== REALTIME MONITOR LIFECYCLE ====================

    @Override
    public void onResume() {
        super.onResume();
        NativeHelper.startRealtimeMonitoring(this, MONITOR_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        NativeHelper.stopRealtimeMonitoring();
        super.onPause();
    }

    // ==================== NATIVE CALLBACK (runs on native thread!) ====================

    @Override
    public void onUpdate(int cpuUsage, float cpuTemp,
                         long ramTotal, long ramUsed, long ramFree,
                         long rxBytesPerSec, long txBytesPerSec) {
        // MUST hop to the main thread before touching views
        uiHandler.post(() -> renderMetrics(cpuUsage, cpuTemp,
                ramTotal, ramUsed, ramFree, rxBytesPerSec, txBytesPerSec));
    }

    // ==================== UI RENDERING ====================

    private void renderMetrics(int cpuUsage, float cpuTemp,
                               long ramTotal, long ramUsed, long ramFree,
                               long rxBps, long txBps) {
        if (getContext() == null) return;

        // CPU
        if (cpuUsage >= 0) {
            tvCpuUsage.setText(cpuUsage + "%");
            progressCpu.setProgress(cpuUsage);
        }
        if (cpuTemp > 0) {
            tvCpuTemp.setText("Temp: " + String.format(Locale.getDefault(), "%.1f", cpuTemp) + "°C");
        }

        // RAM
        if (ramTotal > 0) {
            int percent = (int) (ramUsed * 100 / ramTotal);
            tvRamUsage.setText(percent + "%");
            tvRamDetails.setText(NativeHelper.formatBytes(ramUsed) + " / "
                    + NativeHelper.formatBytes(ramTotal));
            progressRam.setProgress(percent);
        }

        // Realtime network throughput — you can bind these to tvWifiSignal/details
        // e.g. tvWifiSignal.setText("↓ " + NativeHelper.formatBytes(rxBps) + "/s · ↑ "
        //                          + NativeHelper.formatBytes(txBps) + "/s");

        // Battery & Storage are slow-changing: refresh them on a slower cadence
        refreshSlowMetrics();
    }

    private long lastSlowRefresh = 0;
    private void refreshSlowMetrics() {
        long now = System.currentTimeMillis();
        if (now - lastSlowRefresh < 5000) return;
        lastSlowRefresh = now;

        long[] battery = NativeHelper.getBatteryInfoJava();
        if (battery != null && battery.length >= 4) {
            int level = (int) battery[0];
            int status = (int) battery[1];
            int health = (int) battery[2];
            float temp = battery[3];

            tvBatteryLevel.setText(level + "%");
            progressBattery.setProgress(level);
            tvBatteryStatus.setText(
                    NativeHelper.getBatteryStatusString(status) + " · "
                            + NativeHelper.getBatteryHealthString(health) + " · "
                            + String.format(Locale.getDefault(), "%.1f", temp) + "°C");

            int color;
            if (level > 50)      color = R.color.status_green;
            else if (level > 20) color = R.color.status_yellow;
            else                 color = R.color.status_red;
            tvBatteryLevel.setTextColor(ContextCompat.getColor(requireContext(), color));
            progressBattery.setProgressTintList(
                    ContextCompat.getColorStateList(requireContext(), color));
        }

        long[] storage = NativeHelper.getStorageInfoJava();
        if (storage != null && storage.length >= 3) {
            long total = storage[0], used = storage[1];
            int percent = (int) (used * 100 / total);
            tvStorageUsage.setText(percent + "%");
            tvStorageDetails.setText(NativeHelper.formatBytes(used) + " / "
                    + NativeHelper.formatBytes(total));
            progressStorage.setProgress(percent);
        }

        String ssid = NativeHelper.getWifiSSID();
        tvWifiSSID.setText(ssid != null && !ssid.isEmpty()
                && !ssid.equalsIgnoreCase("<unknown ssid>") ? ssid : "Not Connected");
        String ip = NativeHelper.getWifiIPAddress();
        tvWifiIP.setText(ip != null && !ip.equals("0.0.0.0") ? ip : "No IP");
    }
}