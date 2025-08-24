package com.example.app_rutas.domain.services

import com.example.app_rutas.model.EstadoServicio
import retrofit2.http.GET

interface AdminApi {
    @GET("estado-servicio")
    suspend fun getEstadoServicio(): EstadoServicio
}
