package com.example.app_rutas.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_rutas.domain.repositories.UsuarioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody

class RegisterViewModel(private val usuarioRepository: UsuarioRepository) : ViewModel() {

    private val _registroExitoso = MutableStateFlow<Boolean?>(null)
    val registroExitoso: StateFlow<Boolean?> = _registroExitoso

    fun registrarUsuario(
        nombre: RequestBody,
        correo: RequestBody,
        celular: RequestBody,
        password: RequestBody,
        dni: RequestBody,
        estado: RequestBody,
        fotoPerfil: MultipartBody.Part,
        dniFrontal: MultipartBody.Part,
        dniPosterior: MultipartBody.Part
    ) {
        viewModelScope.launch {
            try {
                val resultado = usuarioRepository.registrarUsuarioConMultipart(
                    nombre,
                    correo,
                    celular,
                    password,
                    dni,
                    estado,
                    fotoPerfil,
                    dniFrontal,
                    dniPosterior
                )
                Log.d("RegisterViewModel", "Resultado del registro: $resultado")
                _registroExitoso.value = resultado
            } catch (e: Exception) {
                Log.e("RegisterViewModel", "Error al registrar usuario", e)
                _registroExitoso.value = false
            }
        }
    }
}



