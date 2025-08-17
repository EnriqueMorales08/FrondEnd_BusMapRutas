package com.example.app_rutas

import android.app.Application
import com.example.app_rutas.infrastructure.worker.FlushTelemetryWorker

class App : Application() {
    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        // Envío periódico cada 1 hora
        //FlushTelemetryWorker.enqueuePeriodicHourly(this)
        // Primer flush inmediato (opcional)
        //FlushTelemetryWorker.enqueueOneShot(this)
        instance = this
    }
}
