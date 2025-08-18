package com.example.app_rutas.infrastructure.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TelemetryEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun telemetryDao(): TelemetryDao
}
