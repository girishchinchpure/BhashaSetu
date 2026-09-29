package com.bhashasetu.app.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "correction_log")
public class CorrectionLogEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String sourceText;
    public String producedTranslation;
    public String correctedTranslation; // null if just flagged wrong, no correction typed
    public String targetLang;
    public String modelUsed;
    public boolean markedCorrect; // true = teacher tapped ✅, false = ❌
    public long timestamp;
    public boolean uploaded;
}
