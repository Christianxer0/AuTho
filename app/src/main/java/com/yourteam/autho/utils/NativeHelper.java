package com.yourteam.autho.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@SuppressWarnings("SpellCheckingInspection") // "ssid" is correct WiFi terminology
public class NativeHelper {
    private static final String TAG = "NativeHelper";
    private static Context appContext;

    static {
        try {
            System.loadLibrary("native-lib");
            Log.d(TAG, "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library: " + e.getMessage());
        }
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        Log.d(TAG, "NativeHelper initialized");
    }

    private static void ensureContext() {
        if (appContext == null) {
            throw new IllegalStateException("NativeHelper.init(context) must be called first");
        }
    }

    // ============================================================
    // NATIVE METHODS
    // ============================================================

    // --- System Information ---
    public static native int getCpuUsage();
    public static native int getCpuCoreCount();
    public static native float getCpuTemperature();
    public static native long[] getRamInfo();           // [total, used, free]
    public static native int[] getBatteryInfo();        // stub: [level, status, health, plugged, temp, voltage]
    public static native long[] getInternalStorageInfo(); // [total, used, free]
    public static native long[] getExternalStorageInfo(); // [total, used, free]

    // --- Network ---
    public static native String[] scanNetworkDevices(int timeoutMs); // "IP|MAC|Name|Type"
    public static native int getWifiSignalStrength();   // 0..100 or -1
    public static native String getWifiSSID();
    public static native String getWifiIPAddress();
    public static native String getWifiGateway();
    public static native boolean pingDevice(String ipAddress, int timeoutMs);
    public static native long[] getNetworkStats();      // [rxBytes, txBytes, speedPlaceholder]

    // --- Root ---
    public static native boolean isDeviceRooted();
    public static native String executeRootCommand(String command);
    public static native boolean uninstallPackage(String packageName, boolean isSystemApp);
    public static native boolean restorePackage(String packageName);
    public static native int killBackgroundProcesses();
    public static native boolean clearSystemCache();

    // --- Performance ---
    public static native String[] getProcessList();     // "PID|Name|CPU%|RSS"

    // --- Hardware Info ---
    public static native String getDeviceModel();
    public static native String getKernelVersion();
    public static native int[] getScreenResolution();   // [width, height, dpi]

    // --- Hardware Diagnostics ---
    public static native boolean testDisplay(int testType, int durations);
    public static native boolean testAudio(int frequency, int durationMs);
    public static native float[] testMicrophone(int durationMs);
    public static native boolean testCamera(int cameraId, int durationMs);
    public static native float[] testSensor(int sensorType, int durationMs);
    public static native boolean testVibration(int durationMs);
    public static native boolean testFlashlight(boolean enable);

    // --- Realtime Monitor ---
    private static native boolean startNativeMonitor(NativeMonitorCallback callback, int intervalMs);
    private static native void stopNativeMonitor();

    // ============================================================
    // DATA CLASSES
    // ============================================================

    public static class CpuInfo {
        public final int usagePercent;      // 0..100, -1 if error
        public final int coreCount;
        public final float temperatureC;    // -1 if unavailable

        public CpuInfo(int usagePercent, int coreCount, float temperatureC) {
            this.usagePercent = usagePercent;
            this.coreCount = coreCount;
            this.temperatureC = temperatureC;
        }

        @Override
        public String toString() {
            return String.format("CPU: %d%% | Cores: %d | Temp: %.1f\u00b0C",
                    usagePercent, coreCount, temperatureC);
        }
    }

    public static class RamInfo {
        public final long totalBytes;
        public final long usedBytes;
        public final long freeBytes;

        public RamInfo(long totalBytes, long usedBytes, long freeBytes) {
            this.totalBytes = totalBytes;
            this.usedBytes = usedBytes;
            this.freeBytes = freeBytes;
        }

        public int usagePercent() {
            if (totalBytes <= 0) return 0;
            return (int) (usedBytes * 100 / totalBytes);
        }

        public String totalFormatted() { return formatBytes(totalBytes); }
        public String usedFormatted()  { return formatBytes(usedBytes); }
        public String freeFormatted()  { return formatBytes(freeBytes); }

        @Override
        public String toString() {
            return String.format("RAM: %s / %s (%d%%)", usedFormatted(), totalFormatted(), usagePercent());
        }
    }

