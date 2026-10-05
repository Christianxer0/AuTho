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

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WifiFragment extends Fragment {

    // UI — Current connection
    private TextView tvCurrentSsid, tvCurrentIp, tvCurrentGateway;
    private TextView tvCurrentSignal, tvCurrentSpeed, tvCurrentFrequency;

    // UI — Networks
    private TextView tvNetworksEmpty, tvDevicesEmpty, tvScanStatus;
    private LinearLayout llNetworks, llDevices;
    private Button btnScanNetworks, btnScanDevices;
    private ProgressBar progressScan;

    // System
    private WifiManager wifiManager;
    private BroadcastReceiver wifiScanReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Dedicated pool for device scan (parallel TCP probes)
    private final ExecutorService scanExecutor = Executors.newFixedThreadPool(20);

    private static final int REQUEST_LOCATION = 2001;

    // Common ports to probe — if any responds, device is alive
    private static final int[] SCAN_PORTS = {80, 443, 22, 445, 8080, 62078};

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
        tvCurrentSsid       = view.findViewById(R.id.tvCurrentSsid);
        tvCurrentIp         = view.findViewById(R.id.tvCurrentIp);
        tvCurrentGateway    = view.findViewById(R.id.tvCurrentGateway);
        tvCurrentSignal     = view.findViewById(R.id.tvCurrentSignal);
        tvCurrentSpeed      = view.findViewById(R.id.tvCurrentSpeed);
        tvCurrentFrequency  = view.findViewById(R.id.tvCurrentFrequency);

        tvNetworksEmpty = view.findViewById(R.id.tvNetworksEmpty);
        tvDevicesEmpty  = view.findViewById(R.id.tvDevicesEmpty);
        tvScanStatus    = view.findViewById(R.id.tvScanStatus);
        llNetworks      = view.findViewById(R.id.llNetworks);
        llDevices       = view.findViewById(R.id.llDevices);
        progressScan    = view.findViewById(R.id.progressScan);

        btnScanNetworks = view.findViewById(R.id.btnScanNetworks);
        btnScanDevices  = view.findViewById(R.id.btnScanDevices);
    }

    private void setupWifi() {
        wifiManager = (WifiManager) requireContext().getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    boolean success = intent.getBooleanExtra(
                            WifiManager.EXTRA_RESULTS_UPDATED, false);
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
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateCurrentConnection();
            } else {
                Toast.makeText(requireContext(),
                        "Location permission required for WiFi scanning",
                        Toast.LENGTH_LONG).show();
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

        String ssid = info.getSSID();
        if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        tvCurrentSsid.setText(ssid != null ? ssid : "Not connected");

        int ipInt = info.getIpAddress();
        String ip = String.format(Locale.US, "%d.%d.%d.%d",
                (ipInt & 0xff), (ipInt >> 8 & 0xff),
                (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
        tvCurrentIp.setText(ip);

        DhcpInfo dhcp = wifiManager.getDhcpInfo();
        if (dhcp != null) {
            String gw = String.format(Locale.US, "%d.%d.%d.%d",
                    (dhcp.gateway & 0xff), (dhcp.gateway >> 8 & 0xff),
                    (dhcp.gateway >> 16 & 0xff), (dhcp.gateway >> 24 & 0xff));
            tvCurrentGateway.setText(gw);
        }

        int rssi = info.getRssi();
        int level = WifiManager.calculateSignalLevel(rssi, 5);
        StringBuilder bars = new StringBuilder();
        for (int i = 0; i < 5; i++) bars.append(i < level ? "●" : "○");
        String quality;
        if (level >= 4) quality = "Excellent";
        else if (level >= 3) quality = "Good";
        else if (level >= 2) quality = "Fair";
        else quality = "Weak";
        tvCurrentSignal.setText(bars + " " + quality + " (" + rssi + " dBm)");

        tvCurrentSpeed.setText(info.getLinkSpeed() + " Mbps");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            int freq = info.getFrequency();
            String band = freq > 4000 ? "5 GHz" : "2.4 GHz";
            tvCurrentFrequency.setText(freq + " MHz (" + band + ")");
        } else {
            tvCurrentFrequency.setText("--");
        }
    }

    // ============================================================
    // WIFI NETWORK SCAN
    // ============================================================

    private void startWifiScan() {
        if (wifiManager == null) return;

        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(requireContext(), "Please enable WiFi first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            checkPermissions();
            return;
        }

        progressScan.setVisibility(View.VISIBLE);
        tvScanStatus.setText("Scanning networks...");

        boolean started = wifiManager.startScan();
        if (!started) {
            tvScanStatus.setText("Scan throttled. Showing cached results...");
            displayAvailableNetworks();
        }

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
        int childCount = llNetworks.getChildCount();
        if (childCount > 1) llNetworks.removeViews(1, childCount - 1);

        results.sort((a, b) -> Integer.compare(b.level, a.level));

        for (ScanResult r : results) {
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_wifi_network, llNetworks, false);

            TextView tvSsid     = row.findViewById(R.id.itemSsid);
            TextView tvDetails  = row.findViewById(R.id.itemDetails);
            TextView tvSecurity = row.findViewById(R.id.itemSecurity);
            TextView tvSignal   = row.findViewById(R.id.itemSignal);

            tvSsid.setText(TextUtils.isEmpty(r.SSID) ? "(hidden)" : r.SSID);
            tvDetails.setText(r.BSSID + " · " + r.frequency + " MHz");

            String sec = r.capabilities;
            if (sec.contains("WPA3"))      tvSecurity.setText("🔒 WPA3");
            else if (sec.contains("WPA2")) tvSecurity.setText("🔒 WPA2");
            else if (sec.contains("WPA"))  tvSecurity.setText("🔒 WPA");
            else if (sec.contains("WEP"))  tvSecurity.setText("🔒 WEP");
            else                           tvSecurity.setText("🔓 Open");

            int level = WifiManager.calculateSignalLevel(r.level, 5);
            StringBuilder bars = new StringBuilder();
            for (int i = 0; i < 5; i++) bars.append(i < level ? "●" : "○");
            tvSignal.setText(bars + " " + r.level + " dBm");

            llNetworks.addView(row);
        }

        tvScanStatus.setText("Scan complete · " + results.size() + " networks");
    }

    // ============================================================
    // DEVICE SCAN — TCP socket sweep + ARP cache
    // ============================================================

    private void scanNetworkDevices() {
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            Toast.makeText(requireContext(), "Please enable WiFi first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        progressScan.setVisibility(View.VISIBLE);
        tvScanStatus.setText("Scanning network for devices...");
        tvDevicesEmpty.setVisibility(View.GONE);

        int childCount = llDevices.getChildCount();
        if (childCount > 1) llDevices.removeViews(1, childCount - 1);

        new Thread(this::performDeviceScan).start();
    }

    private void performDeviceScan() {
        WifiInfo info = wifiManager.getConnectionInfo();
        if (info == null) {
            postDeviceResult(Collections.emptyList(), "WiFi info unavailable");
            return;
        }

        int ipInt = info.getIpAddress();
        if (ipInt == 0) {
            postDeviceResult(Collections.emptyList(), "Not connected to a network");
            return;
        }

        String myIp = String.format(Locale.US, "%d.%d.%d.%d",
                (ipInt & 0xff), (ipInt >> 8 & 0xff),
                (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));

        String prefix = myIp.substring(0, myIp.lastIndexOf('.') + 1);

        final ConcurrentHashMap<String, Boolean> foundDevices = new ConcurrentHashMap<>();
        final CountDownLatch latch = new CountDownLatch(254);

        for (int i = 1; i <= 254; i++) {
            final String targetIp = prefix + i;
            scanExecutor.execute(() -> {
                try {
                    if (probeHost(targetIp)) {
                        foundDevices.put(targetIp, Boolean.TRUE);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) { }

        // Enrich with ARP cache for MAC addresses
        ConcurrentHashMap<String, String> arpTable = readArpTable();

        List<DeviceEntry> devices = new ArrayList<>();
        for (String ip : foundDevices.keySet()) {
            String mac = arpTable.getOrDefault(ip, "--");
            devices.add(new DeviceEntry(ip, mac));
        }

        devices.sort((a, b) -> {
            String[] pa = a.ip.split("\\.");
            String[] pb = b.ip.split("\\.");
            for (int i = 0; i < 4; i++) {
                int cmp = Integer.compare(Integer.parseInt(pa[i]),
                        Integer.parseInt(pb[i]));
                if (cmp != 0) return cmp;
            }
            return 0;
        });

        postDeviceResult(devices,
                "Device scan complete · " + devices.size() + " found");
    }

    /**
     * Try to reach a host via TCP on common ports.
     * Succeeds if ANY port accepts a connection.
     */
    private boolean probeHost(String ip) {
        for (int port : SCAN_PORTS) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), 300);
                return true;
            } catch (Exception ignored) {
                // try next port
            }
        }
        return false;
    }

    /**
     * Parse /proc/net/arp for IP → MAC mapping.
     * Readable on all Android versions without root.
     */
    private ConcurrentHashMap<String, String> readArpTable() {
        ConcurrentHashMap<String, String> table = new ConcurrentHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line;
            br.readLine();  // skip header
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 4) {
                    String ip  = parts[0];
                    String mac = parts[3];
                    if (!"00:00:00:00:00:00".equals(mac)) {
                        table.put(ip, mac);
                    }
                }
            }
        } catch (Exception ignored) { }
        return table;
    }

    private void postDeviceResult(List<DeviceEntry> devices, String status) {
        handler.post(() -> {
            if (!isAdded() || getContext() == null) return;

            progressScan.setVisibility(View.GONE);
            tvScanStatus.setText(status);

            if (devices.isEmpty()) {
                tvDevicesEmpty.setVisibility(View.VISIBLE);
                tvDevicesEmpty.setText("No devices found on network\n" +
                        "Make sure you're on WiFi and try again");
                return;
            }

            for (DeviceEntry d : devices) {
                View row = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_network_device, llDevices, false);

                TextView tvIcon = row.findViewById(R.id.itemIcon);
                TextView tvName = row.findViewById(R.id.itemName);
                TextView tvIp   = row.findViewById(R.id.itemIp);
                TextView tvMac  = row.findViewById(R.id.itemMac);
                Button btnPing  = row.findViewById(R.id.itemPing);

                tvIcon.setText(pickIconForDevice(d.ip));
                tvName.setText("Device " + d.ip.substring(d.ip.lastIndexOf('.') + 1));
                tvIp.setText(d.ip);
                tvMac.setText(d.mac);

                String ip = d.ip;
                btnPing.setOnClickListener(v -> pingDevice(ip));

                llDevices.addView(row);
            }
        });
    }

    private String pickIconForDevice(String ip) {
        int lastOctet;
        try {
            lastOctet = Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1));
        } catch (Exception e) {
            return "💻";
        }
        if (lastOctet == 1) return "🌐";        // usually the router
        if (lastOctet < 10) return "🖥️";
        if (lastOctet < 100) return "📱";
        return "💻";
    }

    // ============================================================
    // PING DEVICE
    // ============================================================

    private void pingDevice(String ip) {
        tvScanStatus.setText("Pinging " + ip + "...");
        new Thread(() -> {
            boolean reachable = false;
            long start = System.currentTimeMillis();

            for (int port : SCAN_PORTS) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(ip, port), 1000);
                    reachable = true;
                    break;
                } catch (Exception ignored) { }
            }

            long latency = System.currentTimeMillis() - start;
            final boolean ok = reachable;

            handler.post(() -> {
                if (!isAdded()) return;
                if (ok) {
                    tvScanStatus.setText("✅ " + ip + " reachable (" + latency + " ms)");
                    Toast.makeText(requireContext(),
                            ip + " is reachable (" + latency + " ms)",
                            Toast.LENGTH_SHORT).show();
                } else {
                    tvScanStatus.setText("❌ " + ip + " unreachable");
                    Toast.makeText(requireContext(),
                            ip + " did not respond",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
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
        } catch (IllegalArgumentException ignored) { }
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        scanExecutor.shutdownNow();
    }

    // ============================================================
    // HELPER CLASS
    // ============================================================

    private static class DeviceEntry {
        final String ip;
        final String mac;
        DeviceEntry(String ip, String mac) {
            this.ip = ip;
            this.mac = mac;
        }
    }
}