package com.yourteam.autho.fragments;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.yourteam.autho.R;
import com.yourteam.autho.activities.MainActivity;
import com.yourteam.autho.utils.NativeHelper;
import com.yourteam.autho.utils.NativeMonitorCallback;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DashboardFragment extends Fragment implements NativeMonitorCallback {

    private static final int MONITOR_INTERVAL_MS = 250;   // 4 Hz = realtime feel
    private static final long CLOCK_TICK_MS = 30_000;     // refresh header clock

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

    // Quick Access Cards
    private CardView cardWifiScanner;
    private CardView cardDiagnostics;
    private CardView cardRootTools;
    private CardView cardSecurityCenter;
    private CardView cardBatteryBooster;
    private CardView cardCacheCleaner;

    // Handlers / state
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private long[] lastCpuStats;           // {idle, total} from /proc/stat
    private long lastRxBytes = -1, lastTxBytes = -1, lastNetSampleTime;
    private Intent batteryStickyIntent;
    private String deviceModel;
    private int cpuCores;
    private long lastSlowRefresh = 0;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateDeviceInfoLine();
            uiHandler.postDelayed(this, CLOCK_TICK_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        NativeHelper.init(requireContext());
        deviceModel = NativeHelper.getDeviceModel();
        cpuCores = NativeHelper.getCpuCoreCount();

        initViews(view);
        setupWelcomeMessage();
        setupQuickAccess();
        updateDeviceInfoLine();

        // ✅ Prime CPU stats baseline immediately so the first tick has real data
        readCpuStats();

        return view;
    }

    private void initViews(@NonNull View view) {
        tvWelcome        = view.findViewById(R.id.tvWelcome);
        tvDeviceInfo     = view.findViewById(R.id.tvDeviceInfo);
        tvCpuUsage       = view.findViewById(R.id.tvCpuUsage);
        tvCpuTemp        = view.findViewById(R.id.tvCpuTemp);
        progressCpu      = view.findViewById(R.id.progressCpu);
        tvRamUsage       = view.findViewById(R.id.tvRamUsage);
        tvRamDetails     = view.findViewById(R.id.tvRamDetails);
        progressRam      = view.findViewById(R.id.progressRam);
        tvBatteryLevel   = view.findViewById(R.id.tvBatteryLevel);
        tvBatteryStatus  = view.findViewById(R.id.tvBatteryStatus);
        progressBattery  = view.findViewById(R.id.progressBattery);
        tvStorageUsage   = view.findViewById(R.id.tvStorageUsage);
        tvStorageDetails = view.findViewById(R.id.tvStorageDetails);
        progressStorage  = view.findViewById(R.id.progressStorage);
        tvWifiSSID       = view.findViewById(R.id.tvWifiSSID);
        tvWifiSignal     = view.findViewById(R.id.tvWifiSignal);
        tvWifiIP         = view.findViewById(R.id.tvWifiIP);

        // Quick Access bindings
        cardWifiScanner    = view.findViewById(R.id.cardWifiScanner);
        cardDiagnostics    = view.findViewById(R.id.cardDiagnostics);
        cardRootTools      = view.findViewById(R.id.cardRootTools);
        cardSecurityCenter = view.findViewById(R.id.cardSecurityCenter);
        cardBatteryBooster = view.findViewById(R.id.cardBattery);
        cardCacheCleaner   = view.findViewById(R.id.cardCacheCleaner);
    }

    private void setupWelcomeMessage() {
        String username = null;
        if (getActivity() != null && getActivity().getIntent() != null) {
            username = getActivity().getIntent().getStringExtra("username");
        }
        tvWelcome.setText(username != null && !username.isEmpty()
                ? "Welcome, " + username + "!" : "Welcome Back!");
    }

    /** Header line — model/cores are static, the clock is not. */
    private void updateDeviceInfoLine() {
        if (tvDeviceInfo == null) return;
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
        tvDeviceInfo.setText(deviceModel + " · " + cpuCores + " cores · " + time);
    }

    // ============================================================
    // QUICK ACCESS NAVIGATION
    // ============================================================

    private void setupQuickAccess() {
        cardWifiScanner.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).navigateToWifi();
        });

        cardDiagnostics.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).navigateToDiagnostics();
        });

        cardRootTools.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).navigateToRoot();
        });

        cardSecurityCenter.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).navigateToSecurity();
        });

        cardBatteryBooster.setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        "🔋 Battery Booster coming soon",
                        Toast.LENGTH_SHORT).show());

        cardCacheCleaner.setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        "🧹 Cache Cleaner coming soon",
                        Toast.LENGTH_SHORT).show());
    }

    // ==================== REALTIME MONITOR LIFECYCLE ====================

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStickyIntent = requireContext().registerReceiver(null, filter);
        updateBatteryUI();
        uiHandler.post(clockTick);
        NativeHelper.startRealtimeMonitoring(this, MONITOR_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        NativeHelper.stopRealtimeMonitoring();
        batteryStickyIntent = null;
        uiHandler.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    // ==================== NATIVE CALLBACK ====================

    @Override
    public void onUpdate(int cpuUsage, float cpuTemp,
                         long ramTotal, long ramUsed, long ramFree,
                         long rxBytesPerSec, long txBytesPerSec) {
        uiHandler.post(() -> renderMetrics(cpuUsage, cpuTemp,
                ramTotal, ramUsed, ramFree, rxBytesPerSec, txBytesPerSec));
    }

    // ==================== UI RENDERING ====================

    private void renderMetrics(int cpuUsage, float cpuTemp,
                               long ramTotal, long ramUsed, long ramFree,
                               long rxBps, long txBps) {
        if (!isAdded() || getContext() == null) return;

        // ---------- CPU USAGE ----------
        int usage = (cpuUsage > 0) ? cpuUsage : readCpuUsageJava();
        if (usage < 0) {
            readCpuStats();    // re-prime baseline
            usage = 0;
        }
        tvCpuUsage.setText(usage + "%");
        progressCpu.setProgress(Math.min(100, Math.max(0, usage)));

        // ---------- CPU TEMPERATURE ----------
        float temp = cpuTemp;
        if (temp <= 0) temp = readCpuTempJava();
        if (temp <= 0) temp = readCpuTempMultiPath();   // ✅ NEW multi-path fallback
        if (temp > 0) {
            tvCpuTemp.setText(String.format(Locale.getDefault(), "Temp: %.1f°C", temp));
        } else {
            tvCpuTemp.setText("Temp: N/A");
        }

        // ---------- RAM ----------
        if (ramTotal > 0) {
            int percent = (int) (ramUsed * 100 / ramTotal);
            tvRamUsage.setText(percent + "%");
            tvRamDetails.setText(NativeHelper.formatBytes(ramUsed) + " / "
                    + NativeHelper.formatBytes(ramTotal));
            progressRam.setProgress(percent);
        }

        // ---------- NETWORK ----------
        updateNetworkSpeed();

        // ---------- SLOW METRICS ----------
        refreshSlowMetrics();
    }

    private void refreshSlowMetrics() {
        long now = System.currentTimeMillis();
        if (now - lastSlowRefresh < 5000) return;
        lastSlowRefresh = now;

        updateBatteryUI();

        long[] storage = NativeHelper.getStorageInfoJava();
        if (storage != null && storage.length >= 3 && storage[0] > 0) {
            long total = storage[0], used = storage[1];
            int percent = (int) (used * 100 / total);
            tvStorageUsage.setText(percent + "%");
            tvStorageDetails.setText(NativeHelper.formatBytes(used) + " / "
                    + NativeHelper.formatBytes(total));
            progressStorage.setProgress(percent);
        }

        NativeHelper.WifiInfoDetailed wifi = NativeHelper.getWifiInfoDetailed();
        String ssid = wifi.ssid;
        tvWifiSSID.setText(ssid != null && !ssid.isEmpty()
                && !ssid.equalsIgnoreCase("<unknown ssid>") ? ssid : "Not Connected");
        String ip = wifi.ipAddress;
        tvWifiIP.setText(ip != null && !ip.equals("0.0.0.0") ? ip : "No IP");
    }

    // ==================== BATTERY ====================

    private void updateBatteryUI() {
        Intent bi = batteryStickyIntent;
        if (bi == null) {
            long[] battery = NativeHelper.getBatteryInfoJava();
            if (battery != null && battery.length >= 4) {
                renderBattery((int) battery[0], (int) battery[1],
                        (int) battery[2], battery[3] / 10f);
            }
            return;
        }

        int level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) return;
        int pct = Math.round(level * 100f / scale);

        int status = bi.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        int health = bi.getIntExtra(BatteryManager.EXTRA_HEALTH,
                BatteryManager.BATTERY_HEALTH_UNKNOWN);
        float tempC = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;

        renderBattery(pct, status, health, tempC);
    }

    private void renderBattery(int pct, int status, int health, float tempC) {
        tvBatteryLevel.setText(pct + "%");
        progressBattery.setProgress(pct);
        tvBatteryStatus.setText(
                NativeHelper.getBatteryStatusString(status) + " · "
                        + NativeHelper.getBatteryHealthString(health) + " · "
                        + String.format(Locale.getDefault(), "%.1f", tempC) + "°C");

        int color;
        if (pct > 50)      color = R.color.status_green;
        else if (pct > 20) color = R.color.status_yellow;
        else               color = R.color.status_red;
        tvBatteryLevel.setTextColor(ContextCompat.getColor(requireContext(), color));
        progressBattery.setProgressTintList(
                ContextCompat.getColorStateList(requireContext(), color));
    }

    // ==================== NETWORK ====================

    private void updateNetworkSpeed() {
        long now = System.currentTimeMillis();
        long rx, tx;
        try {
            rx = TrafficStats.getTotalRxBytes();
            tx = TrafficStats.getTotalTxBytes();
        } catch (Exception e) {
            return;
        }

        if (lastRxBytes >= 0) {
            double dtSec = (now - lastNetSampleTime) / 1000.0;
            if (dtSec > 0) {
                long rxBps = (long) ((rx - lastRxBytes) / dtSec);
                long txBps = (long) ((tx - lastTxBytes) / dtSec);
                if (rxBps >= 0 && txBps >= 0) {
                    tvWifiSignal.setText("↓ " + NativeHelper.formatBytes(rxBps)
                            + "/s · ↑ " + NativeHelper.formatBytes(txBps) + "/s");
                }
            }
        }
        lastRxBytes = rx;
        lastTxBytes = tx;
        lastNetSampleTime = now;
    }

    // ==================== CPU FALLBACKS ====================

    /**
     * Returns CPU utilization % since the previous call.
     * First call primes the baseline and returns 0 (not -1),
     * so the second tick already shows real data.
     */
    private int readCpuUsageJava() {
        long[] s = readCpuStats();
        if (s == null) return -1;

        if (lastCpuStats == null) {
            lastCpuStats = s;
            return 0;
        }

        long dIdle  = s[0] - lastCpuStats[0];
        long dTotal = s[1] - lastCpuStats[1];
        lastCpuStats = s;

        if (dTotal <= 0) return 0;
        long used = dTotal - dIdle;
        return Math.max(0, Math.min(100, (int) (used * 100 / dTotal)));
    }

    private long[] readCpuStats() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = br.readLine();
            if (line == null || !line.startsWith("cpu ")) return null;
            String[] parts = line.trim().split("\\s+");
            long total = 0, idle = 0;
            for (int i = 1; i < parts.length; i++) {
                long v = Long.parseLong(parts[i]);
                total += v;
                if (i == 4 || i == 5) idle += v; // idle + iowait
            }
            return new long[]{idle, total};
        } catch (Exception e) {
            return null;
        }
    }

    /** Best-effort CPU temperature from standard thermal zones. */
    private float readCpuTempJava() {
        File dir = new File("/sys/class/thermal");
        File[] zones = dir.listFiles((d, name) -> name.startsWith("thermal_zone"));
        if (zones == null) return -1;

        File best = null;
        for (File zone : zones) {
            String type = readSmallFile(new File(zone, "type"));
            if (type == null) continue;
            String t = type.toLowerCase(Locale.US);
            if (t.contains("cpu") || t.contains("soc") || t.contains("pkg")) {
                best = zone;
                break;
            }
            if (best == null) best = zone;
        }
        if (best == null) return -1;
        String raw = readSmallFile(new File(best, "temp"));
        if (raw == null) return -1;
        try {
            float v = Float.parseFloat(raw.trim());
            if (v > 1000) v /= 1000f;
            return (v > 0 && v < 150) ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * ✅ NEW — Multi-path thermal reader.
     * Tries many OEM-specific paths (Qualcomm, MediaTek, Exynos, Unisoc, Transsion).
     */
    private float readCpuTempMultiPath() {
        String[] paths = {
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/class/thermal/thermal_zone2/temp",
                "/sys/class/thermal/thermal_zone3/temp",
                "/sys/class/thermal/thermal_zone4/temp",
                "/sys/class/hwmon/hwmon0/temp1_input",
                "/sys/class/hwmon/hwmon1/temp1_input",
                "/proc/mtk_thermal/temp",
                "/sys/devices/virtual/thermal/thermal_zone0/temp",
                "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
                "/sys/devices/platform/omap/omap_temp_sensor.0/temperature"
        };

        for (String path : paths) {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) continue;

            String raw = readSmallFile(f);
            if (raw == null) continue;

            try {
                float v = Float.parseFloat(raw.trim());
                if (v > 1000) v /= 1000f;
                if (v > 0 && v < 150) return v;
            } catch (NumberFormatException ignored) { }
        }
        return -1;
    }

    @Nullable
    private String readSmallFile(File f) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            return br.readLine();
        } catch (Exception e) {
            return null;
        }
    }
}