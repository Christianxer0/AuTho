package com.yourteam.autho.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SecurityHelper {
    private Context context;
    private SharedPreferences sharedPreferences;

    private static final String PREF_NAME = "autho_prefs";
    private static final String KEY_USERNAME = "saved_username";
    private static final String KEY_PIN = "saved_pin";

    //Security Helper
    public SecurityHelper(Context context){
        this.context = context;
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    //save Username
    public void saveUsername(String username) {
        sharedPreferences.edit().putString(KEY_USERNAME, username).apply();
    }

    //get saved Username
    public String getSavedUsername(){
        return sharedPreferences.getString(KEY_USERNAME, null);
    }
    //clear Saved Username
    public void clearSavedUsername() {
        sharedPreferences.edit().remove(KEY_USERNAME).apply();
    }
    //saved Pin
    public void savePin(String pin) {
        sharedPreferences.edit().putString(KEY_PIN, pin).apply();
    }
    //get Store Pin
    public String getStoredPin() {
        return sharedPreferences.getString(KEY_PIN, null);
    }
    // check if pin set
    public boolean isPinSet() {
        return getStoredPin() != null &&!getStoredPin().isEmpty();
    }
    //check if pin is verified
    public boolean verifyPin(String pin) {
        String storedPin = getStoredPin();
        if(storedPin == null) return false;

        return storedPin.equals(pin);
    }


    // ============================================================
// PHASE 6: SECURITY CENTER LOGGING
// ============================================================

    private static final String KEY_SECURITY_LOG = "security_log";
    private static final String KEY_LAST_SCAN_TIME = "last_security_scan";
    private static final int MAX_LOG_ENTRIES = 50;

    /**
     * Append a security log entry. Format: "type|title|timestamp"
     * type: "success", "warning", "error"
     */
    public void addSecurityLog(String type, String title) {
        String existing = sharedPreferences.getString(KEY_SECURITY_LOG, "");
        long now = System.currentTimeMillis();
        String entry = type + "|" + title + "|" + now;

        // Prepend new entry so newest appears first
        String updated = entry + "\n" + existing;
        String[] lines = updated.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, MAX_LOG_ENTRIES); i++) {
            if (!lines[i].trim().isEmpty()) {
                sb.append(lines[i]).append('\n');
            }
        }
        sharedPreferences.edit().putString(KEY_SECURITY_LOG, sb.toString()).apply();
    }

    /**
     * Get all security logs as an array of "type|title|timestamp".
     */
    public String[] getSecurityLogs() {
        String logs = sharedPreferences.getString(KEY_SECURITY_LOG, "");
        if (logs.trim().isEmpty()) return new String[0];
        return logs.trim().split("\n");
    }

    public void clearSecurityLogs() {
        sharedPreferences.edit().remove(KEY_SECURITY_LOG).apply();
    }

    public void setLastScanTime(long timeMs) {
        sharedPreferences.edit().putLong(KEY_LAST_SCAN_TIME, timeMs).apply();
    }

    public long getLastScanTime() {
        return sharedPreferences.getLong(KEY_LAST_SCAN_TIME, 0);
    }
}
