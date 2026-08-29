package com.yourteam.autho;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.yourteam.autho.R;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Get user info from intent
        String username = getIntent().getStringExtra("username");
        int userId = getIntent().getIntExtra("user_id", -1);
        TextView tvWelcome = findViewById(R.id.tvWelcome);
        if (username != null) {
            tvWelcome.setText("Welcome, " + username + "!");
            Toast.makeText(this, "Welcome " + username, Toast.LENGTH_SHORT).show();
        }
    }
}