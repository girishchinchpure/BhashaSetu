package com.bhashasetu.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TranslationCacheDao {

    @Query("SELECT * FROM translation_cache WHERE sourceHash = :hash AND targetLang = :lang LIMIT 1")
    TranslationCacheEntity findByHash(String hash, String lang);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TranslationCacheEntity entity);

    @Update
    void update(TranslationCacheEntity entity);

    @Query("SELECT * FROM translation_cache ORDER BY createdAt DESC LIMIT :limit")
    List<TranslationCacheEntity> recent(int limit);

    @Query("SELECT COUNT(*) FROM translation_cache WHERE createdAt >= :sinceMillis")
    int countSince(long sinceMillis);

    @Query("DELETE FROM translation_cache")
    void clearAll();
}
