package com.example.app_rutas.infrastructure.worker

import android.content.Context
import androidx.work.*
import com.example.app_rutas.infrastructure.data.TelemetryRepository
import com.example.app_rutas.infrastructure.data.remote.MonitoreoApi

class FlushTelemetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val repo by lazy { TelemetryRepository(applicationContext, MonitoreoApi.create(applicationContext)) }

    override suspend fun doWork(): Result {
        val ok = repo.flush()
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "telemetry-hourly"

        fun enqueuePeriodicHourly(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val work = PeriodicWorkRequestBuilder<FlushTelemetryWorker>(
                1, java.util.concurrent.TimeUnit.HOURS,
                15, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10, java.util.concurrent.TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }

        fun enqueueOneShot(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val one = OneTimeWorkRequestBuilder<FlushTelemetryWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(one)
        }
    }
}
