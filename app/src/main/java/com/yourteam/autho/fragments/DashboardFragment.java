package com.yourteam.autho.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DashboardFragment extends Fragment {

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

    // Background thread for metric collection
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean isUpdating = false;

    // Data holder for all metrics
    private static class MetricsData {
        int cpuUsage;
        float cpuTemp;
        long[] ramInfo;          // [total, used, free]
        long[] batteryInfo;      // [level, status, health, temp]
        long[] storageInfo;      // [total, used, free]
        String wifiSSID;
        int wifiSignal;
        String wifiIP;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        NativeHelper.init(requireContext());
        initViews(view);
        setupWelcomeMessage();
        startMonitoring();
        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backgroundThread = new HandlerThread("MetricsThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void initViews(@NonNull View view) {
        // Welcome
        tvWelcome = view.findViewById(R.id.tvWelcome);
        tvDeviceInfo = view.findViewById(R.id.tvDeviceInfo);

        // CPU
        tvCpuUsage = view.findViewById(R.id.tvCpuUsage);
        tvCpuTemp = view.findViewById(R.id.tvCpuTemp);
        progressCpu = view.findViewById(R.id.progressCpu);

        // RAM
        tvRamUsage = view.findViewById(R.id.tvRamUsage);
        tvRamDetails = view.findViewById(R.id.tvRamDetails);
        progressRam = view.findViewById(R.id.progressRam);

        // Battery
        tvBatteryLevel = view.findViewById(R.id.tvBatteryLevel);
        tvBatteryStatus = view.findViewById(R.id.tvBatteryStatus);
        progressBattery = view.findViewById(R.id.progressBattery);

        // Storage
        tvStorageUsage = view.findViewById(R.id.tvStorageUsage);
        tvStorageDetails = view.findViewById(R.id.tvStorageDetails);
        progressStorage = view.findViewById(R.id.progressStorage);

        // Network
        tvWifiSSID = view.findViewById(R.id.tvWifiSSID);
        tvWifiSignal = view.findViewById(R.id.tvWifiSignal);
        tvWifiIP = view.findViewById(R.id.tvWifiIP);
    }

    private void setupWelcomeMessage() {
        String username = null;
        if (getActivity() != null && getActivity().getIntent() != null) {
            username = getActivity().getIntent().getStringExtra("username");
        }

        if (username != null && !username.isEmpty()) {
            tvWelcome.setText("Welcome, " + username + "!");
        } else {
            tvWelcome.setText("Welcome Back!");
        }

        // Device info – these are fast stubs, safe on UI thread
        String model = NativeHelper.getDeviceModel();
        String kernel = NativeHelper.getKernelVersion();
        int cores = NativeHelper.getCpuCoreCount();

        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        String time = sdf.format(new Date());

        tvDeviceInfo.setText(model + " · " + cores + " cores · " + time);
    }

    private void startMonitoring() {
        if (isUpdating) return;
        isUpdating = true;

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isUpdating || !isAdded()) return;
                // Collect all data on background thread
                final MetricsData data = collectMetrics();
                // Update UI on main thread
                uiHandler.post(() -> updateUI(data));
                // Schedule next update
                backgroundHandler.postDelayed(this, 2000);
            }
        });
    }

    private MetricsData collectMetrics() {
        MetricsData data = new MetricsData();

        // CPU
        data.cpuUsage = NativeHelper.getCpuUsage();
        data.cpuTemp = NativeHelper.getCpuTemperature();

        // RAM
        data.ramInfo = NativeHelper.getRamInfo();

        // Battery – assuming getBatteryInfoJava() exists in NativeHelper
        data.batteryInfo = NativeHelper.getBatteryInfoJava();

        // Storage
        data.storageInfo = NativeHelper.getStorageInfoJava();

        // Network
        data.wifiSSID = NativeHelper.getWifiSSID();
        data.wifiSignal = NativeHelper.getWifiSignalStrength();
        data.wifiIP = NativeHelper.getWifiIPAddress();

        return data;
    }

    private void updateUI(MetricsData data) {
        if (getContext() == null) return;

        // ==================== CPU ====================
        if (data.cpuUsage >= 0) {
            tvCpuUsage.setText(data.cpuUsage + "%");
            progressCpu.setProgress(data.cpuUsage);
        }
        if (data.cpuTemp > 0) {
            tvCpuTemp.setText("Temp: " + String.format(Locale.getDefault(), "%.1f", data.cpuTemp) + "°C");
        }

        // ==================== RAM ====================
        if (data.ramInfo != null && data.ramInfo.length >= 3) {
            long totalRAM = data.ramInfo[0];
            long usedRAM = data.ramInfo[1];
            long freeRAM = data.ramInfo[2];
            int percent = (int) ((usedRAM * 100) / totalRAM);

            tvRamUsage.setText(NativeHelper.formatBytes(usedRAM));
            tvRamDetails.setText(NativeHelper.formatBytes(usedRAM) + " / " + NativeHelper.formatBytes(totalRAM));
            progressRam.setProgress(percent);
        }

        // ==================== Battery ====================
        if (data.batteryInfo != null && data.batteryInfo.length >= 4) {
            int level = (int) data.batteryInfo[0];
            int status = (int) data.batteryInfo[1];
            int health = (int) data.batteryInfo[2];
            float temp = data.batteryInfo[3]; // assuming Java returns float as long? We'll treat as float.

            tvBatteryLevel.setText(level + "%");
            progressBattery.setProgress(level);

            String statusText = NativeHelper.getBatteryStatusString(status);
            String healthText = NativeHelper.getBatteryHealthString(health);
            tvBatteryStatus.setText(statusText + " · " + healthText + " · " +
                    String.format(Locale.getDefault(), "%.1f", temp) + "°C");

            // Color based on level
            if (level > 50) {
                tvBatteryLevel.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green));
                progressBattery.setProgressTintList(ContextCompat.getColorStateList(requireContext(), R.color.status_green));
            } else if (level > 20) {
                tvBatteryLevel.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_yellow));
                progressBattery.setProgressTintList(ContextCompat.getColorStateList(requireContext(), R.color.status_yellow));
            } else {
                tvBatteryLevel.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_red));
                progressBattery.setProgressTintList(ContextCompat.getColorStateList(requireContext(), R.color.status_red));
            }
        }

        // ==================== Storage ====================
        if (data.storageInfo != null && data.storageInfo.length >= 3) {
            long total = data.storageInfo[0];
            long used = data.storageInfo[1];
            long free = data.storageInfo[2];
            int percent = (int) ((used * 100) / total);

            tvStorageUsage.setText(percent + "%");
            tvStorageDetails.setText(NativeHelper.formatBytes(used) + " / " + NativeHelper.formatBytes(total));
            progressStorage.setProgress(percent);

            if (percent > 80) {
                tvStorageUsage.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_red));
                progressStorage.setProgressTintList(ContextCompat.getColorStateList(requireContext(), R.color.status_red));
            } else if (percent > 60) {
                tvStorageUsage.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_yellow));
                progressStorage.setProgressTintList(ContextCompat.getColorStateList(requireContext(), R.color.status_yellow));
            } else {
                tvStorageUsage.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green));
                progressStorage.setProgressTintList(ContextCompat.getColorStateList(requireContext(), R.color.status_green));
            }
        }

        // ==================== Network ====================
        if (data.wifiSSID != null && !data.wifiSSID.isEmpty() && !data.wifiSSID.equalsIgnoreCase("<unknown ssid>")) {
            tvWifiSSID.setText(data.wifiSSID);
        } else {
            tvWifiSSID.setText("Not Connected");
        }

        if (data.wifiSignal >= 0) {
            String signalText;
            if (data.wifiSignal > 75) signalText = "Excellent";
            else if (data.wifiSignal > 50) signalText = "Good";
            else if (data.wifiSignal > 25) signalText = "Fair";
            else signalText = "Weak";
            tvWifiSignal.setText(signalText + " (" + data.wifiSignal + "%)");
        } else {
            tvWifiSignal.setText("No Signal");
        }

        if (data.wifiIP != null && !data.wifiIP.isEmpty() && !data.wifiIP.equals("0.0.0.0")) {
            tvWifiIP.setText(data.wifiIP);
        } else {
            tvWifiIP.setText("No IP");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isUpdating) {
            startMonitoring();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isUpdating = false;
        if (backgroundHandler != null) {
            backgroundHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isUpdating = false;
        if (backgroundHandler != null) {
            backgroundHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
        }
    }
}