package com.bhashasetu.app.ui;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bhashasetu.app.R;
import com.bhashasetu.app.engine.MaterialTranslator;
import com.bhashasetu.app.engine.PdfMaterialGenerator;
import com.bhashasetu.app.network.BhashaSetuApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WorksheetGenerationActivity extends AppCompatActivity {

    private static final String TARGET_LANGUAGE = "Santhali";

    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private static final String SELECT_TOPIC =
            "Select topic...";

    private Spinner curriculumSpinner;
    private Spinner classSpinner;
    private Spinner subjectSpinner;
    private Spinner difficultySpinner;

    private CheckBox worksheetCheckBox;
    private CheckBox flashcardCheckBox;

    private LinearLayout topicContainer;

    private Button generateButton;

    private TextView resultText;

    private BhashaSetuApi api;
    private MaterialTranslator materialTranslator;

    private boolean destroyed = false;

    private String lastCurriculum = "";
    private String lastClass = "";
    private String lastSubject = "";

    private final Map<String, List<String>> topicCatalog =
            new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_worksheet_generation
        );

        // -------------------------------------------------
        // Language check
        // -------------------------------------------------

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
                    "Worksheet generation currently supports Santhali only.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        // -------------------------------------------------
        // Find views
        // -------------------------------------------------

        curriculumSpinner =
                findViewById(R.id.spinnerCurriculum);

        classSpinner =
                findViewById(R.id.spinnerClass);

        subjectSpinner =
                findViewById(R.id.spinnerSubject);

        difficultySpinner =
                findViewById(R.id.spinnerDifficulty);

        worksheetCheckBox =
                findViewById(R.id.checkboxWorksheet);

        flashcardCheckBox =
                findViewById(R.id.checkboxFlashcards);

        topicContainer =
                findViewById(R.id.topicContainer);

        generateButton =
                findViewById(R.id.btnGenerateMaterial);

        resultText =
                findViewById(R.id.resultText);

        // -------------------------------------------------
        // Build local topic catalog
        // -------------------------------------------------

        buildTopicCatalog();

        // -------------------------------------------------
        // Main spinners
        // -------------------------------------------------

        setupSpinner(
                curriculumSpinner,
                new String[]{
                        "NCERT",
                        "JCERT"
                }
        );

        setupSpinner(
                classSpinner,
                new String[]{
                        "Class 1",
                        "Class 2",
                        "Class 3",
                        "Class 4",
                        "Class 5"
                }
        );

        setupSpinner(
                subjectSpinner,
                new String[]{
                        "Mathematics",
                        "Hindi",
                        "Environmental Studies"
                }
        );

        setupSpinner(
                difficultySpinner,
                new String[]{
                        "Easy",
                        "Medium",
                        "Hard"
                }
        );

        lastCurriculum =
                curriculumSpinner
                        .getSelectedItem()
                        .toString();

        lastClass =
                classSpinner
                        .getSelectedItem()
                        .toString();

        lastSubject =
                subjectSpinner
                        .getSelectedItem()
                        .toString();

        // -------------------------------------------------
        // Context listeners
        // -------------------------------------------------

        curriculumSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        handleContextChange(
                                parent.getItemAtPosition(position)
                                        .toString(),
                                classSpinner
                                        .getSelectedItem()
                                        .toString(),
                                subjectSpinner
                                        .getSelectedItem()
                                        .toString()
                        );
                    }
                }
        );

        classSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        handleContextChange(
                                curriculumSpinner
                                        .getSelectedItem()
                                        .toString(),
                                parent.getItemAtPosition(position)
                                        .toString(),
                                subjectSpinner
                                        .getSelectedItem()
                                        .toString()
                        );
                    }
                }
        );

        subjectSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {

                        handleContextChange(
                                curriculumSpinner
                                        .getSelectedItem()
                                        .toString(),
                                classSpinner
                                        .getSelectedItem()
                                        .toString(),
                                parent.getItemAtPosition(position)
                                        .toString()
                        );
                    }
                }
        );

        // -------------------------------------------------
        // Initial topic row
        // -------------------------------------------------

        addTopicRow(false);

        // -------------------------------------------------
        // Add topic
        // -------------------------------------------------

        Button addTopicButton =
                findViewById(R.id.btnAddTopic);

        addTopicButton.setOnClickListener(
                v -> addCurrentTopicAndCreateNext()
        );

        // -------------------------------------------------
        // Generate
        // -------------------------------------------------

        generateButton.setOnClickListener(
                v -> generateMaterial()
        );
    }

    // =====================================================
    // BASIC SPINNER
    // =====================================================

    private void setupSpinner(
            Spinner spinner,
            String[] items
    ) {

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        items
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinner.setAdapter(adapter);
    }

    private abstract static class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {

        @Override
        public void onNothingSelected(
                android.widget.AdapterView<?> parent
        ) {
        }
    }

    // =====================================================
    // TOPIC CATALOG
    // =====================================================

    private void buildTopicCatalog() {

        addTopics(
                "Mathematics",
                new String[]{
                        "Counting and Numbers",
                        "Addition",
                        "Subtraction",
                        "Shapes",
                        "Measurement",
                        "Patterns",
                        "Money",
                        "Time",
                        "Multiplication",
                        "Division",
                        "Fractions",
                        "Data Handling"
                }
        );

        addTopics(
                "Hindi",
                new String[]{
                        "वर्णमाला",
                        "मात्राएँ",
                        "शब्द",
                        "वाक्य",
                        "चित्र देखकर बोलना",
                        "कहानी",
                        "कविता",
                        "पठन",
                        "लेखन",
                        "सुनना और बोलना"
                }
        );

        addTopics(
                "Environmental Studies",
                new String[]{
                        "My Family",
                        "My Body",
                        "Food",
                        "Water",
                        "Animals",
                        "Plants",
                        "Our Home",
                        "Our School",
                        "Neighbourhood",
                        "Transport",
                        "Communication",
                        "Weather",
                        "Seasons",
                        "Cleanliness",
                        "Safety",
                        "Community Helpers",
                        "Nature"
                }
        );
    }

    private void addTopics(
            String subject,
            String[] topics
    ) {

        List<String> topicList =
                Arrays.asList(topics);

        for (String curriculum :
                new String[]{
                        "NCERT",
                        "JCERT"
                }) {

            for (int classNumber = 1;
                 classNumber <= 5;
                 classNumber++) {

                String key =
                        buildTopicKey(
                                curriculum,
                                String.valueOf(classNumber),
                                subject
                        );

                topicCatalog.put(
                        key,
                        new ArrayList<>(topicList)
                );
            }
        }
    }

    private String buildTopicKey(
            String curriculum,
            String className,
            String subject
    ) {

        return curriculum
                + "|"
                + className
                + "|"
                + subject;
    }

    private List<String> getTopicsForCurrentContext() {

        String curriculum =
                curriculumSpinner
                        .getSelectedItem()
                        .toString();

        String className =
                classSpinner
                        .getSelectedItem()
                        .toString()
                        .replace(
                                "Class ",
                                ""
                        );

        String subject =
                subjectSpinner
                        .getSelectedItem()
                        .toString();

        String key =
                buildTopicKey(
                        curriculum,
                        className,
                        subject
                );

        List<String> topics =
                topicCatalog.get(key);

        if (topics == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(
                topics
        );
    }

    // =====================================================
    // CONTEXT CHANGES
    // =====================================================

    private void handleContextChange(
            String newCurriculum,
            String newClass,
            String newSubject
    ) {

        if (newCurriculum.equals(lastCurriculum)
                && newClass.equals(lastClass)
                && newSubject.equals(lastSubject)) {

            refreshAllEditableTopicRows();

            return;
        }

        if (topicContainer != null
                && topicContainer.getChildCount() > 0
                && (hasFrozenRows()
                || hasUnsavedSelection())) {

            new AlertDialog.Builder(this)
                    .setTitle("Change lesson context?")
                    .setMessage(
                            "Changing the curriculum, class, or subject "
                                    + "will clear the currently selected topics."
                    )
                    .setNegativeButton(
                            "Cancel",
                            (dialog, which) ->
                                    restorePreviousSelections()
                    )
                    .setPositiveButton(
                            "Change",
                            (dialog, which) -> {

                                lastCurriculum =
                                        newCurriculum;

                                lastClass =
                                        newClass;

                                lastSubject =
                                        newSubject;

                                resetTopicRows();
                            }
                    )
                    .show();

            return;
        }

        lastCurriculum =
                newCurriculum;

        lastClass =
                newClass;

        lastSubject =
                newSubject;

        resetTopicRows();
    }

    private void restorePreviousSelections() {

        setSpinnerValue(
                curriculumSpinner,
                lastCurriculum
        );

        setSpinnerValue(
                classSpinner,
                lastClass
        );

        setSpinnerValue(
                subjectSpinner,
                lastSubject
        );
    }

    private void setSpinnerValue(
            Spinner spinner,
            String value
    ) {

        if (value == null) {
            return;
        }

        for (int i = 0;
             i < spinner.getCount();
             i++) {

            if (value.equals(
                    spinner.getItemAtPosition(i)
                            .toString()
            )) {

                spinner.setSelection(i);
                return;
            }
        }
    }

    // =====================================================
    // TOPIC ROWS
    // =====================================================

    private void addTopicRow(
            boolean frozen
    ) {

        View row =
                getLayoutInflater()
                        .inflate(
                                R.layout.item_topic_date,
                                topicContainer,
                                false
                        );

        topicContainer.addView(row);

        configureTopicRow(
                row,
                frozen
        );
    }

    private void configureTopicRow(
            View row,
            boolean frozen
    ) {

        Spinner topicSpinner =
                row.findViewById(
                        R.id.topicInput
                );

        NumberPicker yearPicker =
                row.findViewById(
                        R.id.yearPicker
                );

        NumberPicker monthPicker =
                row.findViewById(
                        R.id.monthPicker
                );

        NumberPicker dayPicker =
                row.findViewById(
                        R.id.dayPicker
                );

        Button deleteButton =
                row.findViewById(
                        R.id.deleteTopicButton
                );

        /*
         * IMPORTANT:
         *
         * The first item is now "Select topic..."
         * so a newly-created row has no selected topic.
         */
        setupTopicSpinner(
                topicSpinner,
                getTopicsForCurrentContext()
        );

        Calendar today =
                Calendar.getInstance();

        int currentYear =
                today.get(Calendar.YEAR);

        yearPicker.setMinValue(
                MIN_YEAR
        );

        yearPicker.setMaxValue(
                MAX_YEAR
        );

        yearPicker.setValue(
                Math.max(
                        MIN_YEAR,
                        Math.min(
                                currentYear,
                                MAX_YEAR
                        )
                )
        );

        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setValue(
                today.get(Calendar.MONTH) + 1
        );

        updateDayPicker(
                yearPicker,
                monthPicker,
                dayPicker,
                today.get(Calendar.DAY_OF_MONTH)
        );

        yearPicker.setOnValueChangedListener(
                (picker, oldVal, newVal) ->
                        updateDayPicker(
                                yearPicker,
                                monthPicker,
                                dayPicker,
                                dayPicker.getValue()
                        )
        );

        monthPicker.setOnValueChangedListener(
                (picker, oldVal, newVal) ->
                        updateDayPicker(
                                yearPicker,
                                monthPicker,
                                dayPicker,
                                dayPicker.getValue()
                        )
        );

        deleteButton.setOnClickListener(
                v -> {

                    topicContainer.removeView(row);

                    if (topicContainer.getChildCount() == 0) {
                        addTopicRow(false);
                    }
                }
        );

        if (frozen) {
            freezeRow(row);
        } else {
            makeRowEditable(row);
        }
    }

    private void setupTopicSpinner(
            Spinner spinner,
            List<String> topics
    ) {

        List<String> spinnerItems =
                new ArrayList<>();

        /*
         * Placeholder.
         */
        spinnerItems.add(
                SELECT_TOPIC
        );

        if (topics != null) {
            spinnerItems.addAll(
                    topics
            );
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        spinnerItems
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinner.setAdapter(adapter);

        /*
         * Always start on the placeholder.
         */
        spinner.setSelection(0);
    }

    private void refreshAllEditableTopicRows() {

        List<String> topics =
                getTopicsForCurrentContext();

        for (int i = 0;
             i < topicContainer.getChildCount();
             i++) {

            View row =
                    topicContainer.getChildAt(i);

            Button deleteButton =
                    row.findViewById(
                            R.id.deleteTopicButton
                    );

            /*
             * Delete button is visible only for frozen rows.
             */
            if (deleteButton.getVisibility()
                    != View.VISIBLE) {

                Spinner topicSpinner =
                        row.findViewById(
                                R.id.topicInput
                        );

                setupTopicSpinner(
                        topicSpinner,
                        topics
                );
            }
        }
    }

    private void addCurrentTopicAndCreateNext() {

        if (topicContainer.getChildCount() == 0) {

            addTopicRow(false);

            return;
        }

        View currentRow =
                topicContainer.getChildAt(
                        topicContainer.getChildCount() - 1
                );

        if (!validateTopicRow(currentRow)) {
            return;
        }

        freezeRow(
                currentRow
        );

        addTopicRow(false);
    }

    private boolean validateTopicRow(
            View row
    ) {

        Spinner topicSpinner =
                row.findViewById(
                        R.id.topicInput
                );

        if (topicSpinner.getSelectedItem() == null) {

            Toast.makeText(
                    this,
                    "Please select a topic.",
                    Toast.LENGTH_SHORT
            ).show();

            return false;
        }

        String topic =
                topicSpinner
                        .getSelectedItem()
                        .toString()
                        .trim();

        if (topic.isEmpty()
                || topic.equals(SELECT_TOPIC)) {

            Toast.makeText(
                    this,
                    "Please select a topic.",
                    Toast.LENGTH_SHORT
            ).show();

            return false;
        }

        return true;
    }

    private void freezeRow(
            View row
    ) {

        Spinner topicSpinner =
                row.findViewById(
                        R.id.topicInput
                );

        NumberPicker yearPicker =
                row.findViewById(
                        R.id.yearPicker
                );

        NumberPicker monthPicker =
                row.findViewById(
                        R.id.monthPicker
                );

        NumberPicker dayPicker =
                row.findViewById(
                        R.id.dayPicker
                );

        Button deleteButton =
                row.findViewById(
                        R.id.deleteTopicButton
                );

        topicSpinner.setEnabled(false);
        yearPicker.setEnabled(false);
        monthPicker.setEnabled(false);
        dayPicker.setEnabled(false);

        deleteButton.setVisibility(
                View.VISIBLE
        );

        row.setAlpha(0.82f);
    }

    private void makeRowEditable(
            View row
    ) {

        Spinner topicSpinner =
                row.findViewById(
                        R.id.topicInput
                );

        NumberPicker yearPicker =
                row.findViewById(
                        R.id.yearPicker
                );

        NumberPicker monthPicker =
                row.findViewById(
                        R.id.monthPicker
                );

        NumberPicker dayPicker =
                row.findViewById(
                        R.id.dayPicker
                );

        Button deleteButton =
                row.findViewById(
                        R.id.deleteTopicButton
                );

        topicSpinner.setEnabled(true);
        yearPicker.setEnabled(true);
        monthPicker.setEnabled(true);
        dayPicker.setEnabled(true);

        deleteButton.setVisibility(
                View.GONE
        );

        row.setAlpha(1.0f);
    }

    private boolean hasFrozenRows() {

        for (int i = 0;
             i < topicContainer.getChildCount();
             i++) {

            View row =
                    topicContainer.getChildAt(i);

            Button deleteButton =
                    row.findViewById(
                            R.id.deleteTopicButton
                    );

            if (deleteButton.getVisibility()
                    == View.VISIBLE) {

                return true;
            }
        }

        return false;
    }

    /*
     * A draft row is considered unsaved only when the
     * user explicitly selected a real topic.
     */
    private boolean hasUnsavedSelection() {

        if (topicContainer.getChildCount() == 0) {
            return false;
        }

        View row =
                topicContainer.getChildAt(
                        topicContainer.getChildCount() - 1
                );

        Button deleteButton =
                row.findViewById(
                        R.id.deleteTopicButton
                );

        /*
         * Frozen row is already saved.
         */
        if (deleteButton.getVisibility()
                == View.VISIBLE) {

            return false;
        }

        Spinner topicSpinner =
                row.findViewById(
                        R.id.topicInput
                );

        if (topicSpinner.getSelectedItem() == null) {
            return false;
        }

        String selectedTopic =
                topicSpinner
                        .getSelectedItem()
                        .toString()
                        .trim();

        return !selectedTopic.isEmpty()
                && !selectedTopic.equals(
                SELECT_TOPIC
        );
    }

    private void resetTopicRows() {

        topicContainer.removeAllViews();

        addTopicRow(false);
    }

    // =====================================================
    // DATE
    // =====================================================

    private void updateDayPicker(
            NumberPicker yearPicker,
            NumberPicker monthPicker,
            NumberPicker dayPicker,
            int preferredDay
    ) {

        int year =
                yearPicker.getValue();

        int month =
                monthPicker.getValue();

        int maxDay =
                getDaysInMonth(
                        year,
                        month
                );

        int safeDay =
                Math.max(
                        1,
                        Math.min(
                                preferredDay,
                                maxDay
                        )
                );

        dayPicker.setMinValue(1);
        dayPicker.setMaxValue(maxDay);
        dayPicker.setValue(safeDay);
    }

    private int getDaysInMonth(
            int year,
            int month
    ) {

        Calendar calendar =
                Calendar.getInstance();

        calendar.set(
                Calendar.YEAR,
                year
        );

        calendar.set(
                Calendar.MONTH,
                month - 1
        );

        calendar.set(
                Calendar.DAY_OF_MONTH,
                1
        );

        return calendar.getActualMaximum(
                Calendar.DAY_OF_MONTH
        );
    }

    private String getSelectedDate(
            View row
    ) {

        NumberPicker yearPicker =
                row.findViewById(
                        R.id.yearPicker
                );

        NumberPicker monthPicker =
                row.findViewById(
                        R.id.monthPicker
                );

        NumberPicker dayPicker =
                row.findViewById(
                        R.id.dayPicker
                );

        return String.format(
                Locale.US,
                "%04d-%02d-%02d",
                yearPicker.getValue(),
                monthPicker.getValue(),
                dayPicker.getValue()
        );
    }

    // =====================================================
    // GENERATE
    // =====================================================

    private void generateMaterial() {

        if (destroyed) {
            return;
        }

        generateButton.setEnabled(false);
        generateButton.setText("Creating PDFs...");

        resultText.setText(
                "Generating Hindi → Santhali material..."
        );

        new Thread(() -> {

            PdfMaterialGenerator generator = null;

            try {

                generator =
                        new PdfMaterialGenerator(
                                getApplicationContext()
                        );

                java.util.List<String> generatedFiles =
                        new java.util.ArrayList<>();

                // ---------------------------------------------
                // Worksheet
                // ---------------------------------------------

                PdfMaterialGenerator.GeneratedFile worksheet =
                        generator.generateWorksheet();

                generatedFiles.add(
                        worksheet.fileName
                );

                // ---------------------------------------------
                // Five flashcards
                // ---------------------------------------------

                for (int i = 1; i <= 5; i++) {

                    PdfMaterialGenerator.GeneratedFile flashcard =
                            generator.generateFlashcard(i);

                    generatedFiles.add(
                            flashcard.fileName
                    );
                }

                // ---------------------------------------------
                // Cleanup
                // ---------------------------------------------

                generator.close();
                generator = null;

                runOnUiThread(() -> {

                    if (destroyed) {
                        return;
                    }

                    generateButton.setEnabled(true);
                    generateButton.setText(
                            "Generate Material"
                    );

                    StringBuilder result =
                            new StringBuilder();

                    result.append(
                            "PDFs CREATED SUCCESSFULLY\n\n"
                    );

                    result.append(
                            "Curriculum: JCERT\n"
                    );

                    result.append(
                            "Class: 2\n"
                    );

                    result.append(
                            "Subject: Hindi\n"
                    );

                    result.append(
                            "Topic: वर्णमाला\n"
                    );

                    result.append(
                            "Difficulty: Easy\n\n"
                    );

                    result.append(
                            "Hindi → Santhali\n\n"
                    );

                    result.append(
                            "Worksheet: 5 questions\n"
                    );

                    result.append(
                            "Flashcards: 5 separate PDFs\n"
                    );

                    result.append(
                            "Total: 6 PDFs\n\n"
                    );

                    result.append(
                            "Saved to:\n"
                    );

                    result.append(
                            "Downloads/BhashaSetu/\n\n"
                    );

                    for (String fileName :
                            generatedFiles) {

                        result.append(
                                fileName
                        );

                        result.append("\n");
                    }

                    resultText.setText(
                            result.toString()
                    );

                    Toast.makeText(
                            WorksheetGenerationActivity.this,
                            "Hindi → Santhali translation complete. 6 PDFs created.",
                            Toast.LENGTH_LONG
                    ).show();
                });

            } catch (Exception e) {

                if (generator != null) {

                    try {
                        generator.close();
                    } catch (Exception ignored) {
                    }
                }

                final String errorMessage =
                        e.getMessage() == null
                                ? e.getClass().getSimpleName()
                                : e.getMessage();

                runOnUiThread(() -> {

                    if (destroyed) {
                        return;
                    }

                    generateButton.setEnabled(true);

                    generateButton.setText(
                            "Generate Material"
                    );

                    resultText.setText(
                            "TRANSLATION / PDF ERROR\n\n"
                                    + errorMessage
                    );
                });
            }

        }).start();
    }

    // =====================================================
    // DISPLAY
    // =====================================================

    private void displayTranslatedMaterial(
            JSONObject serverResponse,
            JSONArray translatedQueue
    ) {

        StringBuilder output =
                new StringBuilder();

        output.append(
                "BILINGUAL MATERIAL\n\n"
        );

        output.append(
                "Language: Santhali\n\n"
        );

        try {

            for (int i = 0;
                 i < translatedQueue.length();
                 i++) {

                JSONObject item =
                        translatedQueue.getJSONObject(i);

                String type =
                        item.optString(
                                "type",
                                ""
                        );

                String hindi =
                        item.optString(
                                "text_hi",
                                ""
                        );

                String santhali =
                        item.optString(
                                "text_sat",
                                ""
                        );

                output.append(
                        type.toUpperCase()
                );

                output.append(
                        "\nHindi: "
                );

                output.append(
                        hindi
                );

                output.append(
                        "\nSanthali: "
                );

                output.append(
                        santhali
                );

                output.append(
                        "\n\n"
                );
            }

            resultText.setText(
                    output.toString()
            );

        } catch (Exception e) {

            resultText.setText(
                    "DISPLAY ERROR\n\n"
                            + e.getMessage()
            );
        }
    }

    // =====================================================
    // CLEANUP
    // =====================================================

    @Override
    protected void onDestroy() {

        destroyed = true;

        if (api != null) {

            api.close();

            api = null;
        }

        if (materialTranslator != null) {

            materialTranslator.close();

            materialTranslator = null;
        }

        super.onDestroy();
    }
}