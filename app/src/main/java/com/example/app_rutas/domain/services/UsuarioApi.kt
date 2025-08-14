package com.example.app_rutas.domain.services

import com.example.app_rutas.domain.entities.UsuarioResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface UsuarioApi {

    @Multipart
    @POST("usuarios/registro")
    suspend fun registrarUsuario(
        @Part("data") dataJson: RequestBody,
        @Part fotoPerfil: MultipartBody.Part,
        @Part dniFrontal: MultipartBody.Part,
        @Part dniPosterior: MultipartBody.Part
    ): Response<UsuarioResponse>
}
