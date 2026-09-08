package com.yourteam.autho.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.yourteam.autho.R;
import com.yourteam.autho.database.UserDatabase;
import com.yourteam.autho.models.User;
import com.yourteam.autho.utils.BiometricManagerHelper;

public class RegisterActivity extends AppCompatActivity {

    // UI
    private ImageView ivBack;
    private TextInputLayout tilUsername, tilEmail, tilPassword, tilConfirmPassword, tilPin;
    private TextInputEditText etUsername, etEmail, etPassword, etConfirmPassword, etPin;
    private CheckBox cbEnableBiometric;
    private TextView tvBiometric, tvLogin;
    private Button btnRegister;
    private ProgressBar progressBar;

    // Helpers
    private UserDatabase userDatabase;
    private BiometricManagerHelper biometricManagerHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initHelpers();
        initViews();
        setupBiometricVisibility();
        setupListeners();
    }

    private void initHelpers() {
        userDatabase = new UserDatabase(this);
        biometricManagerHelper = new BiometricManagerHelper(this);
    }

    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        tilUsername = findViewById(R.id.tilUsername);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        tilPin = findViewById(R.id.tilPin);
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etPin = findViewById(R.id.etPin);
        cbEnableBiometric = findViewById(R.id.cbEnableBiometric);
        tvBiometric = findViewById(R.id.tvBiometric);
        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);
        tvLogin = findViewById(R.id.tvLogin);
    }

    private void setupBiometricVisibility() {
        boolean available = biometricManagerHelper.isBiometricAvailable();
        cbEnableBiometric.setVisibility(available ? View.VISIBLE : View.GONE);
        tvBiometric.setVisibility(available ? View.VISIBLE : View.GONE);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());
        tvLogin.setOnClickListener(v -> finish());
        btnRegister.setOnClickListener(v -> performRegistration());
    }

    private void performRegistration() {
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();
        String pin = etPin.getText().toString().trim();

        if (!validateInputs(username, email, password, confirmPassword, pin)) {
            return;
        }

        setLoading(true);

        new Thread(() -> {
            boolean success = registerUser(username, email, password, pin);

            runOnUiThread(() -> {
                setLoading(false);
                if (success) {
                    Toast.makeText(RegisterActivity.this,
                            "Registration successful! Please login.",
                            Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    Toast.makeText(RegisterActivity.this,
                            "Registration failed. Username or email may already exist.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private boolean validateInputs(String username, String email, String password,
                                   String confirmPassword, String pin) {
        boolean ok = true;

        if (TextUtils.isEmpty(username) || username.length() < 3) {
            tilUsername.setError("At least 3 characters");
            ok = false;
        } else {
            tilUsername.setError(null);
        }

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Valid email required");
            ok = false;
        } else {
            tilEmail.setError(null);
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            tilPassword.setError("At least 6 characters");
            ok = false;
        } else {
            tilPassword.setError(null);
        }

        if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError("Passwords do not match");
            ok = false;
        } else {
            tilConfirmPassword.setError(null);
        }

        if (TextUtils.isEmpty(pin) || pin.length() < 4 || pin.length() > 6) {
            tilPin.setError("4-6 digits required");
            ok = false;
        } else {
            tilPin.setError(null);
        }

        return ok;
    }

    private boolean registerUser(String username, String email, String password, String pin) {
        if (userDatabase.userExists(username, email)) {
            return false;
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setBiometricEnabled(cbEnableBiometric.isChecked());

        boolean success = userDatabase.registerUser(user, password);
        if (!success) {
            return false;
        }

        // Set PIN for the newly created user
        User newUser = userDatabase.loginUser(username, password);
        if (newUser != null) {
            userDatabase.setPin(newUser.getId(), pin);

            if (cbEnableBiometric.isChecked()) {
                biometricManagerHelper.saveBiometricCredentials(
                        username, email, password);
            }
        }

        return true;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
        btnRegister.setText(loading ? "" : getString(R.string.register_button));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userDatabase != null) {
            userDatabase.close();
        }
    }
}