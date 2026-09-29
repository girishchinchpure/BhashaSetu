package com.bhashasetu.app.engine;

import android.content.Context;

import com.bhashasetu.app.database.AppDatabase;
import com.bhashasetu.app.database.CorrectionLogEntity;

import java.util.concurrent.Executors;

public class CorrectionLogger {

    private final AppDatabase db;

    public CorrectionLogger(Context context) {
        db = AppDatabase.getInstance(context);
    }

    public void log(String sourceText, String producedTranslation, String targetLang,
                     String modelUsed, boolean markedCorrect) {
        Executors.newSingleThreadExecutor().execute(() -> {
            CorrectionLogEntity entity = new CorrectionLogEntity();
            entity.sourceText = sourceText;
            entity.producedTranslation = producedTranslation;
            entity.correctedTranslation = null;
            entity.targetLang = targetLang;
            entity.modelUsed = modelUsed;
            entity.markedCorrect = markedCorrect;
            entity.timestamp = System.currentTimeMillis();
            entity.uploaded = false;
            db.correctionLogDao().insert(entity);
        });
    }
}
