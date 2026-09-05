package com.yourteam.autho.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.yourteam.autho.R;
import com.yourteam.autho.fragments.DashboardFragment;

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

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            loadFragment(new DashboardFragment());
            return true;
        } else if (id == R.id.nav_diagnostics) {
            // Replace with DiagnosticsFragment when ready (Phase 3)
            Toast.makeText(this, "Diagnostics coming soon", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.nav_wifi) {
            Toast.makeText(this, "WiFi Scanner coming soon", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.nav_root) {
            Toast.makeText(this, "Root Tools coming soon", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.nav_settings) {
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host_fragment, fragment)
                .commit();
    }
}