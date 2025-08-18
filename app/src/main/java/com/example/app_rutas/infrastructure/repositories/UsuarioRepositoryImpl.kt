package com.example.app_rutas.infrastructure.repositories

import android.content.ContentResolver
import android.net.Uri
import com.example.app_rutas.App
import com.example.app_rutas.application.RetrofitClient
import com.example.app_rutas.domain.entities.UsuarioRequest
import com.example.app_rutas.domain.entities.UsuarioResponse
import com.example.app_rutas.domain.repositories.UsuarioRepository
import com.example.app_rutas.utils.MultipartHelpers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response

class UsuarioRepositoryImpl : UsuarioRepository {

    override suspend fun registrarUsuario(
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
        // Crear JSON como string y convertirlo a RequestBody
        val json = """
            {
              "dni": "$dni",
              "nombre": "$nombre",
              "correo": "$correo",
              "celular": "$celular",
              "password": "$password"
            }
        """.trimIndent()

        val jsonBody = RequestBody.create("application/json".toMediaTypeOrNull(), json)

        // Crear partes de archivo con ayuda de tu helper
        val partFoto  = MultipartHelpers.buildImagePart("fotoPerfil", fotoPerfilUri, cr, "fotoPerfil.jpg")
        val partFront = MultipartHelpers.buildImagePart("dniFrontal", dniFrontalUri, cr, "dniFrontal.jpg")
        val partBack  = MultipartHelpers.buildImagePart("dniPosterior", dniPosteriorUri, cr, "dniPosterior.jpg")

        return RetrofitClient.usuarioApi.registrarUsuario(
            dataJson = jsonBody,
            fotoPerfil = partFoto,
            dniFrontal = partFront,
            dniPosterior = partBack
        )
    }
}









