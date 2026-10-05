package com.yourteam.autho.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.yourteam.autho.R;
import com.yourteam.autho.database.UserDatabase;

import java.util.regex.Pattern;

public class ForgotPasswordActivity extends AppCompatActivity {

    // Password policy: same as registration
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$");

    // UI
    private ImageView ivBack;
    private TextView tvSubtitle;
    private View dotStep1, dotStep2, dotStep3;
    private LinearLayout llStep1, llStep2, llStep3;
    private EditText etIdentifier, etPin, etNewPassword, etConfirmNewPassword;
    private TextView tvAccountFound;
    private Button btnFindAccount, btnBackStep1, btnVerifyPin, btnResetPassword;
    private ProgressBar progressBar;

    // State
    private UserDatabase db;
    private int verifiedUserId = -1;
    private String verifiedUsername = "";

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        db = new UserDatabase(this);

        initViews();
        setupListeners();
        showStep(1);
    }

    private void initViews(View view) { /* not used — see below */ }

    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        dotStep1 = findViewById(R.id.dotStep1);
        dotStep2 = findViewById(R.id.dotStep2);
        dotStep3 = findViewById(R.id.dotStep3);
        llStep1 = findViewById(R.id.llStep1);
        llStep2 = findViewById(R.id.llStep2);
        llStep3 = findViewById(R.id.llStep3);
        etIdentifier = findViewById(R.id.etIdentifier);
        etPin = findViewById(R.id.etPin);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        tvAccountFound = findViewById(R.id.tvAccountFound);
        btnFindAccount = findViewById(R.id.btnFindAccount);
        btnBackStep1 = findViewById(R.id.btnBackStep1);
        btnVerifyPin = findViewById(R.id.btnVerifyPin);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        btnFindAccount.setOnClickListener(v -> handleFindAccount());

        btnBackStep1.setOnClickListener(v -> showStep(1));

        btnVerifyPin.setOnClickListener(v -> handleVerifyPin());

        btnResetPassword.setOnClickListener(v -> handleResetPassword());
    }

    // ============================================================
    // STEP CONTROL
    // ============================================================

    private void showStep(int step) {
        llStep1.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        llStep2.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        llStep3.setVisibility(step == 3 ? View.VISIBLE : View.GONE);

        dotStep1.setBackgroundResource(step >= 1 ? R.drawable.dot_active : R.drawable.dot_inactive);
        dotStep2.setBackgroundResource(step >= 2 ? R.drawable.dot_active : R.drawable.dot_inactive);
        dotStep3.setBackgroundResource(step >= 3 ? R.drawable.dot_active : R.drawable.dot_inactive);

        switch (step) {
            case 1:
                tvSubtitle.setText(R.string.forgot_subtitle_step1);
                break;
            case 2:
                tvSubtitle.setText(R.string.forgot_subtitle_step2);
                break;
            case 3:
                tvSubtitle.setText(R.string.forgot_subtitle_step3);
                break;
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnFindAccount.setEnabled(!loading);
        btnVerifyPin.setEnabled(!loading);
        btnResetPassword.setEnabled(!loading);
    }

    // ============================================================
    // STEP 1: Find account
    // ============================================================

    private void handleFindAccount() {
        final String identifier = etIdentifier.getText().toString().trim();

        if (TextUtils.isEmpty(identifier)) {
            etIdentifier.setError(getString(R.string.forgot_error_empty));
            etIdentifier.requestFocus();
            return;
        }

        setLoading(true);

        // Simulate network delay for UX
        handler.postDelayed(() -> {
            int userId = db.findUserByIdentifier(identifier);

            setLoading(false);

            if (userId == -1) {
                etIdentifier.setError(getString(R.string.forgot_error_not_found));
                etIdentifier.requestFocus();
                Toast.makeText(this, R.string.forgot_error_not_found,
                        Toast.LENGTH_LONG).show();
                return;
            }

            // Save for next step
            verifiedUserId = userId;
            verifiedUsername = identifier;

            // Show success message
            tvAccountFound.setText(getString(R.string.forgot_account_found)
                    + " " + identifier);
            tvAccountFound.setVisibility(View.VISIBLE);

            showStep(2);
            etPin.setText("");
            etPin.requestFocus();
        }, 500);
    }

    // ============================================================
    // STEP 2: Verify PIN
    // ============================================================

    private void handleVerifyPin() {
        final String pin = etPin.getText().toString().trim();

        if (TextUtils.isEmpty(pin)) {
            etPin.setError(getString(R.string.forgot_error_empty_pin));
            etPin.requestFocus();
            return;
        }

        setLoading(true);

        handler.postDelayed(() -> {
            boolean pinOk = db.verifyPin(verifiedUserId, pin);

            setLoading(false);

            if (!pinOk) {
                etPin.setError(getString(R.string.forgot_error_wrong_pin));
                etPin.setText("");
                etPin.requestFocus();
                Toast.makeText(this, R.string.forgot_error_wrong_pin,
                        Toast.LENGTH_LONG).show();
                return;
            }

            showStep(3);
            etNewPassword.setText("");
            etConfirmNewPassword.setText("");
            etNewPassword.requestFocus();
        }, 500);
    }

    // ============================================================
    // STEP 3: Reset password
    // ============================================================

    private void handleResetPassword() {
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmNewPassword.getText().toString().trim();

        // Validate
        if (TextUtils.isEmpty(newPassword)) {
            etNewPassword.setError(getString(R.string.forgot_error_empty_password));
            etNewPassword.requestFocus();
            return;
        }

        if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
            etNewPassword.setError(getString(R.string.forgot_error_weak_password));
            etNewPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            etConfirmNewPassword.setError(getString(R.string.forgot_error_mismatch));
            etConfirmNewPassword.requestFocus();
            return;
        }

        setLoading(true);

        handler.postDelayed(() -> {
            boolean ok = db.resetPassword(verifiedUserId, newPassword);

            setLoading(false);

            if (ok) {
                Toast.makeText(this, R.string.forgot_success,
                        Toast.LENGTH_LONG).show();
                finish();   // Return to LoginActivity
            } else {
                Toast.makeText(this, "Failed to reset password. Please try again.",
                        Toast.LENGTH_LONG).show();
            }
        }, 800);
    }
}