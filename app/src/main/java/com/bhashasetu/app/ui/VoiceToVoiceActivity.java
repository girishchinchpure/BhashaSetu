package com.bhashasetu.app.ui;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bhashasetu.app.R;
import com.bhashasetu.app.engine.ASREngine;
import com.bhashasetu.app.engine.IndicXlitEngine;
import com.bhashasetu.app.engine.TTSEngine;
import com.bhashasetu.app.engine.TranslationPipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceToVoiceActivity extends AppCompatActivity {

    private static final int REQ_MIC = 102;

    /*
     * Current prototype translation language.
     */
    private static final String TARGET_LANGUAGE = "Santhali";

    private ASREngine asrEngine;
    private TTSEngine ttsEngine;
    private TranslationPipeline pipeline;
    private IndicXlitEngine transliterator;

    private String targetLang;

    private TextView hindiText;
    private TextView hoText;
    private TextView pipelineStatus;

    private ListView conversationLog;
    private ArrayAdapter<String> logAdapter;

    /*
     * Stores the translated text corresponding to each
     * conversation-log position.
     *
     * IMPORTANT:
     * Both lists are updated at position 0 together.
     * Therefore:
     *
     * conversation log position 0
     *            ↓
     * translatedMessages position 0
     *
     * This lets us replay the exact message that
     * the user taps.
     */
    private final List<String> translatedMessages =
            new ArrayList<>();

    private final List<Long> latencies =
            new ArrayList<>();

    private final ExecutorService backgroundExecutor =
            Executors.newSingleThreadExecutor();

    private boolean transliteratorReady = false;
    private boolean listening = false;
    private boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_voice_to_voice);

        // -------------------------------------------------
        // Load selected language
        // -------------------------------------------------

        SharedPreferences prefs =
                getSharedPreferences(
                        HomeActivity.PREFS,
                        MODE_PRIVATE
                );

        /*
         * Current translation model supports
         * Hindi -> Santhali only.
         */
        targetLang =
                prefs.getString(
                        HomeActivity.KEY_LANGUAGE,
                        TARGET_LANGUAGE
                );

        /*
         * HARD LANGUAGE GATE
         */
        if (!TARGET_LANGUAGE.equalsIgnoreCase(
                targetLang.trim()
        )) {

            Toast.makeText(
                    this,
                    "Live Classroom currently supports Santhali only.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        targetLang = TARGET_LANGUAGE;

        // -------------------------------------------------
        // Find views
        // -------------------------------------------------

        hindiText =
                findViewById(R.id.hindiText);

        hoText =
                findViewById(R.id.hoText);

        pipelineStatus =
                findViewById(R.id.pipelineStatus);

        conversationLog =
                findViewById(R.id.conversationLog);

        // -------------------------------------------------
        // Conversation log
        // -------------------------------------------------

        logAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        new ArrayList<>()
                );

        conversationLog.setAdapter(logAdapter);

        /*
         * NEW FEATURE:
         *
         * Every conversation-log item is individually clickable.
         *
         * Example:
         *
         * 0 -> "नमस्ते" → "Johar"
         * 1 -> "बैठ जाओ" → "..."
         * 2 -> "किताब खोलो" → "..."
         *
         * Tapping item 1 plays only item 1's translation.
         */
        conversationLog.setOnItemClickListener(
                (parent, view, position, id) -> {

                    if (destroyed) {
                        return;
                    }

                    /*
                     * Protect against an invalid list position.
                     */
                    if (position < 0 ||
                            position >= translatedMessages.size()) {

                        return;
                    }

                    String selectedTranslation =
                            translatedMessages.get(position);

                    if (selectedTranslation == null ||
                            selectedTranslation.trim().isEmpty()) {

                        return;
                    }

                    /*
                     * Stop any current TTS first.
                     *
                     * This makes tapping another message
                     * immediately switch playback to that
                     * specific message.
                     */
                    if (ttsEngine != null) {
                        ttsEngine.stop();
                    }

                    /*
                     * Speak exactly the message that was tapped.
                     *
                     * TTSEngine uses QUEUE_FLUSH, so this
                     * message plays once and does not get
                     * added behind an older message.
                     */
                    if (ttsEngine != null &&
                            ttsEngine.isReady()) {

                        ttsEngine.speak(
                                selectedTranslation
                        );

                        Toast.makeText(
                                VoiceToVoiceActivity.this,
                                "Playing translation",
                                Toast.LENGTH_SHORT
                        ).show();

                    } else {

                        Toast.makeText(
                                VoiceToVoiceActivity.this,
                                "Santhali voice output is not ready.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );

        // -------------------------------------------------
        // ASR
        // -------------------------------------------------

        asrEngine =
                new ASREngine();

        // -------------------------------------------------
        // TTS
        // -------------------------------------------------

        ttsEngine =
                new TTSEngine();

        ttsEngine.initialize(
                getApplicationContext(),
                success -> {

                    if (destroyed) {
                        return;
                    }

                    runOnUiThread(() -> {

                        if (destroyed) {
                            return;
                        }

                        if (success) {

                            /*
                             * Do not overwrite a more useful
                             * transliterator status here.
                             *
                             * TTS is simply prepared in the
                             * background.
                             */

                        } else {

                            /*
                             * TTS failure is not allowed to
                             * crash the activity.
                             */
                            Toast.makeText(
                                    VoiceToVoiceActivity.this,
                                    "Santhali voice output is unavailable.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });
                }
        );

        // -------------------------------------------------
        // Translation pipeline
        // -------------------------------------------------

        pipeline =
                new TranslationPipeline(
                        getApplicationContext()
                );

        // -------------------------------------------------
        // IndicXlit
        // -------------------------------------------------

        transliterator =
                new IndicXlitEngine(
                        getApplicationContext()
                );

        pipelineStatus.setText(
                "Preparing offline transliterator..."
        );

        backgroundExecutor.execute(() -> {

            try {

                transliterator.initialize();

                if (destroyed) {
                    return;
                }

                runOnUiThread(() -> {

                    if (destroyed) {
                        return;
                    }

                    transliteratorReady = true;

                    pipelineStatus.setText(
                            "🎤 Ready for Hindi speech"
                    );
                });

            } catch (Exception e) {

                if (destroyed) {
                    return;
                }

                runOnUiThread(() ->
                        pipelineStatus.setText(
                                "Transliterator error"
                        )
                );
            }
        });

        // -------------------------------------------------
        // HOLD TO SPEAK
        // -------------------------------------------------

        Button holdToSpeak =
                findViewById(R.id.btnHoldToSpeak);

        /*
         * HOLD TO SPEAK
         *
         * Press  = start recording
         * Release = stop recording
         * Cancel = stop recording
         */
        holdToSpeak.setOnTouchListener(
                (v, event) -> {

                    switch (event.getAction()) {

                        case MotionEvent.ACTION_DOWN:

                            requestMicAndListen();

                            return true;

                        case MotionEvent.ACTION_UP:

                            stopListening();

                            return true;

                        case MotionEvent.ACTION_CANCEL:

                            stopListening();

                            return true;

                        default:

                            return true;
                    }
                }
        );
    }

    // -----------------------------------------------------
    // MICROPHONE PERMISSION
    // -----------------------------------------------------

    private void requestMicAndListen() {

        if (destroyed) {
            return;
        }

        /*
         * Re-check the selected language every time
         * the microphone is pressed.
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
                selectedLanguage.trim()
        )) {

            Toast.makeText(
                    this,
                    "Live Classroom currently supports Santhali only.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    REQ_MIC
            );

            return;
        }

        listen();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == REQ_MIC &&
                grantResults.length > 0 &&
                grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED) {

            listen();

        } else if (requestCode == REQ_MIC) {

            Toast.makeText(
                    this,
                    "Microphone permission is required.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // -----------------------------------------------------
    // START LISTENING
    // -----------------------------------------------------

    private void listen() {

        if (destroyed) {
            return;
        }

        if (listening) {
            return;
        }

        if (!transliteratorReady) {

            Toast.makeText(
                    this,
                    "Preparing offline transliterator...",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        final long pipelineStart =
                System.currentTimeMillis();

        listening = true;

        pipelineStatus.setText(
                "🎤 Listening..."
        );

        asrEngine.startListening(
                this,
                new ASREngine.ResultCallback() {

                    @Override
                    public void onResult(
                            String hindiResult
                    ) {

                        listening = false;

                        if (destroyed) {
                            return;
                        }

                        /*
                         * Display raw ASR result.
                         */
                        hindiText.setText(
                                "ASR: " +
                                        hindiResult
                        );

                        pipelineStatus.setText(
                                "🔤 Converting to Devanagari..."
                        );

                        backgroundExecutor.execute(() -> {

                            try {

                                String devanagariHindi =
                                        hindiResult;

                                /*
                                 * IndicConformer normally outputs
                                 * Devanagari Hindi.
                                 *
                                 * IndicXlit is retained for
                                 * Roman/Latin ASR output.
                                 */
                                if (containsLatin(
                                        hindiResult
                                )) {

                                    devanagariHindi =
                                            transliterator
                                                    .transliterateSentence(
                                                            hindiResult
                                                    );
                                }

                                if (destroyed) {
                                    return;
                                }

                                final String finalHindi =
                                        devanagariHindi;

                                runOnUiThread(() -> {

                                    if (destroyed) {
                                        return;
                                    }

                                    hindiText.setText(
                                            "Hindi: " +
                                                    finalHindi
                                    );

                                    pipelineStatus.setText(
                                            "🧠 NMT running..."
                                    );
                                });

                                // -----------------------------------------
                                // Hindi -> Santhali
                                // -----------------------------------------

                                pipeline.translate(
                                        finalHindi,
                                        TARGET_LANGUAGE,
                                        (
                                                translatedText,
                                                confidence,
                                                source,
                                                nmtLatencyMs
                                        ) -> {

                                            if (destroyed) {
                                                return;
                                            }

                                            runOnUiThread(() -> {

                                                if (destroyed) {
                                                    return;
                                                }

                                                // -----------------------------------------
                                                // Display current translation
                                                // -----------------------------------------

                                                hoText.setText(
                                                        "Santhali: " +
                                                                translatedText
                                                );

                                                pipelineStatus.setText(
                                                        "🔊 TTS speaking..."
                                                );

                                                /*
                                                 * Existing behavior:
                                                 * automatically speak the new translation.
                                                 */
                                                if (ttsEngine != null &&
                                                        ttsEngine.isReady()) {

                                                    ttsEngine.speak(
                                                            translatedText
                                                    );
                                                }

                                                // -----------------------------------------
                                                // Latency
                                                // -----------------------------------------

                                                long totalLatency =
                                                        System.currentTimeMillis()
                                                                - pipelineStart;

                                                latencies.add(
                                                        totalLatency
                                                );

                                                long avg = 0;

                                                for (long latency :
                                                        latencies) {

                                                    avg += latency;
                                                }

                                                if (!latencies.isEmpty()) {

                                                    avg /=
                                                            latencies.size();
                                                }

                                                pipelineStatus.setText(
                                                        String.format(
                                                                Locale.US,
                                                                "🎤 ASR · 🔤 Transliteration · 🧠 NMT · 🔊 TTS | Last: %dms Avg: %dms",
                                                                totalLatency,
                                                                avg
                                                        )
                                                );

                                                // -----------------------------------------
                                                // Add to conversation log
                                                // -----------------------------------------

                                                String conversationItem =
                                                        "👩‍🏫 \"" +
                                                                finalHindi +
                                                                "\" → 🔊 \"" +
                                                                translatedText +
                                                                "\"";

                                                /*
                                                 * Insert newest message
                                                 * at position 0.
                                                 */
                                                logAdapter.insert(
                                                        conversationItem,
                                                        0
                                                );

                                                /*
                                                 * Store the exact translated
                                                 * text at the same position.
                                                 *
                                                 * This is what allows the user
                                                 * to tap a particular message
                                                 * and replay that exact translation.
                                                 */
                                                translatedMessages.add(
                                                        0,
                                                        translatedText
                                                );
                                            });
                                        }
                                );

                            } catch (Exception e) {

                                if (destroyed) {
                                    return;
                                }

                                runOnUiThread(() ->
                                        pipelineStatus.setText(
                                                "Translation error"
                                        )
                                );
                            }
                        });
                    }

                    @Override
                    public void onError(
                            String message
                    ) {

                        listening = false;

                        if (destroyed) {
                            return;
                        }

                        Toast.makeText(
                                VoiceToVoiceActivity.this,
                                "ASR: " + message,
                                Toast.LENGTH_SHORT
                        ).show();

                        pipelineStatus.setText(
                                "🎤 No speech detected"
                        );
                    }
                }
        );
    }

    // -----------------------------------------------------
    // LATIN DETECTION
    // -----------------------------------------------------

    private boolean containsLatin(
            String text
    ) {

        if (text == null) {
            return false;
        }

        for (int i = 0;
             i < text.length();
             i++) {

            char c =
                    text.charAt(i);

            if ((c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z')) {

                return true;
            }
        }

        return false;
    }

    // -----------------------------------------------------
    // STOP LISTENING
    // -----------------------------------------------------

    private void stopListening() {

        if (!listening) {
            return;
        }

        listening = false;

        if (asrEngine != null) {
            asrEngine.stopListening();
        }
    }

    // -----------------------------------------------------
    // CLEANUP
    // -----------------------------------------------------

    @Override
    protected void onDestroy() {

        destroyed = true;

        if (asrEngine != null) {
            asrEngine.stopListening();
        }

        /*
         * Stop any currently playing translated audio.
         */
        if (ttsEngine != null) {
            ttsEngine.stop();
        }

        backgroundExecutor.shutdownNow();

        if (asrEngine != null) {
            asrEngine.release();
        }

        if (ttsEngine != null) {
            ttsEngine.release();
        }

        if (transliterator != null) {
            transliterator.close();
        }

        if (pipeline != null) {
            pipeline.close();
        }

        super.onDestroy();
    }
}