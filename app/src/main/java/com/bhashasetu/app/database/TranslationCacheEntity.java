package com.bhashasetu.app.database;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "translation_cache",
        indices = {@Index(value = {"sourceHash", "targetLang"}, unique = true)})
public class TranslationCacheEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String sourceText;
    public String sourceHash;
    public String targetLang;
    public String translatedText;
    public String audioFilePath;
    public float confidence;
    public String modelUsed;
    public long createdAt;
    public int useCount;
    public boolean flaggedForReview;
}
