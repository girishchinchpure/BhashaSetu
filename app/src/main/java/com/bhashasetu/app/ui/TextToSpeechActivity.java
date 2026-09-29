package com.bhashasetu.app.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bhashasetu.app.R;
import com.bhashasetu.app.engine.CorrectionLogger;
import com.bhashasetu.app.engine.TTSEngine;
import com.bhashasetu.app.engine.TranslationPipeline;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TextToSpeechActivity extends AppCompatActivity {

    private static final String TARGET_LANGUAGE = "Santhali";

    private static final String[] QUICK_PHRASES = {
            "ध्यान दो",
            "शाबाश",
            "बैठो",
            "खड़े हो",
            "पढ़ो",
            "लिखो",
            "देखो",
            "सुनो",
            "बताओ"
    };

    private TTSEngine ttsEngine;
    private TranslationPipeline pipeline;
    private CorrectionLogger correctionLogger;

    private EditText hindiInput;
    private TextView hoOutputText;
    private Button translateButton;

    private String lastHindi = "";
    private String lastHo = "";
    private String lastModel = "";

    private boolean slowMode = false;
    private boolean destroyed = false;

    /*
     * Only used for initialization work.
     * The Android UI thread is never used for model/database setup.
     */
    private final ExecutorService initExecutor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Nothing expensive happens before/around setContentView().
         */
        android.util.Log.d("BS_LAG", "BEFORE_LAYOUT");
        setContentView(R.layout.activity_text_to_speech);
        android.util.Log.d("BS_LAG", "AFTER_LAYOUT");

        hindiInput = findViewById(R.id.hindiInput);
        hoOutputText = findViewById(R.id.hoOutputText);
        translateButton = findViewById(R.id.btnTranslateSpeak);

        /*
         * ---------------------------------------------------------
         * LANGUAGE CHECK
         * ---------------------------------------------------------
         *
         * The current translation model supports Santhali only.
         * HomeActivity already prevents unsupported navigation,
         * but this check also prevents direct/bypassed launches.
         */

        SharedPreferences prefs =
                getSharedPreferences(
                        HomeActivity.PREFS,
                        MODE_PRIVATE
                );

        String selectedLanguage =
                prefs.getString(
                        HomeActivity.KEY_LANGUAGE,
                        TARGET_LANGUAGE
                );

        if (!TARGET_LANGUAGE.equalsIgnoreCase(
                selectedLanguage.trim())) {

            Toast.makeText(
                    this,
                    "Text Translate currently supports Santhali only.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        /*
         * Quick phrases.
         */
        LinearLayout quickPhraseContainer =
                findViewById(R.id.quickPhraseContainer);

        for (String phrase : QUICK_PHRASES) {

            Button chip = new Button(this);

            chip.setText(phrase);
            chip.setTextSize(12);

            chip.setOnClickListener(v ->
                    hindiInput.setText(phrase));

            quickPhraseContainer.addView(chip);
        }

        /*
         * Translation button.
         */
        translateButton.setOnClickListener(v -> translate());

        /*
         * Play button.
         */
        findViewById(R.id.btnPlay)
                .setOnClickListener(v -> {

                    if (lastHo.isEmpty()) {

                        Toast.makeText(
                                this,
                                "Translate something first",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    ensureTTS();
                    ttsEngine.speak(lastHo);
                });

        /*
         * Loop button.
         */
        findViewById(R.id.btnLoop)
                .setOnClickListener(v -> {

                    if (lastHo.isEmpty()) {

                        Toast.makeText(
                                this,
                                "Translate something first",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    ensureTTS();
                    ttsEngine.speak(lastHo);
                });

        /*
         * Slow mode.
         */
        findViewById(R.id.btnSlow)
                .setOnClickListener(v -> {

                    slowMode = !slowMode;

                    ensureTTS();
                    ttsEngine.setSlowMode(slowMode);

                    Toast.makeText(
                            this,
                            slowMode
                                    ? "Slow mode ON"
                                    : "Slow mode OFF",
                            Toast.LENGTH_SHORT
                    ).show();
                });

        /*
         * Correct button.
         */
        findViewById(R.id.btnCorrect)
                .setOnClickListener(v -> {

                    if (lastHo.isEmpty()) {

                        Toast.makeText(
                                this,
                                "Translate something first",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    if (correctionLogger == null) {
                        correctionLogger =
                                new CorrectionLogger(
                                        getApplicationContext()
                                );
                    }

                    correctionLogger.log(
                            lastHindi,
                            lastHo,
                            TARGET_LANGUAGE,
                            lastModel,
                            true
                    );

                    Toast.makeText(
                            this,
                            "Marked correct",
                            Toast.LENGTH_SHORT
                    ).show();
                });

        /*
         * Incorrect button.
         */
        findViewById(R.id.btnIncorrect)
                .setOnClickListener(v -> {

                    if (lastHo.isEmpty()) {

                        Toast.makeText(
                                this,
                                "Translate something first",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    if (correctionLogger == null) {
                        correctionLogger =
                                new CorrectionLogger(
                                        getApplicationContext()
                                );
                    }

                    correctionLogger.log(
                            lastHindi,
                            lastHo,
                            TARGET_LANGUAGE,
                            lastModel,
                            false
                    );

                    Toast.makeText(
                            this,
                            "Logged for review",
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void ensureTTS() {

        if (ttsEngine != null) {
            return;
        }

        ttsEngine = new TTSEngine();

        ttsEngine.initialize(
                getApplicationContext(),
                success -> {
                    // Nothing required here.
                }
        );

        ttsEngine.setSlowMode(slowMode);
    }

    private void translate() {

        /*
         * Safety check in case the activity somehow remains alive
         * after the selected language changes.
         */
        SharedPreferences prefs =
                getSharedPreferences(
                        HomeActivity.PREFS,
                        MODE_PRIVATE
                );

        String selectedLanguage =
                prefs.getString(
                        HomeActivity.KEY_LANGUAGE,
                        TARGET_LANGUAGE
                );

        if (!TARGET_LANGUAGE.equalsIgnoreCase(
                selectedLanguage.trim())) {

            Toast.makeText(
                    this,
                    "Text Translate currently supports Santhali only.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String text =
                hindiInput.getText()
                        .toString()
                        .trim();

        if (text.isEmpty()) {

            Toast.makeText(
                    this,
                    "Type something in Hindi first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        /*
         * Prevent double taps while translation is being prepared.
         */
        translateButton.setEnabled(false);
        translateButton.setText("Translating...");

        hoOutputText.setText("Preparing translation...");

        /*
         * Pipeline creation happens OFF the UI thread.
         */
        initExecutor.execute(() -> {

            try {

                if (pipeline == null) {

                    pipeline =
                            new TranslationPipeline(
                                    getApplicationContext()
                            );
                }

                pipeline.translate(
                        text,
                        TARGET_LANGUAGE,
                        (
                                translatedText,
                                confidence,
                                source,
                                latencyMs
                        ) -> {

                            runOnUiThread(() -> {

                                if (destroyed) {
                                    return;
                                }

                                translateButton.setEnabled(true);
                                translateButton.setText(
                                        "Translate & Speak"
                                );

                                lastHindi = text;
                                lastHo = translatedText;
                                lastModel = source;

                                hoOutputText.setText(
                                        translatedText
                                );

                                /*
                                 * Initialize TTS only after
                                 * translation is complete.
                                 */
                                ensureTTS();

                                ttsEngine.speak(
                                        translatedText
                                );

                                Toast.makeText(
                                        this,
                                        "Translation completed in "
                                                + latencyMs
                                                + " ms",
                                        Toast.LENGTH_SHORT
                                ).show();
                            });
                        }
                );

            } catch (Exception e) {

                runOnUiThread(() -> {

                    if (destroyed) {
                        return;
                    }

                    translateButton.setEnabled(true);
                    translateButton.setText(
                            "Translate & Speak"
                    );

                    hoOutputText.setText(
                            "[ERROR: "
                                    + e.getMessage()
                                    + "]"
                    );
                });
            }
        });
    }

    @Override
    protected void onDestroy() {

        destroyed = true;

        /*
         * Stop initialization work.
         */
        initExecutor.shutdownNow();

        /*
         * Release TTS.
         */
        if (ttsEngine != null) {
            ttsEngine.release();
            ttsEngine = null;
        }

        /*
         * Release translation model.
         */
        if (pipeline != null) {
            pipeline.close();
            pipeline = null;
        }

        super.onDestroy();
    }
}