    public static class StorageInfo {
        public final long totalBytes;
        public final long usedBytes;
        public final long freeBytes;
        public final boolean isExternal;

        public StorageInfo(long totalBytes, long usedBytes, long freeBytes, boolean isExternal) {
            this.totalBytes = totalBytes;
            this.usedBytes = usedBytes;
            this.freeBytes = freeBytes;
            this.isExternal = isExternal;
        }

        public int usagePercent() {
            if (totalBytes <= 0) return 0;
            return (int) (usedBytes * 100 / totalBytes);
        }

        public String totalFormatted() { return formatBytes(totalBytes); }
        public String usedFormatted()  { return formatBytes(usedBytes); }
        public String freeFormatted()  { return formatBytes(freeBytes); }

        @Override
        public String toString() {
            String type = isExternal ? "External" : "Internal";
            return String.format("%s: %s / %s (%d%%)", type, usedFormatted(), totalFormatted(), usagePercent());
        }
    }

    public static class BatteryInfo {
        public final int levelPercent;      // 0..100
        public final int status;            // BatteryManager.BATTERY_STATUS_*
        public final int health;            // BatteryManager.BATTERY_HEALTH_*
        public final float temperatureC;    // Celsius
        public final int voltageMv;         // mV, -1 if unknown
        public final boolean isCharging;

        public BatteryInfo(int levelPercent, int status, int health,
                           float temperatureC, int voltageMv, boolean isCharging) {
            this.levelPercent = levelPercent;
            this.status = status;
            this.health = health;
            this.temperatureC = temperatureC;
            this.voltageMv = voltageMv;
            this.isCharging = isCharging;
        }

        public String statusString()  { return getBatteryStatusString(status); }
        public String healthString()  { return getBatteryHealthString(health); }

        @Override
        public String toString() {
            return String.format("Battery: %d%% | %s | %.1f\u00b0C | %s",
                    levelPercent, statusString(), temperatureC, healthString());
        }
    }

    public static class NetworkDevice {
        public final String ipAddress;
        public final String macAddress;
        public final String deviceName;
        public final String deviceType;

        public NetworkDevice(String ipAddress, String macAddress, String deviceName, String deviceType) {
            this.ipAddress = ipAddress;
            this.macAddress = macAddress;
            this.deviceName = deviceName;
            this.deviceType = deviceType;
        }

        @Override
        public String toString() {
            return String.format("%s (%s) - %s [%s]", deviceName, ipAddress, macAddress, deviceType);
        }
    }

    public static class NetworkStats {
        public final long rxBytes;
        public final long txBytes;
        public final long rxBytesPerSec;    // computed if monitor active, else 0
        public final long txBytesPerSec;

        public NetworkStats(long rxBytes, long txBytes, long rxBytesPerSec, long txBytesPerSec) {
            this.rxBytes = rxBytes;
            this.txBytes = txBytes;
            this.rxBytesPerSec = rxBytesPerSec;
            this.txBytesPerSec = txBytesPerSec;
        }

        public String rxFormatted() { return formatBytes(rxBytes); }
        public String txFormatted() { return formatBytes(txBytes); }
        public String rxSpeedFormatted() { return formatBytes(rxBytesPerSec) + "/s"; }
        public String txSpeedFormatted() { return formatBytes(txBytesPerSec) + "/s"; }

        @Override
        public String toString() {
            return String.format("Net: \u2193 %s \u2191 %s", rxFormatted(), txFormatted());
        }
    }

    public static class WifiInfoDetailed {
        public final String ssid;
        public final int signalPercent;     // 0..100, -1 if unavailable
        public final String ipAddress;
        public final String gateway;
        public final String linkSpeed;      // e.g. "72 Mbps"
        public final int rssi;              // raw dBm, if available

        public WifiInfoDetailed(String ssid, int signalPercent, String ipAddress,
                                String gateway, String linkSpeed, int rssi) {
            this.ssid = ssid;
            this.signalPercent = signalPercent;
            this.ipAddress = ipAddress;
            this.gateway = gateway;
            this.linkSpeed = linkSpeed;
            this.rssi = rssi;
        }

        public String signalLabel() {
            if (signalPercent < 0) return "No Signal";
            if (signalPercent > 75) return "Excellent";
            if (signalPercent > 50) return "Good";
            if (signalPercent > 25) return "Fair";
            return "Weak";
        }

