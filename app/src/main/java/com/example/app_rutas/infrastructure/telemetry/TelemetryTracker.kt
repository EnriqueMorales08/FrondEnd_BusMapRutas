package com.example.app_rutas.infrastructure.telemetry

import android.content.Context
import com.example.app_rutas.infrastructure.data.TelemetryRepository
import com.example.app_rutas.infrastructure.data.remote.MonitoreoApi
import com.example.app_rutas.infrastructure.data.remote.MonitoreoEventoDTO
import com.example.app_rutas.infrastructure.worker.FlushTelemetryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class TelemetryTracker private constructor(private val ctx: Context) {

    private val api = MonitoreoApi.create(ctx)
    private val repo = TelemetryRepository(ctx, api)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs = ctx.getSharedPreferences("telemetry", Context.MODE_PRIVATE)

    private val THRESHOLD = 25

    fun currentSession(): String =
        prefs.getString("session_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("session_id", it).apply() }

    fun newSession(): String =
        UUID.randomUUID().toString().also { prefs.edit().putString("session_id", it).apply() }

    fun screenView(activity: String, usuarioId: String?) {
        log(
            MonitoreoEventoDTO(
                evento = "SCREEN_VIEW",
                resultado = "OK",
                actividad = activity,
                usuarioId = usuarioId,
                sesionId = currentSession()
            )
        )
    }

    fun buttonClick(activity: String, componente: String, usuarioId: String?, detalles: Map<String, Any?>? = null) {
        log(
            MonitoreoEventoDTO(
                evento = "BUTTON_CLICK",
                resultado = "OK",
                actividad = activity,
                componente = componente,
                detalles = detalles,
                usuarioId = usuarioId,
                sesionId = currentSession()
            )
        )
    }

    fun error(activity: String, componente: String?, mensaje: String, usuarioId: String?) {
        log(
            MonitoreoEventoDTO(
                evento = "APP_ERROR",
                resultado = "ERROR",
                mensaje = mensaje,
                actividad = activity,
                componente = componente,
                usuarioId = usuarioId,
                sesionId = currentSession()
            )
        )
    }

    private fun log(dto: MonitoreoEventoDTO) {
        scope.launch {
            repo.enqueue(dto)
            // dispara un envío único si se superó el umbral
            val size = repo.countQueue()
            if (size >= THRESHOLD) {
                FlushTelemetryWorker.enqueueOneShot(ctx)
            }
        }
    }

    companion object {
        @Volatile private var INSTANCE: TelemetryTracker? = null
        fun get(context: Context): TelemetryTracker =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TelemetryTracker(context.applicationContext).also { INSTANCE = it }
            }
    }
}
