package com.yourteam.autho.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yourteam.autho.R;

public class DisplayTestActivity extends Activity {

    public static final String EXTRA_RESULT = "display_test_result";

    private FrameLayout colorArea;
    private LinearLayout promptLayout;
    private TextView tvInstruction;
    private Button btnYes, btnNo;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final int[] COLORS = {
            Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK
    };
    private final String[] COLOR_NAMES = {
            "RED", "GREEN", "BLUE", "WHITE", "BLACK"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_test);

        // Full-screen immersive
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        colorArea     = findViewById(R.id.colorArea);
        promptLayout  = findViewById(R.id.promptLayout);
        tvInstruction = findViewById(R.id.tvInstruction);
        btnYes        = findViewById(R.id.btnYes);
        btnNo         = findViewById(R.id.btnNo);

        promptLayout.setVisibility(View.GONE);

        btnYes.setOnClickListener(v -> finishWith(true));
        btnNo.setOnClickListener(v -> finishWith(false));

        startColorCycle();
    }

    private void startColorCycle() {
        for (int i = 0; i < COLORS.length; i++) {
            final int index = i;
            long delay = 1500L * i;
            handler.postDelayed(() -> {
                colorArea.setBackgroundColor(COLORS[index]);
                tvInstruction.setText(COLOR_NAMES[index]);
                tvInstruction.setTextColor(
                        index == 3 || index == 4 ? Color.BLACK : Color.WHITE);
            }, delay);
        }

        // After all colors shown, show prompt
        handler.postDelayed(() -> {
            promptLayout.setVisibility(View.VISIBLE);
            colorArea.setBackgroundColor(Color.parseColor("#0A0E1A"));
            tvInstruction.setText("Did all colors display clearly?");
            tvInstruction.setTextColor(Color.WHITE);
        }, 1500L * COLORS.length + 200);
    }

    private void finishWith(boolean passed) {
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT, passed);
        setResult(passed ? RESULT_OK : RESULT_CANCELED, result);
        finish();
    }

    private void hideSystemUI() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onBackPressed() {
        finishWith(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}