        @Override
        public String toString() {
            return String.format("WiFi: %s | %s (%d%%) | IP: %s | GW: %s",
                    ssid, signalLabel(), signalPercent, ipAddress, gateway);
        }
    }

    public static class ProcessInfo {
        public final int pid;
        public final String name;
        public final float cpuPercent;
        public final long rssKb;

        public ProcessInfo(int pid, String name, float cpuPercent, long rssKb) {
            this.pid = pid;
            this.name = name;
            this.cpuPercent = cpuPercent;
            this.rssKb = rssKb;
        }

        public String rssFormatted() { return formatBytes(rssKb * 1024); }

        @Override
        public String toString() {
            return String.format("[%d] %s | CPU %.1f%% | RSS %s",
                    pid, name, cpuPercent, rssFormatted());
        }
    }

    // ============================================================
    // SYSTEM INFO HELPERS (COMPLETED)
    // ============================================================

    /** Complete CPU snapshot: usage, cores, temperature. */
    public static CpuInfo getCpuInfo() {
        int usage = getCpuUsage();
        int cores = getCpuCoreCount();
        float temp = getCpuTemperature();
        return new CpuInfo(usage, cores, temp);
    }

    /** Complete RAM snapshot with structured result. */
    public static RamInfo getRamInfoJava() {
        long[] raw = getRamInfo();
        if (raw == null || raw.length < 3) {
            Log.w(TAG, "getRamInfo() returned null or incomplete data");
            return new RamInfo(0, 0, 0);
        }
        return new RamInfo(raw[0], raw[1], raw[2]);
    }

    /** Complete battery snapshot via Android APIs (more accurate than native stub). */
    public static BatteryInfo getBatteryInfoDetailed() {
        ensureContext();
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent intent = appContext.registerReceiver(null, ifilter);
        if (intent == null) {
            Log.e(TAG, "Failed to receive battery intent");
            return new BatteryInfo(-1, -1, -1, -1f, -1, false);
        }

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int pct = (scale > 0) ? (level * 100 / scale) : -1;

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        float tempC = (tempTenths >= 0) ? tempTenths / 10.0f : -1f;
        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

        boolean charging = (status == BatteryManager.BATTERY_STATUS_CHARGING)
                || (status == BatteryManager.BATTERY_STATUS_FULL);

        return new BatteryInfo(pct, status, health, tempC, voltage, charging);
    }

    /**
     * Battery snapshot as a raw array (matches the native stub layout):
     * [level, status, health, plugged, temp (tenths °C), voltage (mV)].
     * Uses the native stub if available, otherwise falls back to Android APIs.
     */
    public static long[] getBatteryInfoJava() {
        int[] raw = getBatteryInfo();
        if (raw != null && raw.length >= 6) {
            long[] out = new long[6];
            for (int i = 0; i < 6; i++) out[i] = raw[i];
            return out;
        }
        BatteryInfo info = getBatteryInfoDetailed();
        long plugged = info.isCharging ? 1 : 0;
        long tempTenths = (info.temperatureC >= 0) ? (long) (info.temperatureC * 10) : -1;
        return new long[]{ info.levelPercent, info.status, info.health, plugged, tempTenths, info.voltageMv };
    }

    /** Internal storage snapshot. */
    public static StorageInfo getInternalStorageInfoJava() {
        long[] raw = getInternalStorageInfo();
        if (raw == null || raw.length < 3) {
            Log.w(TAG, "getInternalStorageInfo() returned null or incomplete data");
            return new StorageInfo(0, 0, 0, false);
        }
        return new StorageInfo(raw[0], raw[1], raw[2], false);
    }

    /** External storage snapshot (SD card / adopted storage). */
    public static StorageInfo getExternalStorageInfoJava() {
        long[] raw = getExternalStorageInfo();
        if (raw == null || raw.length < 3) {
            // Fallback to Environment if native path not found
            try {
                StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
                long total = stat.getTotalBytes();
                long free = stat.getFreeBytes();
                return new StorageInfo(total, total - free, free, true);
            } catch (Exception e) {
                Log.e(TAG, "External storage fallback failed: " + e.getMessage());
                return new StorageInfo(0, 0, 0, true);
            }
        }
        return new StorageInfo(raw[0], raw[1], raw[2], true);
    }

