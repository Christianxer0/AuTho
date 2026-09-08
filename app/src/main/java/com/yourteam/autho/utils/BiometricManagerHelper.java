package com.yourteam.autho.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.biometric.BiometricManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class BiometricManagerHelper {

    private static final String TAG = "BiometricManagerHelper";
    private static final String PREFS_NAME = "autho_biometric_prefs";

    private static final String KEY_BIOMETRIC_ENABLED  = "biometric_enabled";
    private static final String KEY_BIOMETRIC_USERNAME = "biometric_username";
    private static final String KEY_BIOMETRIC_EMAIL    = "biometric_email";
    private static final String KEY_BIOMETRIC_PASSWORD = "biometric_password";

    private final Context context;
    private final SharedPreferences prefs;

    public BiometricManagerHelper(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = createEncryptedPrefs();
    }

    /* ------------------------------------------------------------------ */
    /*  Encrypted SharedPreferences                                        */
    /* ------------------------------------------------------------------ */

    private SharedPreferences createEncryptedPrefs() {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences", e);
            // Fallback (not recommended for production — handle securely)
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Biometric availability (minSdk 30 = API 30+, no legacy checks)    */
    /* ------------------------------------------------------------------ */

    public boolean isBiometricAvailable() {
        int result = BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
        );
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public boolean isBiometricHardwareDetected() {
        int result = BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        return result != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
                && result != BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE;
    }

    public boolean hasEnrolledBiometrics() {
        int result = BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /* ------------------------------------------------------------------ */
    /*  Settings & credential storage                                      */
    /* ------------------------------------------------------------------ */

    public boolean isBiometricEnabled() {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }

    public void setBiometricEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply();
        if (!enabled) {
            clearBiometricCredentials();
        }
    }

    public void saveBiometricCredentials(String username, String email, String password) {
        prefs.edit()
                .putString(KEY_BIOMETRIC_USERNAME, username)
                .putString(KEY_BIOMETRIC_EMAIL, email)
                .putString(KEY_BIOMETRIC_PASSWORD, password)
                .apply();
        setBiometricEnabled(true);
    }

    public String getBiometricUsername() {
        return prefs.getString(KEY_BIOMETRIC_USERNAME, null);
    }

    public String getBiometricEmail() {
        return prefs.getString(KEY_BIOMETRIC_EMAIL, null);
    }

    public String getBiometricPassword() {
        return prefs.getString(KEY_BIOMETRIC_PASSWORD, null);
    }

    public void clearBiometricCredentials() {
        prefs.edit()
                .remove(KEY_BIOMETRIC_USERNAME)
                .remove(KEY_BIOMETRIC_EMAIL)
                .remove(KEY_BIOMETRIC_PASSWORD)
                .apply();
    }

    public boolean hasBiometricCredentials() {
        return getBiometricUsername() != null && getBiometricPassword() != null;
    }
}