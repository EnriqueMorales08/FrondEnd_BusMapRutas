package com.example.app_rutas.infrastructure.data.remote

import android.os.Build
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import android.content.Context
import android.content.pm.PackageManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface MonitoreoApi {

    @POST("monitoreo/batch")
    suspend fun batch(@Body dtos: List<MonitoreoEventoDTO>): Response<BaseResponse<CountData>>

    companion object {
        private const val BASE = "http://137.184.200.155:9090/api/"

        fun create(context: Context): MonitoreoApi {
            val appVersion = try {
                val pm = context.packageManager
                val pn = context.packageName
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pn, PackageManager.PackageInfoFlags.of(0)).versionName
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pn, 0).versionName
                } ?: "unknown"
            } catch (e: Exception) { "unknown" }

            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val osVersion = "Android ${Build.VERSION.RELEASE}"

            val headers = Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("X-App-Version", appVersion)
                    .addHeader("X-Device-Model", deviceModel)
                    .addHeader("X-OS-Version", osVersion)
                    .build()
                chain.proceed(req)
            }

            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(headers)
                .addInterceptor(logger)
                .build()

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
                .create(MonitoreoApi::class.java)
        }
    }
}
