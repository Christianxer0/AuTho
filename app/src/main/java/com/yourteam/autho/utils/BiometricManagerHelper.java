package com.yourteam.autho.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.os.Build;
import android.util.Log;

import androidx.core.hardware.fingerprint.FingerprintManagerCompat;

public class BiometricManagerHelper {

    private static final String TAG = "BiometricManager";
    private static final String PREFS_NAME = "autho_biometric_prefs";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_BIOMETRIC_USERNAME = "biometric_username";
    private static final String KEY_BIOMETRIC_EMAIL = "biometric_email";
    private static final String KEY_BIOMETRIC_PASSWORD = "biometric_password";

    private Context context;
    private SharedPreferences prefs;

    public BiometricManagerHelper(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }



}
