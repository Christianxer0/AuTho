package com.yourteam.autho.utils;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

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

    // ==================== NATIVE METHODS ====================
    // System Information
    public static native int getCpuUsage();
    public static native int getCpuCoreCount();
    public static native float getCpuTemperature();
    public static native long[] getRamInfo();
    public static native int[] getBatteryInfo();
    public static native long[] getInternalStorageInfo();
    public static native long[] getExternalStorageInfo();

    // Network
    public static native String[] scanNetworkDevices(int timeoutMs);
    public static native int getWifiSignalStrength();
    public static native String getWifiSSID();
    public static native String getWifiIPAddress();
    public static native String getWifiGateway();
    public static native boolean pingDevice(String ipAddress, int timeoutMs);

    // Root
    public static native boolean isDeviceRooted();
    public static native String executeRootCommand(String command);
    public static native boolean uninstallPackage(String packageName, boolean isSystemApp);
    public static native boolean restorePackage(String packageName);
    public static native int killBackgroundProcesses();
    public static native boolean clearSystemCache();

    // Performance
    public static native String[] getProcessList();
    public static native long[] getNetworkStats();

    // Hardware Info
    public static native String getDeviceModel();
    public static native String getKernelVersion();
    public static native int[] getScreenResolution();

    // ==================== JAVA HELPER METHODS ====================

    public static long[] getBatteryInfoJava() {
        if (appContext == null) {
            Log.e(TAG, "Context not initialized");
            return new long[]{-1, -1, -1, -1};
        }
        android.os.BatteryManager bm = (android.os.BatteryManager)
                appContext.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) {
            return new long[]{-1, -1, -1, -1};
        }
        int level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
        int status = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS);
        int health = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_HEALTH);
        // Temperature is in tenths of degrees Celsius
        int temp = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_TEMPERATURE);
        float tempCelsius = temp / 10.0f;
        return new long[]{level, status, health, (long) tempCelsius};
    }

    public static String getBatteryStatusString(int status) {
        switch (status) {
            case android.os.BatteryManager.BATTERY_STATUS_CHARGING:
                return "⚡ Charging";
            case android.os.BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "🔋 Discharging";
            case android.os.BatteryManager.BATTERY_STATUS_FULL:
                return "✅ Full";
            case android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "⏸️ Not Charging";
            default:
                return "❓ Unknown";
        }
    }

    public static String getBatteryHealthString(int health) {
        switch (health) {
            case android.os.BatteryManager.BATTERY_HEALTH_GOOD:
                return "✅ Good";
            case android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "🔥 Overheating";
            case android.os.BatteryManager.BATTERY_HEALTH_DEAD:
                return "💀 Dead";
            case android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "⚡ Over Voltage";
            case android.os.BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "❌ Failure";
            default:
                return "❓ Unknown";
        }
    }

    public static long[] getStorageInfoJava() {
        if (appContext == null) {
            return new long[]{-1, -1, -1};
        }
        try {
            StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
            long totalBytes = statFs.getTotalBytes();
            long freeBytes = statFs.getFreeBytes();
            long usedBytes = totalBytes - freeBytes;
            return new long[]{totalBytes, usedBytes, freeBytes};
        } catch (Exception e) {
            Log.e(TAG, "Failed to get storage info: " + e.getMessage());
            return new long[]{-1, -1, -1};
        }
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}