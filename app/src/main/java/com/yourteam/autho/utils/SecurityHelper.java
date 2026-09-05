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
}
