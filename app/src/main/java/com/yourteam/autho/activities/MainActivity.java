package com.yourteam.autho.activities;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.yourteam.autho.R;
import com.yourteam.autho.fragments.DashboardFragment;
import com.yourteam.autho.fragments.DiagnosticsFragment;
import com.yourteam.autho.fragments.RootFragment;
import com.yourteam.autho.fragments.SecurityFragment;
import com.yourteam.autho.fragments.WifiFragment;

public class MainActivity extends AppCompatActivity
        implements NavigationBarView.OnItemSelectedListener {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get user data from LoginActivity (optional)
        String username = getIntent().getStringExtra("username");
        int userId = getIntent().getIntExtra("user_id", -1);
        // You can pass username to fragments via arguments if needed

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Use the modern listener setter
        bottomNavigationView.setOnItemSelectedListener(this);

        // Load the Dashboard by default
        if (savedInstanceState == null) {
            loadFragment(new DashboardFragment());
        }
    }

    // ============================================================
    // BOTTOM NAVIGATION
    // ============================================================

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            loadFragment(new DashboardFragment());
            return true;
        } else if (id == R.id.nav_diagnostics) {
            loadFragment(new DiagnosticsFragment());
            return true;
        } else if (id == R.id.nav_wifi) {
            loadFragment(new WifiFragment());
            return true;
        } else if (id == R.id.nav_root) {
            loadFragment(new RootFragment());
            return true;
        } else if (id == R.id.nav_security) {
            loadFragment(new SecurityFragment());
            return true;
        }
        return false;
    }

    // ============================================================
    // PUBLIC NAVIGATION HELPERS (for DashboardFragment Quick Access)
    // ============================================================

    public void navigateToDashboard() {
        loadFragment(new DashboardFragment());
        if (bottomNavigationView != null)
            bottomNavigationView.setSelectedItemId(R.id.nav_dashboard);
    }

    public void navigateToDiagnostics() {
        loadFragment(new DiagnosticsFragment());
        if (bottomNavigationView != null)
            bottomNavigationView.setSelectedItemId(R.id.nav_diagnostics);
    }

    public void navigateToWifi() {
        loadFragment(new WifiFragment());
        if (bottomNavigationView != null)
            bottomNavigationView.setSelectedItemId(R.id.nav_wifi);
    }

    public void navigateToRoot() {
        loadFragment(new RootFragment());
        if (bottomNavigationView != null)
            bottomNavigationView.setSelectedItemId(R.id.nav_root);
    }

    public void navigateToSecurity() {
        loadFragment(new SecurityFragment());
        if (bottomNavigationView != null)
            bottomNavigationView.setSelectedItemId(R.id.nav_security);
    }

    // ============================================================
    // FRAGMENT LOADER
    // ============================================================

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit();
    }
}