    /** Internal + external storage pair. */
    public static class StoragePair {
        public final StorageInfo internal;
        public final StorageInfo external;

        public StoragePair(StorageInfo internal, StorageInfo external) {
            this.internal = internal;
            this.external = external;
        }
    }

    /** Internal + external storage as structured objects. */
    public static StoragePair getStoragePair() {
        return new StoragePair(
                getInternalStorageInfoJava(),
                getExternalStorageInfoJava()
        );
    }

    /**
     * Internal storage as a raw array: [total, used, free].
     * Uses the native path if available, otherwise reads /data via StatFs.
     */
    public static long[] getStorageInfoJava() {
        long[] raw = getInternalStorageInfo();
        if (raw != null && raw.length >= 3) {
            return raw;
        }
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long total = stat.getTotalBytes();
            long free = stat.getFreeBytes();
            return new long[]{ total, total - free, free };
        } catch (Exception e) {
            Log.e(TAG, "Storage info fallback failed: " + e.getMessage());
            return new long[]{ 0, 0, 0 };
        }
    }

    /** Combined system health snapshot. */
    public static SystemSnapshot getSystemSnapshot() {
        return new SystemSnapshot(
                getCpuInfo(),
                getRamInfoJava(),
                getBatteryInfoDetailed(),
                getInternalStorageInfoJava(),
                getExternalStorageInfoJava()
        );
    }

    public static class SystemSnapshot {
        public final CpuInfo cpu;
        public final RamInfo ram;
        public final BatteryInfo battery;
        public final StorageInfo internalStorage;
        public final StorageInfo externalStorage;

        public SystemSnapshot(CpuInfo cpu, RamInfo ram, BatteryInfo battery,
                              StorageInfo internalStorage, StorageInfo externalStorage) {
            this.cpu = cpu;
            this.ram = ram;
            this.battery = battery;
            this.internalStorage = internalStorage;
            this.externalStorage = externalStorage;
        }

        @Override
        public String toString() {
            return cpu + "\n" + ram + "\n" + battery + "\n" + internalStorage;
        }
    }

    // ============================================================
    // NETWORK HELPERS (COMPLETED)
    // ============================================================

    /** Scans the LAN and returns parsed device objects. */
    public static List<NetworkDevice> scanNetworkDevicesParsed(int timeoutMs) {
        String[] raw = scanNetworkDevices(timeoutMs);
        List<NetworkDevice> devices = new ArrayList<>();
        if (raw == null) return devices;

        for (String entry : raw) {
            String[] parts = entry.split("\\|", 4);
            if (parts.length >= 4) {
                devices.add(new NetworkDevice(parts[0], parts[1], parts[2], parts[3]));
            } else if (parts.length >= 1) {
                devices.add(new NetworkDevice(parts[0], "00:00:00:00:00:00", "Unknown", "Unknown"));
            }
        }
        return devices;
    }

    /** Complete WiFi information combining native + Android APIs. */
    public static WifiInfoDetailed getWifiInfoDetailed() {
        ensureContext();
        String ssid = getWifiSSID();
        int signal = getWifiSignalStrength();
        String ip = getWifiIPAddress();
        String gw = getWifiGateway();

        String linkSpeed = "Unknown";
        int rssi = -100;

        try {
            WifiManager wm = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo info = wm.getConnectionInfo();
                if (info != null) {
                    int speedMbps = info.getLinkSpeed(); // Mbps
                    linkSpeed = speedMbps + " Mbps";
                    rssi = info.getRssi();
                    // Override SSID if native returned placeholder
                    if (ssid == null || ssid.equals("WiFi_Network") || ssid.isEmpty()) {
                        String realSsid = info.getSSID();
                        if (realSsid != null && !realSsid.equals("<unknown ssid>")) {
                            ssid = realSsid.replace("\"", "");
                        }
                    }
                    // Override signal if native returned -1
                    if (signal < 0 && rssi != 0) {
                        signal = WifiManager.calculateSignalLevel(rssi, 101);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "WifiManager fallback failed: " + e.getMessage());
        }

        if (ssid == null || ssid.isEmpty() || ssid.equalsIgnoreCase("<unknown ssid>")) {
            ssid = "Not Connected";
        }
        if (ip == null || ip.equals("0.0.0.0")) ip = getLocalIpAddress();
        if (gw == null || gw.equals("0.0.0.0")) gw = "192.168.1.1";

        return new WifiInfoDetailed(ssid, signal, ip, gw, linkSpeed, rssi);
    }

    /** Network counters from /proc/net/dev. */
    public static NetworkStats getNetworkStatsJava() {
        long[] raw = getNetworkStats();
        if (raw == null || raw.length < 2) {
            return new NetworkStats(0, 0, 0, 0);
        }
        return new NetworkStats(raw[0], raw[1], 0, 0);
    }

    /** Ping with result wrapper. */
    public static PingResult ping(String ipAddress, int timeoutMs) {
        long start = System.currentTimeMillis();
        boolean ok = pingDevice(ipAddress, timeoutMs);
        long elapsed = System.currentTimeMillis() - start;
        return new PingResult(ipAddress, ok, elapsed);
    }

    public static class PingResult {
        public final String ipAddress;
        public final boolean success;
        public final long latencyMs;

        public PingResult(String ipAddress, boolean success, long latencyMs) {
            this.ipAddress = ipAddress;
            this.success = success;
            this.latencyMs = latencyMs;
        }

        @Override
        public String toString() {
            return success
                    ? String.format("Ping %s: OK (%d ms)", ipAddress, latencyMs)
                    : String.format("Ping %s: FAILED", ipAddress);
        }
    }

    /** Fallback IP detection using Java APIs. */
    private static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                for (InetAddress addr : Collections.list(addrs)) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "getLocalIpAddress failed: " + e.getMessage());
        }
        return "0.0.0.0";
    }

    // ============================================================
    // PERFORMANCE HELPERS
    // ============================================================

    /** Parse native process list into objects. */
    public static List<ProcessInfo> getProcessListParsed() {
        String[] raw = getProcessList();
        List<ProcessInfo> list = new ArrayList<>();
        if (raw == null) return list;

        for (String entry : raw) {
            String[] parts = entry.split("\\|", 4);
            if (parts.length == 4) {
                try {
                    int pid = Integer.parseInt(parts[0]);
                    float cpu = Float.parseFloat(parts[2]);
                    long rss = Long.parseLong(parts[3]);
                    list.add(new ProcessInfo(pid, parts[1], cpu, rss));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Failed to parse process entry: " + entry);
                }
            }
        }
        return list;
    }

    // ============================================================
    // FORMATTING UTILITIES
    // ============================================================

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        if (bytes < 1024L * 1024L * 1024L * 1024L)
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        return String.format("%.2f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
    }

    public static String formatBitsPerSecond(long bytesPerSec) {
        long bits = bytesPerSec * 8;
        if (bits < 1000) return bits + " bps";
        if (bits < 1000_000) return String.format("%.1f Kbps", bits / 1000.0);
        if (bits < 1000_000_000) return String.format("%.1f Mbps", bits / 1000_000.0);
        return String.format("%.2f Gbps", bits / 1000_000_000.0);
    }

    public static String getBatteryStatusString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:     return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:  return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL:         return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not Charging";
            default: return "Unknown";
        }
    }

    public static String getBatteryHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:                return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:            return "Overheating";
            case BatteryManager.BATTERY_HEALTH_DEAD:                return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:        return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "Failure";
            case BatteryManager.BATTERY_HEALTH_COLD:                return "Cold";
            default: return "Unknown";
        }
    }

    // ============================================================
    // REALTIME MONITOR
    // ============================================================

    private static volatile boolean monitorRunning = false;

    public static synchronized boolean startRealtimeMonitoring(NativeMonitorCallback callback,
                                                               int intervalMs) {
        if (monitorRunning) return true;
        boolean ok = startNativeMonitor(callback, intervalMs);
        monitorRunning = ok;
        return ok;
    }

    public static synchronized void stopRealtimeMonitoring() {
        if (!monitorRunning) return;
        stopNativeMonitor();
        monitorRunning = false;
    }

    public static boolean isMonitorRunning() {
        return monitorRunning;
    }

    // ============================================================
    // HARDWARE DIAGNOSTICS - JAVA HELPER METHODS
    // ============================================================

    /**
     * Play a test tone using Android AudioTrack.
     * (Implementation left to the app layer; see AudioTrack docs.)
     */
}