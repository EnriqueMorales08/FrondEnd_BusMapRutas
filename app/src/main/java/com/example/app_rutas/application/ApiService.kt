package com.example.app_rutas.application

import com.example.app_rutas.domain.entities.UsuarioRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {


        @POST("usuarios/login")
        suspend fun login(@Body dniData: Map<String, String>): Response<UsuarioRequest>

        @POST("usuarios/registrar")
        suspend fun registrarUsuario(@Body usuario: UsuarioRequest): Response<UsuarioRequest>


}

