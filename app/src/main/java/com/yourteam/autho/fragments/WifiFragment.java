package com.yourteam.autho.fragments;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.DhcpInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.yourteam.autho.R;
import com.yourteam.autho.utils.NativeHelper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WifiFragment extends Fragment {

    // UI
    private TextView tvCurrentSsid, tvCurrentIp, tvCurrentGateway;
    private TextView tvCurrentSignal, tvCurrentSpeed, tvCurrentFrequency;
    private TextView tvNetworksEmpty, tvDevicesEmpty, tvScanStatus;
    private LinearLayout llNetworks, llDevices;
    private Button btnScanNetworks, btnScanDevices;
    private ProgressBar progressScan;

    // Managers
    private WifiManager wifiManager;
    private BroadcastReceiver wifiScanReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final int REQUEST_LOCATION = 2001;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wifi, container, false);

        initViews(view);
        setupWifi();
        setupListeners();
        checkPermissions();
        updateCurrentConnection();

        return view;
    }

    private void initViews(View view) {
        tvCurrentSsid = view.findViewById(R.id.tvCurrentSsid);
        tvCurrentIp = view.findViewById(R.id.tvCurrentIp);
        tvCurrentGateway = view.findViewById(R.id.tvCurrentGateway);
        tvCurrentSignal = view.findViewById(R.id.tvCurrentSignal);
        tvCurrentSpeed = view.findViewById(R.id.tvCurrentSpeed);
        tvCurrentFrequency = view.findViewById(R.id.tvCurrentFrequency);

        tvNetworksEmpty = view.findViewById(R.id.tvNetworksEmpty);
        tvDevicesEmpty = view.findViewById(R.id.tvDevicesEmpty);
        tvScanStatus = view.findViewById(R.id.tvScanStatus);
        llNetworks = view.findViewById(R.id.llNetworks);
        llDevices = view.findViewById(R.id.llDevices);
        progressScan = view.findViewById(R.id.progressScan);

        btnScanNetworks = view.findViewById(R.id.btnScanNetworks);
        btnScanDevices = view.findViewById(R.id.btnScanDevices);
    }

    private void setupWifi() {
        wifiManager = (WifiManager) requireContext().getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        // Register broadcast receiver for scan results
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                    if (success) {
                        displayAvailableNetworks();
                    } else {
                        tvScanStatus.setText("Scan failed. Try again.");
                        progressScan.setVisibility(View.GONE);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        requireContext().registerReceiver(wifiScanReceiver, filter);
    }

    private void setupListeners() {
        btnScanNetworks.setOnClickListener(v -> startWifiScan());
        btnScanDevices.setOnClickListener(v -> scanNetworkDevices());
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateCurrentConnection();
            } else {
                Toast.makeText(requireContext(),
                        "Location permission required for WiFi scanning", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ============================================================
    // CURRENT CONNECTION
    // ============================================================

    private void updateCurrentConnection() {
        if (wifiManager == null) return;

        if (!wifiManager.isWifiEnabled()) {
            tvCurrentSsid.setText("WiFi Disabled");
            tvCurrentIp.setText("--");
            tvCurrentGateway.setText("--");
            tvCurrentSignal.setText("--");
            tvCurrentSpeed.setText("--");
            tvCurrentFrequency.setText("--");
            return;
        }

        WifiInfo info = wifiManager.getConnectionInfo();
        if (info == null) return;

        // SSID
        String ssid = info.getSSID();
        if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        tvCurrentSsid.setText(ssid != null ? ssid : "Not connected");

        // IP
        int ipInt = info.getIpAddress();
        String ip = String.format("%d.%d.%d.%d",
                (ipInt & 0xff),
                (ipInt >> 8 & 0xff),
                (ipInt >> 16 & 0xff),
                (ipInt >> 24 & 0xff));
        tvCurrentIp.setText(ip);

        // Gateway
        DhcpInfo dhcp = wifiManager.getDhcpInfo();
        if (dhcp != null) {
            String gw = String.format("%d.%d.%d.%d",
                    (dhcp.gateway & 0xff),
                    (dhcp.gateway >> 8 & 0xff),
                    (dhcp.gateway >> 16 & 0xff),
                    (dhcp.gateway >> 24 & 0xff));
            tvCurrentGateway.setText(gw);
        }

        // Signal
        int rssi = info.getRssi();
        int level = WifiManager.calculateSignalLevel(rssi, 5); // 0-4
        String bars = "";
        for (int i = 0; i < 5; i++) {
            bars += (i < level) ? "●" : "○";
        }
        String quality;
        if (level >= 4) quality = "Excellent";
        else if (level >= 3) quality = "Good";
        else if (level >= 2) quality = "Fair";
        else quality = "Weak";
        tvCurrentSignal.setText(bars + " " + quality + " (" + rssi + " dBm)");

        // Speed
        int speed = info.getLinkSpeed(); // Mbps
        tvCurrentSpeed.setText(speed + " Mbps");

        // Frequency
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            int freq = info.getFrequency(); // MHz
            String band = freq > 4000 ? "5 GHz" : "2.4 GHz";
            tvCurrentFrequency.setText(freq + " MHz (" + band + ")");
        } else {
            tvCurrentFrequency.setText("--");
        }
    }

    // ============================================================
    // WIFI SCAN
    // ============================================================

    private void startWifiScan() {
        if (wifiManager == null) return;

        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(requireContext(), "Please enable WiFi first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            checkPermissions();
            return;
        }

        progressScan.setVisibility(View.VISIBLE);
        tvScanStatus.setText("Scanning networks...");

        boolean started = wifiManager.startScan();
        if (!started) {
            // Throttled — wait a moment and try again, or use cached results
            tvScanStatus.setText("Scan throttled. Showing cached results...");
            displayAvailableNetworks();
        }

        // Timeout in case broadcast is never received
        handler.postDelayed(() -> {
            if (progressScan.getVisibility() == View.VISIBLE) {
                displayAvailableNetworks();
            }
        }, 4000);
    }

    private void displayAvailableNetworks() {
        if (wifiManager == null) return;

        List<ScanResult> results;
        try {
            results = wifiManager.getScanResults();
        } catch (SecurityException e) {
            tvScanStatus.setText("Permission denied for scan results");
            progressScan.setVisibility(View.GONE);
            return;
        }

        progressScan.setVisibility(View.GONE);

        if (results == null || results.isEmpty()) {
            tvNetworksEmpty.setVisibility(View.VISIBLE);
            tvNetworksEmpty.setText("No networks found");
            tvScanStatus.setText("Scan complete · 0 networks");
            return;
        }

        tvNetworksEmpty.setVisibility(View.GONE);
        // Remove previously added dynamic rows (keep the empty-state TextView)
        int childCount = llNetworks.getChildCount();
        if (childCount > 1) {
            llNetworks.removeViews(1, childCount - 1);
        }

        // Sort by signal strength (descending)
        results.sort((a, b) -> Integer.compare(b.level, a.level));

        for (ScanResult r : results) {
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_wifi_network, llNetworks, false);

            TextView tvSsid = row.findViewById(R.id.itemSsid);
            TextView tvDetails = row.findViewById(R.id.itemDetails);
            TextView tvSecurity = row.findViewById(R.id.itemSecurity);
            TextView tvSignal = row.findViewById(R.id.itemSignal);

            String ssid = TextUtils.isEmpty(r.SSID) ? "(hidden)" : r.SSID;
            tvSsid.setText(ssid);
            tvDetails.setText(r.BSSID + " · " + r.frequency + " MHz");

            String sec = r.capabilities;
            if (sec.contains("WPA3")) tvSecurity.setText("🔒 WPA3");
            else if (sec.contains("WPA2")) tvSecurity.setText("🔒 WPA2");
            else if (sec.contains("WPA")) tvSecurity.setText("🔒 WPA");
            else if (sec.contains("WEP")) tvSecurity.setText("🔒 WEP");
            else tvSecurity.setText("🔓 Open");

            int level = WifiManager.calculateSignalLevel(r.level, 5);
            String bars = "";
            for (int i = 0; i < 5; i++) bars += (i < level) ? "●" : "○";
            tvSignal.setText(bars + " " + r.level + " dBm");

            llNetworks.addView(row);
        }

        tvScanStatus.setText("Scan complete · " + results.size() + " networks");
    }

    // ============================================================
    // DEVICE SCAN (via native ping sweep)
    // ============================================================

    private void scanNetworkDevices() {
        progressScan.setVisibility(View.VISIBLE);
        tvScanStatus.setText("Scanning network for devices...");
        tvDevicesEmpty.setVisibility(View.GONE);

        // Clear old rows
        int childCount = llDevices.getChildCount();
        if (childCount > 1) {
            llDevices.removeViews(1, childCount - 1);
        }

        executor.execute(() -> {
            // Native scan: returns array of "IP|MAC|Hostname|Vendor"
            String[] devices = null;
            try {
                devices = NativeHelper.scanNetworkDevices(1500);
            } catch (Throwable t) {
                devices = null;
            }

            final String[] result = devices;
            handler.post(() -> {
                progressScan.setVisibility(View.GONE);

                if (result == null || result.length == 0) {
                    tvDevicesEmpty.setVisibility(View.VISIBLE);
                    tvDevicesEmpty.setText("No devices found on network");
                    tvScanStatus.setText("Device scan complete · 0 devices");
                    return;
                }

                for (String entry : result) {
                    // entry format: "IP|MAC|Hostname|Vendor"
                    String[] parts = entry.split("\\|");
                    if (parts.length < 4) continue;

                    View row = LayoutInflater.from(requireContext())
                            .inflate(R.layout.item_network_device, llDevices, false);

                    TextView tvIcon = row.findViewById(R.id.itemIcon);
                    TextView tvName = row.findViewById(R.id.itemName);
                    TextView tvIp = row.findViewById(R.id.itemIp);
                    TextView tvMac = row.findViewById(R.id.itemMac);
                    Button btnPing = row.findViewById(R.id.itemPing);

                    tvIcon.setText("💻");
                    tvName.setText(parts[2]);
                    tvIp.setText(parts[0]);
                    tvMac.setText(parts[1]);

                    String ip = parts[0];
                    btnPing.setOnClickListener(v -> pingDevice(ip));

                    llDevices.addView(row);
                }

                tvScanStatus.setText("Device scan complete · " + result.length + " devices");
            });
        });
    }

    private void pingDevice(String ip) {
        tvScanStatus.setText("Pinging " + ip + "...");
        executor.execute(() -> {
            boolean reachable = false;
            try {
                reachable = NativeHelper.pingDevice(ip, 2000);
            } catch (Throwable ignored) {}

            final boolean ok = reachable;
            handler.post(() -> {
                tvScanStatus.setText(ip + (ok ? " is reachable ✅" : " unreachable ❌"));
                Toast.makeText(requireContext(),
                        ip + (ok ? " is reachable" : " unreachable"),
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================

    @Override
    public void onResume() {
        super.onResume();
        updateCurrentConnection();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            requireContext().unregisterReceiver(wifiScanReceiver);
        } catch (IllegalArgumentException ignored) {}
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}