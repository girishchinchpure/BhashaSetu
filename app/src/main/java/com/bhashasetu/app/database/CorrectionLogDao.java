package com.bhashasetu.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CorrectionLogDao {

    @Insert
    long insert(CorrectionLogEntity entity);

    @Update
    void update(CorrectionLogEntity entity);

    @Query("SELECT * FROM correction_log WHERE uploaded = 0")
    List<CorrectionLogEntity> getUnuploaded();

    @Query("SELECT COUNT(*) FROM correction_log")
    int countAll();
}
