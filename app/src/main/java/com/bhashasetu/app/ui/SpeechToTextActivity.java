package com.bhashasetu.app.ui;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bhashasetu.app.R;
import com.bhashasetu.app.database.AppDatabase;
import com.bhashasetu.app.database.TranslationCacheEntity;
import com.bhashasetu.app.engine.ASREngine;
import com.bhashasetu.app.engine.CorrectionLogger;
import com.bhashasetu.app.engine.TTSEngine;
import com.bhashasetu.app.engine.TranslationPipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import android.util.Log;import android.util.Log;
public class SpeechToTextActivity extends AppCompatActivity {

    private static final int REQ_MIC = 101;

    private ASREngine asrEngine;
    private TTSEngine ttsEngine;
    private TranslationPipeline pipeline;
    private CorrectionLogger correctionLogger;

    private TextView hindiInputText, hoOutputText, metaText;
    private ArrayAdapter<String> recentAdapter;
    private String targetLang;
    private String lastHindi = "";
    private String lastHo = "";
    private String lastModel = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("STT_TEST", "SpeechToTextActivity onCreate reached");
        setContentView(R.layout.activity_speech_to_text);

        SharedPreferences prefs = getSharedPreferences(HomeActivity.PREFS, MODE_PRIVATE);
        targetLang = prefs.getString(HomeActivity.KEY_LANGUAGE, "Ho");

        asrEngine = new ASREngine();
        ttsEngine = new TTSEngine();
        ttsEngine.initialize(this, success -> {});
        Log.e("STT_TEST", "SpeechToTextActivity onCreate reached");
        pipeline = new TranslationPipeline(this);
        pipeline = new TranslationPipeline(this);
        correctionLogger = new CorrectionLogger(this);

        hindiInputText = findViewById(R.id.hindiInputText);
        hoOutputText = findViewById(R.id.hoOutputText);
        metaText = findViewById(R.id.metaText);

        ListView recentList = findViewById(R.id.recentList);
        recentAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        recentList.setAdapter(recentAdapter);
        loadRecent();

        findViewById(R.id.btnMic).setOnClickListener(v -> requestMicAndListen());
        findViewById(R.id.btnPlayAudio).setOnClickListener(v -> ttsEngine.speak(lastHo));
        findViewById(R.id.btnCorrect).setOnClickListener(v -> {
            correctionLogger.log(lastHindi, lastHo, targetLang, lastModel, true);
            Toast.makeText(this, "Marked correct — thank you", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnIncorrect).setOnClickListener(v -> {
            correctionLogger.log(lastHindi, lastHo, targetLang, lastModel, false);
            Toast.makeText(this, "Logged for review", Toast.LENGTH_SHORT).show();
        });
    }

    private void requestMicAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        listen();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            listen();
        }
    }

    private void listen() {
        hindiInputText.setText("Listening...");
        asrEngine.startListening(this, new ASREngine.ResultCallback() {
            @Override
            public void onResult(String hindiText) {
                hindiInputText.setText(hindiText);
                runTranslation(hindiText);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SpeechToTextActivity.this, "ASR: " + message, Toast.LENGTH_SHORT).show();
                hindiInputText.setText("—");
            }
        });
    }

    private void runTranslation(String hindiText) {
        pipeline.translate(hindiText, targetLang, (hoText, confidence, source, latencyMs) -> runOnUiThread(() -> {
            lastHindi = hindiText;
            lastHo = hoText;
            lastModel = source;

            hoOutputText.setText(hoText);
            metaText.setText(String.format(Locale.US,
                    "⏱️ Latency: %dms  |  📊 Confidence: %.0f%%  |  💾 %s",
                    latencyMs, confidence * 100, source.contains("cache") ? "Cache HIT" : "Cache MISS"));

            ttsEngine.speak(hoText);
            loadRecent();
        }));
    }

    private void loadRecent() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<TranslationCacheEntity> recent = AppDatabase.getInstance(this)
                    .translationCacheDao().recent(10);
            List<String> lines = new ArrayList<>();
            for (TranslationCacheEntity e : recent) {
                lines.add(e.sourceText + " → " + e.translatedText);
            }
            runOnUiThread(() -> {
                recentAdapter.clear();
                recentAdapter.addAll(lines);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        asrEngine.release();
        ttsEngine.release();
    }
}
