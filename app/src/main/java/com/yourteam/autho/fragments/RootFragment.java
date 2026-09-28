package com.yourteam.autho.fragments;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.yourteam.autho.R;
import com.yourteam.autho.models.AppInfo;
import com.yourteam.autho.utils.BloatwareDetector;
import com.yourteam.autho.utils.NativeHelper;
import com.yourteam.autho.utils.SecurityHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RootFragment extends Fragment {

    // ---- Root status
    private TextView tvRootIcon, tvRootStatus, tvRootDetails, tvSessionTimer;
    private LinearLayout llRootSession;
    private ProgressBar progressRoot;
    private Button btnAuthRoot, btnLogoutRoot;

    // ---- Bloatware
    private TextView tvTotalApps, tvSafeApps, tvRemovedApps;
    private TextView tvAppsEmpty, tvRootStatusLine;
    private LinearLayout llApps;
    private Button btnScanApps, btnFilterAll, btnFilterBloat, btnFilterRemoved;

    // ---- Optimization
    private Button btnKillProcesses, btnClearCache, btnDropCaches;

    // ---- State
    private boolean isRooted = false;
    private boolean rootSessionActive = false;
    private final List<AppInfo> allApps = new ArrayList<>();
    private int currentFilter = FILTER_ALL;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_BLOAT = 1;
    private static final int FILTER_REMOVED = 2;

    private static final int ROOT_SESSION_DURATION_SEC = 5 * 60;
    private int remainingRootSeconds = ROOT_SESSION_DURATION_SEC;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SecurityHelper securityHelper;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_root, container, false);

        securityHelper = new SecurityHelper(requireContext());

        initViews(view);
        setupListeners();
        checkRootStatus();

        return view;
    }

    private void initViews(View view) {
        tvRootIcon = view.findViewById(R.id.tvRootIcon);
        tvRootStatus = view.findViewById(R.id.tvRootStatus);
        tvRootDetails = view.findViewById(R.id.tvRootDetails);
        tvSessionTimer = view.findViewById(R.id.tvSessionTimer);
        llRootSession = view.findViewById(R.id.llRootSession);
        progressRoot = view.findViewById(R.id.progressRoot);
        btnAuthRoot = view.findViewById(R.id.btnAuthRoot);
        btnLogoutRoot = view.findViewById(R.id.btnLogoutRoot);

        tvTotalApps = view.findViewById(R.id.tvTotalApps);
        tvSafeApps = view.findViewById(R.id.tvSafeApps);
        tvRemovedApps = view.findViewById(R.id.tvRemovedApps);
        tvAppsEmpty = view.findViewById(R.id.tvAppsEmpty);
        tvRootStatusLine = view.findViewById(R.id.tvRootStatusLine);
        llApps = view.findViewById(R.id.llApps);
        btnScanApps = view.findViewById(R.id.btnScanApps);
        btnFilterAll = view.findViewById(R.id.btnFilterAll);
        btnFilterBloat = view.findViewById(R.id.btnFilterBloat);
        btnFilterRemoved = view.findViewById(R.id.btnFilterRemoved);

        btnKillProcesses = view.findViewById(R.id.btnKillProcesses);
        btnClearCache = view.findViewById(R.id.btnClearCache);
        btnDropCaches = view.findViewById(R.id.btnDropCaches);
    }

    private void setupListeners() {
        btnAuthRoot.setOnClickListener(v -> showPinDialog());
        btnLogoutRoot.setOnClickListener(v -> endRootSession());
        btnScanApps.setOnClickListener(v -> scanApps());
        btnFilterAll.setOnClickListener(v -> { currentFilter = FILTER_ALL; renderAppList(); });
        btnFilterBloat.setOnClickListener(v -> { currentFilter = FILTER_BLOAT; renderAppList(); });
        btnFilterRemoved.setOnClickListener(v -> { currentFilter = FILTER_REMOVED; renderAppList(); });

        btnKillProcesses.setOnClickListener(v -> runOptimization("kill"));
        btnClearCache.setOnClickListener(v -> runOptimization("cache"));
        btnDropCaches.setOnClickListener(v -> runOptimization("drop"));
    }

    // ============================================================
    // ROOT STATUS
    // ============================================================

    private void checkRootStatus() {
        progressRoot.setVisibility(View.VISIBLE);
        tvRootStatus.setText("Checking root...");
        tvRootDetails.setText("Please wait...");

        executor.execute(() -> {
            boolean rooted = false;
            try {
                rooted = NativeHelper.isDeviceRooted();
            } catch (Throwable ignored) {}

            final boolean r = rooted;
            handler.post(() -> {
                if (!isAdded()) return;
                progressRoot.setVisibility(View.GONE);
                isRooted = r;

                if (r) {
                    tvRootIcon.setText("✅");
                    tvRootStatus.setText("Device is Rooted");
                    tvRootStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green));
                    tvRootDetails.setText("Root access available. Authenticate to enable tools.");
                    btnAuthRoot.setVisibility(View.VISIBLE);
                    btnLogoutRoot.setVisibility(View.GONE);
                    llRootSession.setVisibility(View.GONE);
                } else {
                    tvRootIcon.setText("🔒");
                    tvRootStatus.setText("Device NOT Rooted");
                    tvRootStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_red));
                    tvRootDetails.setText("Root tools require a rooted device. Diagnostics still work.");
                    btnAuthRoot.setVisibility(View.GONE);
                    btnLogoutRoot.setVisibility(View.GONE);
                    llRootSession.setVisibility(View.GONE);
                }
            });
        });
    }

    /**
     * Inline PIN authentication dialog (replaces the missing AuthActivity).
     */
    private void showPinDialog() {
        if (!isRooted) {
            Toast.makeText(requireContext(), "Device is not rooted", Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Enter Security PIN");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(requireContext())
                .setTitle(" Root Authentication")
                .setMessage("Enter your 4–6 digit security PIN to enable root tools.")
                .setView(input)
                .setPositiveButton("Verify", (dialog, which) -> {
                    String pin = input.getText().toString().trim();
                    if (pin.isEmpty()) {
                        Toast.makeText(requireContext(), "PIN cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Verify PIN using SecurityHelper
                    boolean ok = securityHelper.verifyPin(pin);
                    if (ok) {
                        startRootSession();
                    } else {
                        Toast.makeText(requireContext(), " Invalid PIN", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startRootSession() {
        rootSessionActive = true;
        remainingRootSeconds = ROOT_SESSION_DURATION_SEC;

        llRootSession.setVisibility(View.VISIBLE);
        btnAuthRoot.setVisibility(View.GONE);
        btnLogoutRoot.setVisibility(View.VISIBLE);
        tvRootStatus.setText("Root Session Active");
        tvRootDetails.setText("You can now perform system modifications.");

        handler.post(tickTimer);
        Toast.makeText(requireContext(), " Root access granted", Toast.LENGTH_SHORT).show();
    }

    private final Runnable tickTimer = new Runnable() {
        @Override
        public void run() {
            if (!rootSessionActive) return;
            remainingRootSeconds--;
            int min = remainingRootSeconds / 60;
            int sec = remainingRootSeconds % 60;
            tvSessionTimer.setText(String.format(Locale.US, "%d:%02d", min, sec));

            if (remainingRootSeconds <= 0) {
                endRootSession();
                if (getContext() != null) {
                    Toast.makeText(getContext(),
                            "Root session expired. Please re-authenticate.", Toast.LENGTH_LONG).show();
                }
                return;
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void endRootSession() {
        rootSessionActive = false;
        handler.removeCallbacks(tickTimer);
        llRootSession.setVisibility(View.GONE);
        btnLogoutRoot.setVisibility(View.GONE);
        if (isRooted) {
            btnAuthRoot.setVisibility(View.VISIBLE);
            tvRootStatus.setText("Device is Rooted");
            tvRootDetails.setText("Root access available. Authenticate to enable tools.");
        }
    }

    private boolean requireRootSession() {
        if (!rootSessionActive) {
            Toast.makeText(requireContext(),
                    "Please authenticate root access first", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // ============================================================
    // BLOATWARE SCAN
    // ============================================================

    private void scanApps() {
        tvAppsEmpty.setVisibility(View.VISIBLE);
        tvAppsEmpty.setText("Scanning installed apps...");
        progressRoot.setVisibility(View.VISIBLE);

        PackageManager pm = requireContext().getPackageManager();
        executor.execute(() -> {
            List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            List<AppInfo> results = new ArrayList<>();
            for (ApplicationInfo ai : installed) {
                String pkg = ai.packageName;
                boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean protectedApp = BloatwareDetector.isProtected(pkg);
                boolean bloat = BloatwareDetector.isBloatware(pkg);
                String name = pm.getApplicationLabel(ai).toString();
                Drawable icon;
                try {
                    icon = pm.getApplicationIcon(ai);
                } catch (Exception e) {
                    icon = null;
                }

                AppInfo info = new AppInfo(name, pkg, icon, isSystem, bloat, protectedApp);
                results.add(info);
            }

            // Sort: bloatware first, then user apps, then system
            results.sort((a, b) -> {
                if (a.isBloatware != b.isBloatware) return a.isBloatware ? -1 : 1;
                if (a.isSystemApp != b.isSystemApp) return a.isSystemApp ? 1 : -1;
                return a.appName.compareToIgnoreCase(b.appName);
            });

            handler.post(() -> {
                if (!isAdded()) return;
                progressRoot.setVisibility(View.GONE);
                allApps.clear();
                allApps.addAll(results);
                updateSummary();
                renderAppList();
            });
        });
    }

    private void updateSummary() {
        int total = allApps.size();
        int safe = 0, removed = 0;
        for (AppInfo a : allApps) {
            if (a.isRemoved) removed++;
            else if (BloatwareDetector.canBeRemoved(a.packageName, a.isSystemApp)) safe++;
        }
        tvTotalApps.setText(String.valueOf(total));
        tvSafeApps.setText(String.valueOf(safe));
        tvRemovedApps.setText(String.valueOf(removed));
    }

    private void renderAppList() {
        // Clear old rows (keep the empty-state TextView at index 0)
        int childCount = llApps.getChildCount();
        if (childCount > 1) llApps.removeViews(1, childCount - 1);

        List<AppInfo> filtered = new ArrayList<>();
        for (AppInfo a : allApps) {
            if (currentFilter == FILTER_ALL) {
                if (a.isBloatware || !a.isSystemApp) filtered.add(a);
            } else if (currentFilter == FILTER_BLOAT) {
                if (a.isBloatware) filtered.add(a);
            } else if (currentFilter == FILTER_REMOVED) {
                if (a.isRemoved) filtered.add(a);
            }
        }

        if (filtered.isEmpty()) {
            tvAppsEmpty.setVisibility(View.VISIBLE);
            tvAppsEmpty.setText("No apps match this filter");
            return;
        }
        tvAppsEmpty.setVisibility(View.GONE);

        for (AppInfo a : filtered) {
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_app, llApps, false);

            ImageView icon = row.findViewById(R.id.itemAppIcon);
            TextView name = row.findViewById(R.id.itemAppName);
            TextView pkg = row.findViewById(R.id.itemAppPackage);
            TextView tag = row.findViewById(R.id.itemAppTag);
            Button action = row.findViewById(R.id.itemAppAction);

            if (a.icon != null) icon.setImageDrawable(a.icon);
            name.setText(a.appName);
            pkg.setText(a.packageName);

            if (a.isRemoved) {
                tag.setText("Removed");
                tag.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_yellow));
                action.setText("Restore");
                action.setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), R.color.status_green));
                action.setOnClickListener(v -> restoreApp(a));
            } else if (a.isProtected) {
                tag.setText("Protected System App");
                tag.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_red));
                action.setText("Protected");
                action.setEnabled(false);
                action.setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), R.color.dark_navy));
            } else if (BloatwareDetector.canBeRemoved(a.packageName, a.isSystemApp)) {
                tag.setText(a.isBloatware ? "Bloatware · Safe to remove" : "User App");
                tag.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green));
                action.setText("Remove");
                action.setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), R.color.status_red));
                action.setOnClickListener(v -> confirmRemove(a));
            } else {
                tag.setText("System App");
                tag.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                action.setText("Protected");
                action.setEnabled(false);
                action.setBackgroundTintList(
                        ContextCompat.getColorStateList(requireContext(), R.color.dark_navy));
            }

            llApps.addView(row);
        }
    }

    private void confirmRemove(AppInfo app) {
        if (!requireRootSession()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Remove App?")
                .setMessage("Remove \"" + app.appName + "\"?\n\n" + app.packageName +
                        "\n\nThis will uninstall the app for user 0. You can restore it later.")
                .setPositiveButton("Remove", (d, w) -> removeApp(app))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeApp(AppInfo app) {
        tvRootStatusLine.setText(String.format("Removing %s...", app.appName));

        executor.execute(() -> {
            boolean ok;
            try {
                ok = NativeHelper.uninstallPackage(app.packageName, app.isSystemApp);
            } catch (Throwable t) {
                ok = false;
            }
            final boolean success = ok;

            handler.post(() -> {
                if (!isAdded()) return;
                if (success) {
                    app.isRemoved = true;
                    tvRootStatusLine.setText(String.format(" Removed %s", app.appName));
                } else {
                    tvRootStatusLine.setText(String.format(" Failed to remove %s", app.appName));
                }
                updateSummary();
                renderAppList();
            });
        });
    }

    private void restoreApp(AppInfo app) {
        if (!requireRootSession()) return;

        tvRootStatusLine.setText(String.format("Restoring %s...", app.appName));

        executor.execute(() -> {
            boolean ok;
            try {
                ok = NativeHelper.restorePackage(app.packageName);
            } catch (Throwable t) {
                ok = false;
            }
            final boolean success = ok;

            handler.post(() -> {
                if (!isAdded()) return;
                if (success) {
                    app.isRemoved = false;
                    tvRootStatusLine.setText(String.format(" Restored %s", app.appName));
                } else {
                    tvRootStatusLine.setText(String.format(" Failed to restore %s", app.appName));
                }
                updateSummary();
                renderAppList();
            });
        });
    }

    // ============================================================
    // SYSTEM OPTIMIZATION
    // ============================================================

    private void runOptimization(String type) {
        if (!requireRootSession()) return;

        String label;
        switch (type) {
            case "kill": label = "Killing background processes..."; break;
            case "cache": label = "Clearing system cache..."; break;
            case "drop": label = "Dropping VM caches..."; break;
            default: label = "Optimizing...";
        }
        tvRootStatusLine.setText(label);

        executor.execute(() -> {
            String result;
            try {
                switch (type) {
                    case "kill": {
                        int n = NativeHelper.killBackgroundProcesses();
                        result = String.format(" Killed %d background processes", n);
                        break;
                    }
                    case "cache": {
                        boolean ok = NativeHelper.clearSystemCache();
                        result = ok ? " System cache cleared" : " Failed to clear cache";
                        break;
                    }
                    case "drop": {
                        String out = NativeHelper.executeRootCommand(
                                "sync && echo 3 > /proc/sys/vm/drop_caches");
                        result = (out != null) ? " VM caches dropped" : " Failed";
                        break;
                    }
                    default: result = "Unknown operation";
                }
            } catch (Throwable t) {
                result = " Error: " + t.getMessage();
            }
            final String r = result;
            handler.post(() -> {
                if (!isAdded()) return;
                tvRootStatusLine.setText(r);
            });
        });
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