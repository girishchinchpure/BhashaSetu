package com.bhashasetu.app.engine;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PdfMaterialGenerator {

    public static class GeneratedFile {

        public final String fileName;
        public final Uri uri;

        public GeneratedFile(
                String fileName,
                Uri uri
        ) {
            this.fileName = fileName;
            this.uri = uri;
        }
    }

    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;

    private final Context context;
    private final TranslationPipeline translationPipeline;

    public PdfMaterialGenerator(Context context) {

        this.context =
                context.getApplicationContext();

        translationPipeline =
                new TranslationPipeline(
                        this.context
                );
    }

    // =========================================================
    // TRANSLATION
    // =========================================================

    private String translateHindiToSanthali(
            String hindiText
    ) throws Exception {

        if (hindiText == null
                || hindiText.trim().isEmpty()) {

            return "";
        }

        final CountDownLatch latch =
                new CountDownLatch(1);

        final AtomicReference<String> result =
                new AtomicReference<>("");

        final AtomicReference<Exception> error =
                new AtomicReference<>(null);

        translationPipeline.translate(
                hindiText,
                "Santhali",
                (
                        translatedText,
                        confidence,
                        source,
                        latencyMs
                ) -> {

                    if (translatedText == null) {

                        error.set(
                                new Exception(
                                        "Translation returned null."
                                )
                        );

                    } else if (
                            translatedText.startsWith(
                                    "[PIPELINE ERROR:"
                            )
                    ) {

                        error.set(
                                new Exception(
                                        translatedText
                                )
                        );

                    } else {

                        result.set(
                                translatedText.trim()
                        );
                    }

                    latch.countDown();
                }
        );

        boolean completed =
                latch.await(
                        120,
                        TimeUnit.SECONDS
                );

        if (!completed) {

            throw new Exception(
                    "Hindi to Santali translation timed out."
            );
        }

        if (error.get() != null) {
            throw error.get();
        }

        String translated =
                result.get();

        if (translated == null
                || translated.trim().isEmpty()) {

            throw new Exception(
                    "The translation model returned empty output."
            );
        }

        return translated.trim();
    }

    // =========================================================
    // WORKSHEET
    // =========================================================

    public GeneratedFile generateWorksheet()
            throws Exception {

        String date =
                new SimpleDateFormat(
                        "dd MMMM yyyy",
                        Locale.ENGLISH
                ).format(
                        new Date()
                );

        String[] hindiQuestions = {

                "1. अ से शुरू होने वाला शब्द चुनिए: अनार / कमल",

                "2. क के बाद कौन-सा अक्षर आता है?",

                "3. खाली स्थान भरिए: अ, आ, ___, ई",

                "4. इनमें से स्वर कौन-सा है? क / म / उ",

                "5. इन अक्षरों को सही क्रम में लिखिए: इ, अ, आ"
        };

        /*
         * Translate the five questions using
         * the actual offline Hindi -> Santali model.
         */
        String[] santhaliQuestions =
                new String[hindiQuestions.length];

        for (int i = 0;
             i < hindiQuestions.length;
             i++) {

            santhaliQuestions[i] =
                    translateHindiToSanthali(
                            hindiQuestions[i]
                    );
        }

        String fileName =
                "JCERT_Class2_Hindi_Varnamala_Worksheet.pdf";

        PdfDocument document =
                new PdfDocument();

        try {

            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(
                            PAGE_WIDTH,
                            PAGE_HEIGHT,
                            1
                    ).create();

            PdfDocument.Page page =
                    document.startPage(
                            pageInfo
                    );

            Canvas canvas =
                    page.getCanvas();

            Paint titlePaint =
                    createPaint(
                            24,
                            true
                    );

            Paint headingPaint =
                    createPaint(
                            18,
                            true
                    );

            Paint normalPaint =
                    createPaint(
                            15,
                            false
                    );

            Paint santhaliPaint =
                    createPaint(
                            15,
                            false
                    );

            Paint smallPaint =
                    createPaint(
                            12,
                            false
                    );

            Paint linePaint =
                    createPaint(
                            1,
                            false
                    );

            float x = 50;
            float y = 60;

            // -------------------------------------------------
            // Header
            // -------------------------------------------------

            drawCenteredText(
                    canvas,
                    "BhashaSetu",
                    297,
                    y,
                    titlePaint
            );

            y += 38;

            drawCenteredText(
                    canvas,
                    "Class 2 Worksheet",
                    297,
                    y,
                    headingPaint
            );

            y += 32;

            drawCenteredText(
                    canvas,
                    "Subject: Hindi",
                    297,
                    y,
                    normalPaint
            );

            y += 24;

            drawCenteredText(
                    canvas,
                    "Topic: वर्णमाला",
                    297,
                    y,
                    normalPaint
            );

            y += 24;

            drawCenteredText(
                    canvas,
                    "Date: " + date,
                    297,
                    y,
                    smallPaint
            );

            y += 45;

            // -------------------------------------------------
            // Student details
            // -------------------------------------------------

            canvas.drawText(
                    "Name: ________________________________",
                    x,
                    y,
                    normalPaint
            );

            y += 30;

            canvas.drawText(
                    "Roll No.: __________",
                    x,
                    y,
                    normalPaint
            );

            y += 42;

            // -------------------------------------------------
            // Questions
            // -------------------------------------------------

            for (int i = 0;
                 i < hindiQuestions.length;
                 i++) {

                y = drawWrappedText(
                        canvas,
                        hindiQuestions[i],
                        x,
                        y,
                        495,
                        normalPaint,
                        21
                );

                y += 4;

                y = drawWrappedText(
                        canvas,
                        "Santhali: "
                                + santhaliQuestions[i],
                        x,
                        y,
                        495,
                        santhaliPaint,
                        21
                );

                y += 8;

                canvas.drawLine(
                        x,
                        y,
                        545,
                        y,
                        linePaint
                );

                y += 24;
            }

            // -------------------------------------------------
            // Footer
            // -------------------------------------------------

            drawCenteredText(
                    canvas,
                    "Difficulty: Easy",
                    297,
                    790,
                    smallPaint
            );

            document.finishPage(page);

            Uri uri =
                    savePdf(
                            document,
                            fileName
                    );

            if (uri == null) {

                throw new IOException(
                        "Could not save worksheet PDF."
                );
            }

            return new GeneratedFile(
                    fileName,
                    uri
            );

        } finally {

            document.close();
        }
    }

    // =========================================================
    // FLASHCARD
    // =========================================================

    public GeneratedFile generateFlashcard(
            int index
    ) throws Exception {

        if (index < 1
                || index > 5) {

            throw new IllegalArgumentException(
                    "Flashcard index must be between 1 and 5."
            );
        }

        String[] hindiFronts = {

                "अ",
                "आ",
                "इ",
                "ई",
                "उ"
        };

        String[] hindiExamples = {

                "अनार",
                "आम",
                "इमली",
                "ईख",
                "उल्लू"
        };

        String hindiFront =
                hindiFronts[index - 1];

        String hindiExample =
                hindiExamples[index - 1];

        /*
         * Translate both the letter/example content
         * through the offline pipeline.
         */
        String santhaliFront =
                translateHindiToSanthali(
                        hindiFront
                );

        String santhaliExample =
                translateHindiToSanthali(
                        hindiExample
                );

        String fileName =
                "JCERT_Class2_Hindi_Varnamala_Flashcard_"
                        + index
                        + ".pdf";

        PdfDocument document =
                new PdfDocument();

        try {

            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(
                            PAGE_WIDTH,
                            PAGE_HEIGHT,
                            1
                    ).create();

            PdfDocument.Page page =
                    document.startPage(
                            pageInfo
                    );

            Canvas canvas =
                    page.getCanvas();

            Paint titlePaint =
                    createPaint(
                            24,
                            true
                    );

            Paint headingPaint =
                    createPaint(
                            16,
                            true
                    );

            Paint hindiPaint =
                    createPaint(
                            15,
                            false
                    );

            Paint santhaliPaint =
                    createPaint(
                            18,
                            false
                    );

            Paint letterPaint =
                    createPaint(
                            72,
                            true
                    );

            Paint smallPaint =
                    createPaint(
                            12,
                            false
                    );

            Paint borderPaint =
                    createPaint(
                            3,
                            false
                    );

            borderPaint.setStyle(
                    Paint.Style.STROKE
            );

            // -------------------------------------------------
            // Header
            // -------------------------------------------------

            drawCenteredText(
                    canvas,
                    "BhashaSetu",
                    297,
                    65,
                    titlePaint
            );

            drawCenteredText(
                    canvas,
                    "Class 2 - Hindi",
                    297,
                    95,
                    smallPaint
            );

            drawCenteredText(
                    canvas,
                    "वर्णमाला",
                    297,
                    120,
                    smallPaint
            );

            // -------------------------------------------------
            // Card border
            // -------------------------------------------------

            canvas.drawRoundRect(
                    55,
                    165,
                    540,
                    700,
                    22,
                    22,
                    borderPaint
            );

            // -------------------------------------------------
            // Hindi letter
            // -------------------------------------------------

            drawCenteredText(
                    canvas,
                    "Hindi",
                    297,
                    230,
                    headingPaint
            );

            drawCenteredText(
                    canvas,
                    hindiFront,
                    297,
                    350,
                    letterPaint
            );

            // -------------------------------------------------
            // Santali translation
            // -------------------------------------------------

            drawCenteredText(
                    canvas,
                    "Santhali",
                    297,
                    430,
                    headingPaint
            );

            drawCenteredText(
                    canvas,
                    santhaliFront,
                    297,
                    475,
                    santhaliPaint
            );

            // -------------------------------------------------
            // Example
            // -------------------------------------------------

            drawCenteredText(
                    canvas,
                    "Hindi example",
                    297,
                    545,
                    smallPaint
            );

            drawCenteredText(
                    canvas,
                    hindiExample,
                    297,
                    580,
                    hindiPaint
            );

            drawCenteredText(
                    canvas,
                    "Santhali example",
                    297,
                    630,
                    smallPaint
            );

            drawCenteredText(
                    canvas,
                    santhaliExample,
                    297,
                    670,
                    santhaliPaint
            );

            drawCenteredText(
                    canvas,
                    "Difficulty: Easy",
                    297,
                    775,
                    smallPaint
            );

            document.finishPage(page);

            Uri uri =
                    savePdf(
                            document,
                            fileName
                    );

            if (uri == null) {

                throw new IOException(
                        "Could not save flashcard PDF."
                );
            }

            return new GeneratedFile(
                    fileName,
                    uri
            );

        } finally {

            document.close();
        }
    }

    // =========================================================
    // SAVE PDF
    // =========================================================

    private Uri savePdf(
            PdfDocument document,
            String fileName
    ) throws IOException {

        ContentResolver resolver =
                context.getContentResolver();

        // Android 10+
        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.Q) {

            Uri uri = null;

            try {

                ContentValues values =
                        new ContentValues();

                values.put(
                        MediaStore.Downloads.DISPLAY_NAME,
                        fileName
                );

                values.put(
                        MediaStore.Downloads.MIME_TYPE,
                        "application/pdf"
                );

                values.put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS
                                + "/BhashaSetu"
                );

                values.put(
                        MediaStore.Downloads.IS_PENDING,
                        1
                );

                uri =
                        resolver.insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                values
                        );

                if (uri == null) {

                    throw new IOException(
                            "Could not create download entry."
                    );
                }

                try (
                        OutputStream outputStream =
                                resolver.openOutputStream(uri)
                ) {

                    if (outputStream == null) {

                        throw new IOException(
                                "Could not open PDF output stream."
                        );
                    }

                    document.writeTo(
                            outputStream
                    );
                }

                ContentValues completed =
                        new ContentValues();

                completed.put(
                        MediaStore.Downloads.IS_PENDING,
                        0
                );

                resolver.update(
                        uri,
                        completed,
                        null,
                        null
                );

                return uri;

            } catch (Exception e) {

                if (uri != null) {

                    try {
                        resolver.delete(
                                uri,
                                null,
                                null
                        );
                    } catch (Exception ignored) {
                    }
                }

                if (e instanceof IOException) {
                    throw (IOException) e;
                }

                throw new IOException(
                        "Could not save PDF.",
                        e
                );
            }
        }

        // Android 9 and below
        File downloads =
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                );

        File folder =
                new File(
                        downloads,
                        "BhashaSetu"
                );

        if (!folder.exists()
                && !folder.mkdirs()) {

            throw new IOException(
                    "Could not create Downloads/BhashaSetu."
            );
        }

        File file =
                new File(
                        folder,
                        fileName
                );

        try (
                OutputStream outputStream =
                        new FileOutputStream(file)
        ) {

            document.writeTo(
                    outputStream
            );
        }

        return Uri.fromFile(file);
    }

    // =========================================================
    // PAINT
    // =========================================================

    private Paint createPaint(
            float textSize,
            boolean bold
    ) {

        Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                                | Paint.SUBPIXEL_TEXT_FLAG
                );

        paint.setTextSize(
                textSize
        );

        paint.setTypeface(
                Typeface.create(
                        "sans-serif",
                        bold
                                ? Typeface.BOLD
                                : Typeface.NORMAL
                )
        );

        paint.setColor(
                Color.BLACK
        );

        paint.setDither(true);

        return paint;
    }

    // =========================================================
    // CENTERED TEXT
    // =========================================================

    private void drawCenteredText(
            Canvas canvas,
            String text,
            float centerX,
            float baselineY,
            Paint paint
    ) {

        float width =
                paint.measureText(text);

        canvas.drawText(
                text,
                centerX - (width / 2f),
                baselineY,
                paint
        );
    }

    // =========================================================
    // WRAPPED TEXT
    // =========================================================

    private float drawWrappedText(
            Canvas canvas,
            String text,
            float x,
            float y,
            float maxWidth,
            Paint paint,
            float lineHeight
    ) {

        String[] words =
                text.split("\\s+");

        StringBuilder line =
                new StringBuilder();

        for (String word : words) {

            String testLine;

            if (line.length() == 0) {

                testLine = word;

            } else {

                testLine =
                        line.toString()
                                + " "
                                + word;
            }

            if (
                    paint.measureText(
                            testLine
                    ) > maxWidth
                            && line.length() > 0
            ) {

                canvas.drawText(
                        line.toString(),
                        x,
                        y,
                        paint
                );

                y += lineHeight;

                line =
                        new StringBuilder(
                                word
                        );

            } else {

                line =
                        new StringBuilder(
                                testLine
                        );
            }
        }

        if (line.length() > 0) {

            canvas.drawText(
                    line.toString(),
                    x,
                    y,
                    paint
            );

            y += lineHeight;
        }

        return y;
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    public void close() {

        translationPipeline.close();
    }
}