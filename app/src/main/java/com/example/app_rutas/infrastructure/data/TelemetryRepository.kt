package com.example.app_rutas.infrastructure.data

import android.content.Context
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.app_rutas.infrastructure.data.local.AppDb
import com.example.app_rutas.infrastructure.data.local.TelemetryEntity
import com.example.app_rutas.infrastructure.data.remote.MonitoreoApi
import com.example.app_rutas.infrastructure.data.remote.MonitoreoEventoDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TelemetryRepository(
    context: Context,
    private val api: MonitoreoApi
) {
    private val db = Room.databaseBuilder(context, AppDb::class.java, "app.db").build()
    private val dao = db.telemetryDao()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(MonitoreoEventoDTO::class.java)

    suspend fun enqueue(dto: MonitoreoEventoDTO) {
        val json = adapter.toJson(dto)
        dao.insert(TelemetryEntity(payloadJson = json))
    }

    suspend fun countQueue(): Long = dao.count()

    suspend fun flush(maxBatch: Int = 50): Boolean = withContext(Dispatchers.IO) {
        val rows = dao.take(maxBatch)
        if (rows.isEmpty()) return@withContext true

        val dtos = rows.map { adapter.fromJson(it.payloadJson)!! }
        val ids = rows.map { it.id }

        try {
            val r = api.batch(dtos)
            if (r.isSuccessful) {
                dao.deleteByIds(ids)
                true
            } else {
                dao.incTries(ids)
                false
            }
        } catch (_: Exception) {
            dao.incTries(ids)
            false
        }
    }
}
