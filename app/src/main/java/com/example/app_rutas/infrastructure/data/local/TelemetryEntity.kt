package com.example.app_rutas.infrastructure.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry_queue")
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tryCount: Int = 0
)
