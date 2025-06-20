package com.example.app_rutas.infrastructure.repositories

import android.net.Uri
import android.util.Log
import com.example.app_rutas.App
import com.example.app_rutas.application.RetrofitClient
import com.example.app_rutas.domain.entities.UsuarioRequest
import com.example.app_rutas.domain.repositories.UsuarioRepository
import com.example.app_rutas.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody


class UsuarioRepositoryImpl : UsuarioRepository {

    override suspend fun login(dni: String, contraseña: String): UsuarioRequest? {
        return withContext(Dispatchers.IO) {
            val response = RetrofitClient.usuarioApi.login(
                mapOf("dni" to dni, "contraseña" to contraseña)
            )
            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }
        }
    }
    override suspend fun registrar(usuario: UsuarioRequest, fotoUri: Uri, dniFrontalUri: Uri, dniReversoUri: Uri): Boolean {
        return try {
            val context = App.instance.applicationContext

            val fileFoto = FileUtils.getFileFromUri(context, fotoUri)
            val fileFrontal = FileUtils.getFileFromUri(context, dniFrontalUri)
            val fileReverso = FileUtils.getFileFromUri(context, dniReversoUri)

            // Verifica que existan los archivos
            Log.d("RegistroDebug", "Foto: ${fileFoto?.absolutePath}, existe: ${fileFoto?.exists()}")
            Log.d("RegistroDebug", "DNI Frontal: ${fileFrontal?.absolutePath}, existe: ${fileFrontal?.exists()}")
            Log.d("RegistroDebug", "DNI Reverso: ${fileReverso?.absolutePath}, existe: ${fileReverso?.exists()}")

            val nombreBody = usuario.nombre.toRequestBody("text/plain".toMediaType())
            val correoBody = usuario.correo.toRequestBody("text/plain".toMediaType())
            val celularBody = usuario.celular.toRequestBody("text/plain".toMediaType())
            val passwordBody = usuario.password.toRequestBody("text/plain".toMediaType())
            val dniBody = usuario.dni.toRequestBody("text/plain".toMediaType())

            val fotoPart = MultipartBody.Part.createFormData("fotoPerfil", fileFoto.name, fileFoto.asRequestBody("image/*".toMediaType()))
            val frontalPart = MultipartBody.Part.createFormData("dniFrontal", fileFrontal.name, fileFrontal.asRequestBody("image/*".toMediaType()))
            val reversoPart = MultipartBody.Part.createFormData("dniPosterior", fileReverso.name, fileReverso.asRequestBody("image/*".toMediaType()))

            val response = RetrofitClient.usuarioApi.registrarUsuario(
                nombreBody, correoBody, celularBody, passwordBody, dniBody,
                fotoPart, frontalPart, reversoPart
            )

            if (!response.isSuccessful) {
                Log.e("RegistroError", "Código de error: ${response.code()}")
                Log.e("RegistroError", "Mensaje: ${response.message()}")
                Log.e("RegistroError", "Cuerpo de error: ${response.errorBody()?.string()}")
            }

            response.isSuccessful
        } catch (e: Exception) {
            Log.e("RegistroError", "Excepción al registrar usuario", e)
            false
        }
    }



}









