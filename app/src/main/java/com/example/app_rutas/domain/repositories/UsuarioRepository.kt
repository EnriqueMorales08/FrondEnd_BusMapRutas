package com.example.app_rutas.domain.repositories

import android.content.ContentResolver
import android.net.Uri
import com.example.app_rutas.application.RetrofitClient
import com.example.app_rutas.domain.entities.UsuarioRequest
import com.example.app_rutas.domain.entities.UsuarioResponse
import com.example.app_rutas.utils.MultipartHelpers
import retrofit2.Response

interface UsuarioRepository {
    suspend fun registrarUsuario(
        dni: String,
        nombre: String,
        correo: String,
        celular: String,
        password: String,
        fotoPerfilUri: Uri,
        dniFrontalUri: Uri,
        dniPosteriorUri: Uri,
        cr: ContentResolver
    ): Response<UsuarioResponse> {
        val dataJson = MultipartHelpers.buildDataJsonPart(dni, nombre, correo, celular, password)
        val partFoto  = MultipartHelpers.buildImagePart("fotoPerfil",   fotoPerfilUri, cr,   "fotoPerfil.jpg")
        val partFront = MultipartHelpers.buildImagePart("dniFrontal",    dniFrontalUri, cr,   "dniFrontal.jpg")
        val partBack  = MultipartHelpers.buildImagePart("dniPosterior",  dniPosteriorUri, cr, "dniPosterior.jpg")

        return RetrofitClient.usuarioApi.registrarUsuario(
            dataJson, partFoto, partFront, partBack
        )
    }
}
