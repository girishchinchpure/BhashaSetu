package com.bhashasetu.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bhashasetu.app.R;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS = "bhashasetu_prefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView statusText = findViewById(R.id.statusText);

        // In the full spec, this checks assets/models/*.tflite for the real
        // IndicTrans2 / Whisper / FastSpeech2 model files. This prototype has
        // no model binaries to check (see README), so it always resolves to
        // "dictionary mode ready" and moves on.
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean("models_downloaded", false).apply();

        statusText.setText("Loading dictionary + offline cache...");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, HomeActivity.class));
            finish();
        }, 1500);
    }
}
