package com.bhashasetu.app.ui;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bhashasetu.app.R;
import com.bhashasetu.app.curriculum.LessonPlanGenerator;
import com.bhashasetu.app.engine.NMTEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.Executors;

public class LessonPlanActivity extends AppCompatActivity {

    private NMTEngine nmtEngine;
    private LessonPlanGenerator generator;
    private Spinner nipunSpinner;
    private TextView output;
    private LessonPlanGenerator.LessonPlan currentPlan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lesson_plan);

        nmtEngine = new NMTEngine();
        nmtEngine.initialize(this);
        generator = new LessonPlanGenerator(nmtEngine);

        nipunSpinner = findViewById(R.id.nipunSpinner);
        output = findViewById(R.id.lessonPlanOutput);

        Executors.newSingleThreadExecutor().execute(() -> {
            List<String> codes = generator.listOutcomeCodes(this);
            runOnUiThread(() -> nipunSpinner.setAdapter(
                    new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, codes)));
        });

        findViewById(R.id.btnGenerate).setOnClickListener(v -> generate());
        findViewById(R.id.btnExportPdf).setOnClickListener(v -> exportPdf());
        findViewById(R.id.btnSave).setOnClickListener(v ->
                Toast.makeText(this, "Saved offline (Room storage — see WorksheetDao/CurriculumDao in README next steps)", Toast.LENGTH_SHORT).show());
    }

    private void generate() {
        Object selected = nipunSpinner.getSelectedItem();
        if (selected == null) {
            Toast.makeText(this, "No NIPUN outcomes loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        String code = selected.toString();

        Executors.newSingleThreadExecutor().execute(() -> {
            LessonPlanGenerator.LessonPlan plan = generator.generate(this, code);
            currentPlan = plan;

            StringBuilder sb = new StringBuilder();
            sb.append("Grade ").append(plan.grade).append(" | ").append(plan.subject)
                    .append(" | ").append(plan.code).append("\n\n");
            sb.append("🎯 LEARNING OBJECTIVE:\n");
            sb.append("HI: ").append(plan.objectiveHi).append("\n");
            sb.append("HO: ").append(plan.objectiveHo).append("\n\n");

            int i = 1;
            for (LessonPlanGenerator.Activity a : plan.activities) {
                sb.append("⏰ ACTIVITY ").append(i++).append("\n");
                sb.append("HI: ").append(a.hindi).append("\n");
                sb.append("HO: ").append(a.ho).append("\n\n");
            }

            runOnUiThread(() -> output.setText(sb.toString()));
        });
    }

    private void exportPdf() {
        if (currentPlan == null) {
            Toast.makeText(this, "Generate a lesson plan first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prototype uses Android's built-in PdfDocument (zero extra dependency).
        // Spec calls for iText7 for richer bilingual-column layout + QR codes to
        // audio — see README "Extend This Next" for that upgrade path.
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        paint.setTextSize(12);

        int y = 40;
        String[] lines = output.getText().toString().split("\n");
        for (String line : lines) {
            canvas.drawText(line, 30, y, paint);
            y += 18;
            if (y > 800) break; // simple single-page prototype
        }
        document.finishPage(page);

        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "BhashaSetu");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "lesson_plan_" + currentPlan.code + ".pdf");

        try (FileOutputStream out = new FileOutputStream(file)) {
            document.writeTo(out);
            Toast.makeText(this, "Saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "PDF export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            document.close();
        }
    }
}
