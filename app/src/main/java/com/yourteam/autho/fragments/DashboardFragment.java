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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private boolean isUpdating = false;

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

    private void initViews(View view) {
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

        // Get device info
        String model = NativeHelper.getDeviceModel();
        String kernel = NativeHelper.getKernelVersion();
        int cores = NativeHelper.getCpuCoreCount();

        // Time
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        String time = sdf.format(new Date());

        tvDeviceInfo.setText(model + " · " + cores + " cores · " + time);
    }

    private void startMonitoring() {
        if (isUpdating) return;
        isUpdating = true;

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isUpdating || !isAdded()) return;
                updateSystemMetrics();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updateRunnable);
    }

    private void updateSystemMetrics() {
        if (getContext() == null) return;

        // ==================== CPU ====================
        int cpuUsage = NativeHelper.getCpuUsage();
        if (cpuUsage >= 0) {
            tvCpuUsage.setText(cpuUsage + "%");
            progressCpu.setProgress(cpuUsage);
        }

        float cpuTemp = NativeHelper.getCpuTemperature();
        if (cpuTemp > 0) {
            tvCpuTemp.setText("Temp: " + String.format(Locale.getDefault(), "%.1f", cpuTemp) + "°C");
        }

        // ==================== RAM ====================
        long[] ramInfo = NativeHelper.getRamInfo();
        if (ramInfo != null && ramInfo.length >= 3) {
            long totalRAM = ramInfo[0];
            long usedRAM = ramInfo[1];
            long freeRAM = ramInfo[2];
            int percent = (int) ((usedRAM * 100) / totalRAM);

            tvRamUsage.setText(NativeHelper.formatBytes(usedRAM));
            tvRamDetails.setText(NativeHelper.formatBytes(usedRAM) + " / " + NativeHelper.formatBytes(totalRAM));
            progressRam.setProgress(percent);
        }

        // ==================== Battery ====================
        long[] batteryInfo = NativeHelper.getBatteryInfoJava();
        if (batteryInfo != null && batteryInfo.length >= 4) {
            int level = (int) batteryInfo[0];
            int status = (int) batteryInfo[1];
            int health = (int) batteryInfo[2];
            float temp = batteryInfo[3];

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
        long[] storageInfo = NativeHelper.getStorageInfoJava();
        if (storageInfo != null && storageInfo.length >= 3) {
            long total = storageInfo[0];
            long used = storageInfo[1];
            long free = storageInfo[2];
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
        String ssid = NativeHelper.getWifiSSID();
        if (ssid != null && !ssid.isEmpty() && !ssid.equalsIgnoreCase("<unknown ssid>")) {
            tvWifiSSID.setText(ssid);
        } else {
            tvWifiSSID.setText("Not Connected");
        }

        int signal = NativeHelper.getWifiSignalStrength();
        if (signal >= 0) {
            String signalText;
            if (signal > 75) signalText = "Excellent";
            else if (signal > 50) signalText = "Good";
            else if (signal > 25) signalText = "Fair";
            else signalText = "Weak";
            tvWifiSignal.setText(signalText + " (" + signal + "%)");
        } else {
            tvWifiSignal.setText("No Signal");
        }

        String ip = NativeHelper.getWifiIPAddress();
        if (ip != null && !ip.isEmpty() && !ip.equals("0.0.0.0")) {
            tvWifiIP.setText(ip);
        } else {
            tvWifiIP.setText("No IP");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isUpdating = false;
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
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
    public void onDestroyView() {
        super.onDestroyView();
        isUpdating = false;
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }
}