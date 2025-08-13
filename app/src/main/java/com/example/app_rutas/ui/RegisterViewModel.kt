package com.example.app_rutas.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_rutas.application.RetrofitClient
import com.example.app_rutas.domain.entities.UsuarioRequest
import com.example.app_rutas.domain.entities.UsuarioResponse
import com.example.app_rutas.utils.toImagePart
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody

class RegisterViewModel : ViewModel() {

    private val _registroExitoso = MutableStateFlow<UsuarioResponse?>(null)
    val registroExitoso = _registroExitoso.asStateFlow()

    fun registrarUsuario(
        usuario: UsuarioRequest,
        fotoPerfilUri: Uri,
        dniFrontalUri: Uri,
        dniPosteriorUri: Uri,
        contentResolver: ContentResolver
    ) {
        viewModelScope.launch {
            try {
                // JSON como RequestBody
                val json = Gson().toJson(usuario)
                val dataBody = RequestBody.create(
                    "application/json; charset=utf-8".toMediaType(),
                    json
                )

                // Partes de imagen
                val partPerfil = contentResolver.toImagePart("fotoPerfil", fotoPerfilUri)
                val partFrontal = contentResolver.toImagePart("dniFrontal", dniFrontalUri)
                val partPosterior = contentResolver.toImagePart("dniPosterior", dniPosteriorUri)

                val resp = RetrofitClient.usuarioApi.registrarUsuario(
                    data = dataBody,
                    fotoPerfil = partPerfil,
                    dniFrontal = partFrontal,
                    dniPosterior = partPosterior
                )

                if (resp.isSuccessful) {
                    _registroExitoso.value = resp.body()
                } else {
                    _registroExitoso.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _registroExitoso.value = null
            }
        }
    }
}
