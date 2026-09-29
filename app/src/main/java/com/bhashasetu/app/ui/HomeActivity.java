package com.bhashasetu.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bhashasetu.app.R;
import com.bhashasetu.app.database.AppDatabase;

import java.util.Calendar;
import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {

    public static final String PREFS = "bhashasetu_prefs";
    public static final String KEY_LANGUAGE = "target_language";

    private Spinner languageSpinner;

    private View cardTextToSpeech;
    private View cardVoiceToVoice;
    private View cardWorksheet;

    private final String[] languages = {
            "Ho",
            "Santhali",
            "Mundari"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);

        languageSpinner =
                findViewById(R.id.languageSpinner);

        cardTextToSpeech =
                findViewById(R.id.cardTextToSpeech);

        cardVoiceToVoice =
                findViewById(R.id.cardVoiceToVoice);

        /*
         * We are reusing the existing Lesson Plan card
         * as the Worksheet & Flashcard Generation entry.
         */
        cardWorksheet =
                findViewById(R.id.cardLessonPlan);

        // -------------------------------------------------
        // Language spinner
        // -------------------------------------------------

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        languages
                );

        languageSpinner.setAdapter(adapter);

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        /*
         * Santhali is the currently supported prototype
         * translation language.
         */
        String savedLang =
                prefs.getString(
                        KEY_LANGUAGE,
                        "Santhali"
                );

        for (int i = 0;
             i < languages.length;
             i++) {

            if (languages[i].equalsIgnoreCase(savedLang)) {

                languageSpinner.setSelection(i);

                break;
            }
        }

        languageSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        String selectedLanguage =
                                languages[position];

                        prefs.edit()
                                .putString(
                                        KEY_LANGUAGE,
                                        selectedLanguage
                                )
                                .apply();

                        updateTranslationCards(
                                selectedLanguage
                        );
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent
                    ) {
                    }
                }
        );

        // -------------------------------------------------
        // Voice Translate - under development
        // -------------------------------------------------

        findViewById(
                R.id.cardSpeechToText
        ).setOnClickListener(v -> {

            Toast.makeText(
                    this,
                    "Voice Translate is under development.",
                    Toast.LENGTH_SHORT
            ).show();
        });

        // -------------------------------------------------
        // Text Translate
        // -------------------------------------------------

        cardTextToSpeech.setOnClickListener(v -> {

            if (!isSanthaliSelected()) {

                showSanthaliOnlyMessage();

                return;
            }

            startActivity(
                    new Intent(
                            this,
                            TextToSpeechActivity.class
                    )
            );
        });

        // -------------------------------------------------
        // Live Classroom
        // -------------------------------------------------

        cardVoiceToVoice.setOnClickListener(v -> {

            if (!isSanthaliSelected()) {

                showSanthaliOnlyMessage();

                return;
            }

            startActivity(
                    new Intent(
                            this,
                            VoiceToVoiceActivity.class
                    )
            );
        });

        // -------------------------------------------------
        // Worksheet & Flashcard Generation
        // -------------------------------------------------

        /*
         * The old Lesson Plan card is being reused for the
         * worksheet/flashcard generation prototype.
         *
         * It may currently be disabled in XML, so we explicitly
         * enable it here.
         */
        cardWorksheet.setEnabled(true);
        cardWorksheet.setClickable(true);
        cardWorksheet.setFocusable(true);

        cardWorksheet.setOnClickListener(v -> {

            if (!isSanthaliSelected()) {

                showSanthaliOnlyMessage();

                return;
            }

            startActivity(
                    new Intent(
                            this,
                            WorksheetGenerationActivity.class
                    )
            );
        });

        // -------------------------------------------------
        // Initial state
        // -------------------------------------------------

        updateTranslationCards(savedLang);

        updateStatusBar();
    }

    @Override
    protected void onResume() {

        super.onResume();

        updateStatusBar();
    }

    /**
     * Returns true only when Santhali is selected.
     */
    private boolean isSanthaliSelected() {

        Object selected =
                languageSpinner.getSelectedItem();

        return selected != null
                && selected.toString()
                .trim()
                .equalsIgnoreCase("Santhali");
    }

    /**
     * Updates the visual state of all features that
     * currently depend on Santhali.
     */
    private void updateTranslationCards(
            String selectedLanguage
    ) {

        boolean santhali =
                selectedLanguage != null
                        && selectedLanguage.trim()
                        .equalsIgnoreCase("Santhali");

        float alpha =
                santhali
                        ? 1.0f
                        : 0.55f;

        cardTextToSpeech.setAlpha(alpha);

        cardVoiceToVoice.setAlpha(alpha);

        cardWorksheet.setAlpha(alpha);

        /*
         * Keep the worksheet card usable as a navigation
         * entry; it will display a message if another
         * language is selected.
         */
        cardWorksheet.setEnabled(true);
        cardWorksheet.setClickable(true);
        cardWorksheet.setFocusable(true);
    }

    private void showSanthaliOnlyMessage() {

        Toast.makeText(
                this,
                "This feature is currently available only for Santhali.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void updateStatusBar() {

        TextView statusBarText =
                findViewById(
                        R.id.statusBarText
                );

        Executors
                .newSingleThreadExecutor()
                .execute(() -> {

                    Calendar cal =
                            Calendar.getInstance();

                    cal.set(
                            Calendar.HOUR_OF_DAY,
                            0
                    );

                    cal.set(
                            Calendar.MINUTE,
                            0
                    );

                    cal.set(
                            Calendar.SECOND,
                            0
                    );

                    cal.set(
                            Calendar.MILLISECOND,
                            0
                    );

                    int countToday =
                            AppDatabase
                                    .getInstance(this)
                                    .translationCacheDao()
                                    .countSince(
                                            cal.getTimeInMillis()
                                    );

                    runOnUiThread(() ->
                            statusBarText.setText(
                                    "📊 Today: "
                                            + countToday
                                            + " translations | ⚡ Offline | 🕐 Never synced"
                            )
                    );
                });
    }
}