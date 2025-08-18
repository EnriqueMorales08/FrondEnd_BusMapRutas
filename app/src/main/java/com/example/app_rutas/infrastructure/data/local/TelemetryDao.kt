package com.example.app_rutas.infrastructure.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TelemetryDao {
    @Insert
    suspend fun insert(e: TelemetryEntity): Long

    @Query("SELECT * FROM telemetry_queue ORDER BY id ASC LIMIT :limit")
    suspend fun take(limit: Int): List<TelemetryEntity>

    @Query("DELETE FROM telemetry_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE telemetry_queue SET tryCount = tryCount + 1 WHERE id IN (:ids)")
    suspend fun incTries(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM telemetry_queue")
    suspend fun count(): Long
}
