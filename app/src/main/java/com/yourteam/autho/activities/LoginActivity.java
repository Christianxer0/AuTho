package com.yourteam.autho.activities;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.yourteam.autho.R;
import com.yourteam.autho.database.UserDatabase;
import com.yourteam.autho.models.User;
import com.yourteam.autho.utils.BiometricManagerHelper;
import com.yourteam.autho.utils.SecurityHelper;

import java.util.concurrent.Executor;

public class LoginActivity extends AppCompatActivity {

    // UI Components
    private TextInputLayout tilUsername, tilPassword;
    private TextInputEditText etUsername, etPassword;
    private Button btnLogin, btnFingerprint;
    private TextView tvForgetPassword, tvSignUp, tvSetupBiometric, tvBiometricStatus;
    private CheckBox cbRememberMe;
    private ProgressBar progressBar;

    // Helpers
    private UserDatabase userDatabase;
    private SecurityHelper securityHelper;
    private BiometricManagerHelper biometricManagerHelper;

    // Biometric
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initHelpers();
        initViews();
        setupBiometricPrompt();
        refreshBiometricUi();
        checkRememberMe();
        setupListeners();
    }

    /* ------------------------------------------------------------------ */
    /*  Initialization                                                     */
    /* ------------------------------------------------------------------ */

    private void initHelpers() {
        userDatabase = new UserDatabase(this);
        securityHelper = new SecurityHelper(this);
        biometricManagerHelper = new BiometricManagerHelper(this);
    }

    private void initViews() {
        tilUsername = findViewById(R.id.tilUsername);
        tilPassword = findViewById(R.id.tilPassword);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnFingerprint = findViewById(R.id.btnFingerprint);
        tvForgetPassword = findViewById(R.id.tvForgetPassword);
        tvSignUp = findViewById(R.id.tvSignUp);
        tvSetupBiometric = findViewById(R.id.tvSetupBiometric);
        tvBiometricStatus = findViewById(R.id.tvBiometricStatus);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        progressBar = findViewById(R.id.progressBar);
    }

    /* ------------------------------------------------------------------ */
    /*  Biometric Setup                                                    */
    /* ------------------------------------------------------------------ */

    private void setupBiometricPrompt() {
        executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode,
                                                      @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            Toast.makeText(LoginActivity.this,
                                    errString, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        performBiometricLogin();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(LoginActivity.this,
                                "Fingerprint not recognized", Toast.LENGTH_SHORT).show();
                    }
                });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login")
                .setSubtitle("Authenticate to access AuTho")
                .setNegativeButtonText("Cancel")
                .build();
    }

    private void refreshBiometricUi() {
        boolean hardwareReady = biometricManagerHelper.isBiometricAvailable();
        boolean hasCredentials = biometricManagerHelper.hasBiometricCredentials();

        if (hardwareReady && hasCredentials) {
            btnFingerprint.setVisibility(View.VISIBLE);
            tvSetupBiometric.setVisibility(View.GONE);
            tvBiometricStatus.setVisibility(View.VISIBLE);
        } else if (hardwareReady) {
            btnFingerprint.setVisibility(View.GONE);
            tvSetupBiometric.setVisibility(View.VISIBLE);
            tvBiometricStatus.setVisibility(View.GONE);
        } else {
            btnFingerprint.setVisibility(View.GONE);
            tvSetupBiometric.setVisibility(View.GONE);
            tvBiometricStatus.setVisibility(View.GONE);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Listeners                                                          */
    /* ------------------------------------------------------------------ */

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> performManualLogin());

        btnFingerprint.setOnClickListener(v -> biometricPrompt.authenticate(promptInfo));

        tvSetupBiometric.setOnClickListener(v -> {
            Toast.makeText(this,
                    "Please login manually first to enable biometric",
                    Toast.LENGTH_SHORT).show();
        });

        tvForgetPassword.setOnClickListener(v -> {
            Toast.makeText(this,
                    "Password reset coming soon", Toast.LENGTH_SHORT).show();
        });

        tvSignUp.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Remember Me                                                        */
    /* ------------------------------------------------------------------ */

    private void checkRememberMe() {
        String saved = securityHelper.getSavedUsername();
        if (!TextUtils.isEmpty(saved)) {
            etUsername.setText(saved);
            cbRememberMe.setChecked(true);
            etPassword.requestFocus();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Login Flows                                                        */
    /* ------------------------------------------------------------------ */

    private void performManualLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (!validateInputs(username, password)) return;

        setLoading(true);

        new Thread(() -> {
            User user = userDatabase.loginUser(username, password);

            runOnUiThread(() -> {
                setLoading(false);
                if (user != null && user.isAuthenticated()) {
                    onManualLoginSuccess(user, password);
                } else {
                    onLoginFailure();
                }
            });
        }).start();
    }

    private void performBiometricLogin() {
        String username = biometricManagerHelper.getBiometricUsername();
        String password = biometricManagerHelper.getBiometricPassword();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this,
                    "Saved biometric credentials missing. Please login manually.",
                    Toast.LENGTH_LONG).show();
            refreshBiometricUi();
            return;
        }

        setLoading(true);

        new Thread(() -> {
            User user = userDatabase.loginUser(username, password);

            runOnUiThread(() -> {
                setLoading(false);
                if (user != null && user.isAuthenticated()) {
                    Toast.makeText(this,
                            "Welcome back, " + user.getUsername() + "!",
                            Toast.LENGTH_SHORT).show();
                    navigateToMain(user);
                } else {
                    Toast.makeText(this,
                            "Biometric login failed. Please login manually.",
                            Toast.LENGTH_LONG).show();
                    biometricManagerHelper.clearBiometricCredentials();
                    refreshBiometricUi();
                }
            });
        }).start();
    }

    /* ------------------------------------------------------------------ */
    /*  Validation & Results                                               */
    /* ------------------------------------------------------------------ */

    private boolean validateInputs(String username, String password) {
        boolean ok = true;

        if (TextUtils.isEmpty(username)) {
            tilUsername.setError("Username or email required");
            ok = false;
        } else {
            tilUsername.setError(null);
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password required");
            ok = false;
        } else {
            tilPassword.setError(null);
        }

        return ok;
    }

    private void onManualLoginSuccess(User user, String plainPassword) {
        // Session
        userDatabase.updateLastLogin(user.getId());
        String token = java.util.UUID.randomUUID().toString();
        userDatabase.saveSessionToken(user.getId(), token);

        // Remember Me
        if (cbRememberMe.isChecked()) {
            securityHelper.saveUsername(user.getUsername());
        } else {
            securityHelper.clearSavedUsername();
        }

        // Save biometric credentials if hardware is ready and not already saved
        if (biometricManagerHelper.isBiometricAvailable()
                && !biometricManagerHelper.hasBiometricCredentials()) {
            biometricManagerHelper.saveBiometricCredentials(
                    user.getUsername(), user.getEmail(), plainPassword);
        }

        Toast.makeText(this, "Welcome, " + user.getUsername() + "!",
                Toast.LENGTH_SHORT).show();

        navigateToMain(user);
    }

    private void onLoginFailure() {
        tilPassword.setError("Invalid username or password");
        etPassword.setText("");
        etPassword.requestFocus();
        Toast.makeText(this, "Invalid credentials", Toast.LENGTH_LONG).show();
    }

    private void navigateToMain(User user) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("user_id", user.getId());
        intent.putExtra("username", user.getUsername());
        intent.putExtra("email", user.getEmail());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /* ------------------------------------------------------------------ */
    /*  Loading State                                                      */
    /* ------------------------------------------------------------------ */

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnFingerprint.setEnabled(!loading);
        btnLogin.setText(loading ? "" : getString(R.string.login_button));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userDatabase != null) {
            userDatabase.close();
        }
    }
}