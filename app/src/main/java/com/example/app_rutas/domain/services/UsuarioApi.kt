package com.example.app_rutas.domain.services

import com.example.app_rutas.domain.entities.UsuarioRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface UsuarioApi {

    @POST("usuarios/login")
    suspend fun login(@Body credenciales: Map<String, String>): Response<UsuarioRequest>

    @Multipart
    @POST("usuarios/registrar")
    suspend fun registrarUsuario(
        @Part("nombre") nombre: RequestBody,
        @Part("correo") correo: RequestBody,
        @Part("celular") celular: RequestBody,
        @Part("password") password: RequestBody,
        @Part("dni") dni: RequestBody,
        @Part("estado") estado: RequestBody,
        @Part fotoPerfil: MultipartBody.Part,
        @Part dniFrontal: MultipartBody.Part,
        @Part dniPosterior: MultipartBody.Part
    ): Response<Void>

}
