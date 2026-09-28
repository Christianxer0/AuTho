package com.yourteam.autho.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.yourteam.autho.R;
import com.yourteam.autho.utils.MalwareDetector;
import com.yourteam.autho.utils.NativeHelper;
import com.yourteam.autho.utils.SecurityHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SecurityFragment extends Fragment {

    // ---- Score
    private TextView tvProtectionIcon, tvProtectionTitle, tvProtectionSubtitle;
    private TextView tvSecurityScore, tvLastScanTime, tvSecurityStatus;
    private ProgressBar progressSecurity, progressSecurityScore;
    private Button btnRunFullScan;

    // ---- Root
    private TextView tvRootProtection;
    private Button btnCheckRoot;

    // ---- Permissions
    private TextView tvPermissionSummary;
    private LinearLayout llRiskyApps;
    private Button btnAuditPermissions;

    // ---- Malware
    private TextView tvMalwareResult;
    private ProgressBar progressMalware;
    private LinearLayout llThreats;
    private Button btnScanMalware;

    // ---- Privacy
    private TextView tvPrivacyLevel, tvPrivacyDetails;
    private Button btnManagePrivacy;

    // ---- Logs / Recommendations
    private LinearLayout llLogs, llRecommendations;
    private TextView tvLogsEmpty, tvRecommendationsEmpty;

    private SecurityHelper securityHelper;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private boolean isRooted = false;
    private int lastThreatCount = 0;
    private int lastRiskyAppCount = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_security, container, false);

        securityHelper = new SecurityHelper(requireContext());

        initViews(view);
        setupListeners();
        loadSecurityLogs();
        runInitialScan();

        return view;
    }

    private void initViews(View view) {
        tvProtectionIcon = view.findViewById(R.id.tvProtectionIcon);
        tvProtectionTitle = view.findViewById(R.id.tvProtectionTitle);
        tvProtectionSubtitle = view.findViewById(R.id.tvProtectionSubtitle);
        tvSecurityScore = view.findViewById(R.id.tvSecurityScore);
        tvLastScanTime = view.findViewById(R.id.tvLastScanTime);
        tvSecurityStatus = view.findViewById(R.id.tvSecurityStatus);
        progressSecurity = view.findViewById(R.id.progressSecurity);
        progressSecurityScore = view.findViewById(R.id.progressSecurityScore);
        btnRunFullScan = view.findViewById(R.id.btnRunFullScan);

        tvRootProtection = view.findViewById(R.id.tvRootProtection);
        btnCheckRoot = view.findViewById(R.id.btnCheckRoot);

        tvPermissionSummary = view.findViewById(R.id.tvPermissionSummary);
        llRiskyApps = view.findViewById(R.id.llRiskyApps);
        btnAuditPermissions = view.findViewById(R.id.btnAuditPermissions);

        tvMalwareResult = view.findViewById(R.id.tvMalwareResult);
        progressMalware = view.findViewById(R.id.progressMalware);
        llThreats = view.findViewById(R.id.llThreats);
        btnScanMalware = view.findViewById(R.id.btnScanMalware);

        tvPrivacyLevel = view.findViewById(R.id.tvPrivacyLevel);
        tvPrivacyDetails = view.findViewById(R.id.tvPrivacyDetails);
        btnManagePrivacy = view.findViewById(R.id.btnManagePrivacy);

        llLogs = view.findViewById(R.id.llLogs);
        llRecommendations = view.findViewById(R.id.llRecommendations);
        tvLogsEmpty = view.findViewById(R.id.tvLogsEmpty);
        tvRecommendationsEmpty = view.findViewById(R.id.tvRecommendationsEmpty);
    }

    private void setupListeners() {
        btnRunFullScan.setOnClickListener(v -> runFullScan());
        btnCheckRoot.setOnClickListener(v -> checkRoot());
        btnAuditPermissions.setOnClickListener(v -> auditPermissions());
        btnScanMalware.setOnClickListener(v -> scanMalware());
        btnManagePrivacy.setOnClickListener(v -> showPrivacyDialog());
    }

    // ============================================================
    // INITIAL SCAN
    // ============================================================

    private void runInitialScan() {
        progressSecurity.setVisibility(View.VISIBLE);
        tvProtectionTitle.setText("CHECKING...");
        tvProtectionSubtitle.setText("Running security checks...");

        Context appContext = requireContext().getApplicationContext();

        executor.execute(() -> {
            // Root check
            try {
                isRooted = NativeHelper.isDeviceRooted();
            } catch (Throwable t) {
                isRooted = false;
            }

            // Malware + permission checks
            List<MalwareDetector.Threat> threats = MalwareDetector.scanForMalware(appContext);
            List<MalwareDetector.PermissionRisk> risks = MalwareDetector.auditPermissions(appContext);
            lastThreatCount = threats.size();
            lastRiskyAppCount = risks.size();

            int score = MalwareDetector.computeSecurityScore(
                    appContext, isRooted, lastThreatCount, lastRiskyAppCount);

            handler.post(() -> {
                if (!isAdded()) return;
                progressSecurity.setVisibility(View.GONE);
                updateScoreUI(score);
                updateRootUI();
                updateLastScanTime();
                loadSecurityLogs();
                updateRecommendations();
            });
        });
    }

    private void runFullScan() {
        progressSecurity.setVisibility(View.VISIBLE);
        tvSecurityStatus.setText("Running full security scan...");
        btnRunFullScan.setEnabled(false);

        securityHelper.addSecurityLog("success", "Full security scan started");

        Context appContext = requireContext().getApplicationContext();

        executor.execute(() -> {
            try { isRooted = NativeHelper.isDeviceRooted(); } catch (Throwable ignored) {}

            List<MalwareDetector.Threat> threats = MalwareDetector.scanForMalware(appContext);
            List<MalwareDetector.PermissionRisk> risks = MalwareDetector.auditPermissions(appContext);
            lastThreatCount = threats.size();
            lastRiskyAppCount = risks.size();

            int score = MalwareDetector.computeSecurityScore(
                    appContext, isRooted, lastThreatCount, lastRiskyAppCount);

            securityHelper.setLastScanTime(System.currentTimeMillis());

            handler.post(() -> {
                if (!isAdded()) return;
                progressSecurity.setVisibility(View.GONE);
                btnRunFullScan.setEnabled(true);
                updateScoreUI(score);
                updateRootUI();
                updateLastScanTime();
                loadSecurityLogs();
                updateRecommendations();
                tvSecurityStatus.setText(String.format("✅ Scan complete · %d threats · %d risky apps",
                        lastThreatCount, lastRiskyAppCount));
                securityHelper.addSecurityLog("success",
                        "Scan complete · " + lastThreatCount + " threats found");
            });
        });
    }

    // ============================================================
    // SCORE UI
    // ============================================================

    private void updateScoreUI(int score) {
        tvSecurityScore.setText(String.format(Locale.US, "%d/100", score));
        progressSecurityScore.setProgress(score);

        int color;
        String icon, title, subtitle;
        if (score >= 85) {
            color = ContextCompat.getColor(requireContext(), R.color.status_green);
            icon = "🛡️";
            title = "PROTECTED";
            subtitle = "Your device is well protected";
        } else if (score >= 60) {
            color = ContextCompat.getColor(requireContext(), R.color.status_yellow);
            icon = "⚠️";
            title = "AT RISK";
            subtitle = "Some issues need attention";
        } else {
            color = ContextCompat.getColor(requireContext(), R.color.status_red);
            icon = "🚨";
            title = "VULNERABLE";
            subtitle = "Multiple security issues detected";
        }

        tvSecurityScore.setTextColor(color);
        progressSecurityScore.setProgressTintList(
                android.content.res.ColorStateList.valueOf(color));
        tvProtectionIcon.setText(icon);
        tvProtectionTitle.setText(title);
        tvProtectionSubtitle.setText(subtitle);
    }

    private void updateLastScanTime() {
        long last = securityHelper.getLastScanTime();
        if (last == 0) {
            tvLastScanTime.setText("Last Scan: Never");
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        tvLastScanTime.setText("Last Scan: " + sdf.format(new Date(last)));
    }

    // ============================================================
    // ROOT CHECK
    // ============================================================

    private void checkRoot() {
        btnCheckRoot.setEnabled(false);
        tvRootProtection.setText("Checking...");

        executor.execute(() -> {
            boolean rooted;
            try {
                rooted = NativeHelper.isDeviceRooted();
            } catch (Throwable t) {
                rooted = false;
            }
            isRooted = rooted;
            final boolean r = rooted;

            handler.post(() -> {
                if (!isAdded()) return;
                btnCheckRoot.setEnabled(true);
                updateRootUI();
                securityHelper.addSecurityLog(
                        r ? "warning" : "success",
                        r ? "Root detected" : "No root detected");
                loadSecurityLogs();
            });
        });
    }

    private void updateRootUI() {
        if (isRooted) {
            tvRootProtection.setText("⚠️ Root Detected · Device may be at risk");
            tvRootProtection.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_red));
        } else {
            tvRootProtection.setText("✅ No Root Detected · Device is secure");
            tvRootProtection.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_green));
        }
    }

    // ============================================================
    // PERMISSION AUDIT
    // ============================================================

    private void auditPermissions() {
        btnAuditPermissions.setEnabled(false);
        tvPermissionSummary.setText("Auditing permissions...");
        llRiskyApps.removeAllViews();

        Context appContext = requireContext().getApplicationContext();

        executor.execute(() -> {
            List<MalwareDetector.PermissionRisk> risks =
                    MalwareDetector.auditPermissions(appContext);

            handler.post(() -> {
                if (!isAdded()) return;
                btnAuditPermissions.setEnabled(true);

                if (risks.isEmpty()) {
                    tvPermissionSummary.setText("✅ No apps with dangerous permissions");
                    tvPermissionSummary.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.status_green));
                    securityHelper.addSecurityLog("success", "Permission audit clean");
                    loadSecurityLogs();
                    return;
                }

                tvPermissionSummary.setText(String.format(Locale.US,
                        "⚠️ %d apps request dangerous permissions", risks.size()));
                tvPermissionSummary.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.status_yellow));

                // Show top 5
                int shown = Math.min(5, risks.size());
                for (int i = 0; i < shown; i++) {
                    MalwareDetector.PermissionRisk r = risks.get(i);
                    View row = LayoutInflater.from(requireContext())
                            .inflate(R.layout.item_log, llRiskyApps, false);

                    TextView icon = row.findViewById(R.id.itemLogIcon);
                    TextView title = row.findViewById(R.id.itemLogTitle);
                    TextView time = row.findViewById(R.id.itemLogTime);

                    icon.setText("🔑");
                    title.setText(r.appName);
                    time.setText(String.join(", ", r.dangerousPermissions));

                    llRiskyApps.addView(row);
                }

                securityHelper.addSecurityLog("warning",
                        risks.size() + " apps with dangerous permissions");
                loadSecurityLogs();
                updateRecommendations();
            });
        });
    }

    // ============================================================
    // MALWARE SCAN
    // ============================================================

    private void scanMalware() {
        btnScanMalware.setEnabled(false);
        progressMalware.setVisibility(View.VISIBLE);
        progressMalware.setProgress(0);
        tvMalwareResult.setText("Scanning installed apps...");
        llThreats.removeAllViews();

        Context appContext = requireContext().getApplicationContext();

        executor.execute(() -> {
            List<MalwareDetector.Threat> threats = MalwareDetector.scanForMalware(appContext);
            lastThreatCount = threats.size();

            // Animate progress
            for (int p = 0; p <= 100; p += 20) {
                final int progress = p;
                handler.post(() -> {
                    if (!isAdded()) return;
                    progressMalware.setProgress(progress);
                });
                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            }

            handler.post(() -> {
                if (!isAdded()) return;
                btnScanMalware.setEnabled(true);
                progressMalware.setVisibility(View.GONE);

                if (threats.isEmpty()) {
                    tvMalwareResult.setText("✅ No threats found");
                    tvMalwareResult.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.status_green));
                    securityHelper.addSecurityLog("success", "Malware scan · No threats");
                } else {
                    tvMalwareResult.setText(String.format(Locale.US,
                            "🚨 %d potential threats found", threats.size()));
                    tvMalwareResult.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.status_red));

                    for (MalwareDetector.Threat t : threats) {
                        View row = LayoutInflater.from(requireContext())
                                .inflate(R.layout.item_threat, llThreats, false);

                        TextView icon = row.findViewById(R.id.itemThreatIcon);
                        TextView name = row.findViewById(R.id.itemThreatName);
                        TextView details = row.findViewById(R.id.itemThreatDetails);
                        Button action = row.findViewById(R.id.itemThreatAction);

                        icon.setText(t.severity >= 3 ? "🚨" : "⚠️");
                        name.setText(t.appName);
                        details.setText(t.reason);

                        action.setOnClickListener(v -> showThreatDialog(t));
                    }
                    securityHelper.addSecurityLog("error",
                            threats.size() + " potential malware threats detected");
                }

                loadSecurityLogs();
                updateRecommendations();
            });
        });
    }

    private void showThreatDialog(MalwareDetector.Threat t) {
        new AlertDialog.Builder(requireContext())
                .setTitle("⚠️ " + t.appName)
                .setMessage("Package: " + t.packageName + "\n\nReason: " + t.reason +
                        "\n\nSeverity: " + (t.severity >= 3 ? "HIGH" : "MEDIUM"))
                .setPositiveButton("Open App Info", (d, w) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(android.net.Uri.parse("package:" + t.packageName));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Cannot open app info", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ============================================================
    // PRIVACY
    // ============================================================

    private void showPrivacyDialog() {
        String[] options = {
                "App Permissions",
                "Location Settings",
                "Privacy Settings",
                "Security Settings"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("🔒 Privacy & Security Settings")
                .setItems(options, (dialog, which) -> {
                    Intent intent = null;
                    switch (which) {
                        case 0:
                            intent = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
                            break;
                        case 1:
                            intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            break;
                        case 2:
                            intent = new Intent(Settings.ACTION_PRIVACY_SETTINGS);
                            break;
                        case 3:
                            intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                            break;
                    }
                    if (intent != null) {
                        try { startActivity(intent); }
                        catch (Exception e) {
                            Toast.makeText(requireContext(), "Settings not available", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ============================================================
    // LOGS
    // ============================================================

    private void loadSecurityLogs() {
        // Clear old rows (keep index 0 = empty-state TextView)
        int childCount = llLogs.getChildCount();
        if (childCount > 1) llLogs.removeViews(1, childCount - 1);

        String[] logs = securityHelper.getSecurityLogs();
        if (logs.length == 0) {
            tvLogsEmpty.setVisibility(View.VISIBLE);
            tvLogsEmpty.setText("No security events yet");
            return;
        }
        tvLogsEmpty.setVisibility(View.GONE);

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        int shown = Math.min(10, logs.length);
        for (int i = 0; i < shown; i++) {
            String line = logs[i];
            String[] parts = line.split("\\|");
            if (parts.length < 3) continue;

            String type = parts[0];
            String title = parts[1];
            long ts;
            try { ts = Long.parseLong(parts[2]); } catch (Exception e) { ts = 0; }

            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_log, llLogs, false);

            TextView icon = row.findViewById(R.id.itemLogIcon);
            TextView titleView = row.findViewById(R.id.itemLogTitle);
            TextView timeView = row.findViewById(R.id.itemLogTime);

            String iconText;
            switch (type) {
                case "success": iconText = "✅"; break;
                case "warning": iconText = "⚠️"; break;
                case "error":   iconText = "❌"; break;
                default:        iconText = "ℹ️";
            }
            icon.setText(iconText);
            titleView.setText(title);
            timeView.setText(ts > 0 ? sdf.format(new Date(ts)) : "—");

            llLogs.addView(row);
        }
    }

    // ============================================================
    // RECOMMENDATIONS
    // ============================================================

    private void updateRecommendations() {
        int childCount = llRecommendations.getChildCount();
        if (childCount > 1) llRecommendations.removeViews(1, childCount - 1);

        boolean hasAny = false;

        if (isRooted) {
            addRecommendation("Device is rooted — consider unrooting for security", "Learn",
                    v -> Toast.makeText(requireContext(),
                            "Root increases attack surface. Remove if not needed.",
                            Toast.LENGTH_LONG).show());
            hasAny = true;
        }

        if (lastThreatCount > 0) {
            addRecommendation(lastThreatCount + " potential threats detected", "Review",
                    v -> scanMalware());
            hasAny = true;
        }

        if (lastRiskyAppCount > 5) {
            addRecommendation(lastRiskyAppCount + " apps with dangerous permissions", "Audit",
                    v -> auditPermissions());
            hasAny = true;
        }

        // ADB check
        try {
            int adbEnabled = Settings.Global.getInt(
                    requireContext().getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            if (adbEnabled == 1) {
                addRecommendation("USB Debugging is enabled", "Disable",
                        v -> {
                            try {
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                            } catch (Exception ignored) {}
                        });
                hasAny = true;
            }
        } catch (Exception ignored) {}

        if (!hasAny) {
            tvRecommendationsEmpty.setVisibility(View.VISIBLE);
            tvRecommendationsEmpty.setText("✅ No recommendations — device is well configured");
        } else {
            tvRecommendationsEmpty.setVisibility(View.GONE);
        }
    }

    private void addRecommendation(String text, String actionLabel, View.OnClickListener action) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_recommendation, llRecommendations, false);

        TextView icon = row.findViewById(R.id.itemRecIcon);
        TextView textView = row.findViewById(R.id.itemRecText);
        Button actionBtn = row.findViewById(R.id.itemRecAction);

        icon.setText("💡");
        textView.setText(text);
        actionBtn.setText(actionLabel);
        actionBtn.setOnClickListener(action);

        llRecommendations.addView(row);
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}