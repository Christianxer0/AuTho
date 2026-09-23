package com.yourteam.autho.utils;

/**
 * Push-style realtime monitor callback.
 * Invoked from a NATIVE pthread on every sampling tick — NOT the main thread.
 * Always post() to the main looper before touching UI.
 */
public interface NativeMonitorCallback {

    /**
     * @param cpuUsage  0..100, -1 if unreadable
     * @param cpuTemp   celsius, -1 if unavailable
     * @param ramTotal  bytes
     * @param ramUsed   bytes
     * @param ramFree   bytes
     * @param rxBytesPerSec  realtime download throughput
     * @param txBytesPerSec  realtime upload throughput
     */
    void onUpdate(int cpuUsage, float cpuTemp,
                  long ramTotal, long ramUsed, long ramFree,
                  long rxBytesPerSec, long txBytesPerSec);
}