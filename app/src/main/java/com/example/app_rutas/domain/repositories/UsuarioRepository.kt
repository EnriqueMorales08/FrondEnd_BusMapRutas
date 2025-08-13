package com.example.app_rutas.domain.repositories

import okhttp3.MultipartBody
import okhttp3.RequestBody

interface UsuarioRepository {

    suspend fun login(dni: String, contraseña: String): com.example.app_rutas.domain.entities.UsuarioRequest?

    suspend fun registrar(
        usuario: com.example.app_rutas.domain.entities.UsuarioRequest,
        fotoPerfilUri: android.net.Uri,
        dniFrenteUri: android.net.Uri,
        dniReversoUri: android.net.Uri
    ): Boolean

    // NUEVO: Método compatible con Multipart
    suspend fun registrarUsuarioConMultipart(
        nombre: RequestBody,
        correo: RequestBody,
        celular: RequestBody,
        password: RequestBody,
        dni: RequestBody,
        estado: RequestBody,
        fotoPerfil: MultipartBody.Part,
        dniFrontal: MultipartBody.Part,
        dniPosterior: MultipartBody.Part
    ): Boolean
}

