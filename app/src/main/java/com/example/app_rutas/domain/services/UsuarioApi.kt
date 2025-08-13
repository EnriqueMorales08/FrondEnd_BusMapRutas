package com.example.app_rutas.domain.services

import com.example.app_rutas.domain.entities.UsuarioResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface UsuarioApi {

    @POST("usuarios/login")
    suspend fun login(@Body credenciales: Map<String, String>): Response<UsuarioResponse>

    @Multipart
    @POST("usuarios/registro")
    suspend fun registrarUsuario(
        @Part("data") data: RequestBody,
        @Part fotoPerfil: MultipartBody.Part?,
        @Part dniFrontal: MultipartBody.Part?,
        @Part dniPosterior: MultipartBody.Part?
    ): Response<UsuarioResponse>

}
