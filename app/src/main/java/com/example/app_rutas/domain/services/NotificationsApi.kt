package com.example.app_rutas.domain.services

import com.example.app_rutas.domain.entities.Notificacion
import retrofit2.Response
import retrofit2.http.*

interface NotificationsApi {
    @GET("notificaciones")
    suspend fun listAll(): Response<List<Notificacion>>

    @GET("notificaciones/usuario/{userId}")
    suspend fun listByUser(@Path("userId") userId: String): Response<List<Notificacion>>

    @PUT("notificaciones/usuario/{userId}")
    suspend fun updateByUser(
        @Path("userId") userId: String,
        @Body body: Notificacion
    ): Response<String>
}
