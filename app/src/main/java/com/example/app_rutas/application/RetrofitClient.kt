package com.example.app_rutas.application

import com.example.app_rutas.domain.services.UsuarioApi
import com.example.app_rutas.domain.services.NotificationsApi
import com.example.app_rutas.domain.services.AdminApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "http://143.198.176.30:9004/api/"
    private const val BASE_URL_LOGIN = "http://143.198.176.30:9003/api/"
    private const val BASE_URL_NOTIF     = "http://143.198.176.30:9004/api/"

    //Registro
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val usuarioApi: UsuarioApi by lazy { retrofit.create(UsuarioApi::class.java) }

    //Login
    val usuarioApiLogin: UsuarioApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_LOGIN)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UsuarioApi::class.java)
    }

    // ---------- Notificaciones ----------
    val notificationsApi: NotificationsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_NOTIF)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NotificationsApi::class.java)
    }

    val adminApi: AdminApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_NOTIF)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AdminApi::class.java)
    }
}
