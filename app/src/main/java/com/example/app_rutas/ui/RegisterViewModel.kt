package com.example.app_rutas.ui

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_rutas.domain.entities.UsuarioResponse
import com.example.app_rutas.domain.repositories.UsuarioRepository
import com.example.app_rutas.infrastructure.repositories.UsuarioRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val repo: UsuarioRepository = UsuarioRepositoryImpl()
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _resultado = MutableStateFlow<Result<UsuarioResponse>?>(null)
    val resultado: StateFlow<Result<UsuarioResponse>?> = _resultado

    fun registrar(
        dni: String,
        nombre: String,
        correo: String,
        celular: String,
        password: String,
        fotoPerfil: Uri,
        dniFrontal: Uri,
        dniPosterior: Uri,
        contentResolver: ContentResolver
    ) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val resp = repo.registrarUsuario(
                    dni, nombre, correo, celular, password,
                    fotoPerfil, dniFrontal, dniPosterior, contentResolver
                )
                if (resp.isSuccessful && resp.body() != null) {
                    _resultado.value = Result.success(resp.body()!!)
                } else {
                    val errorBody = resp.errorBody()?.string()
                    Log.e(
                        "RegisterViewModel",
                        """
                    Error al registrar usuario.
                    HTTP ${resp.code()} - ${resp.message()}
                    url = ${resp.raw().request.url}
                    body = $errorBody
                    """.trimIndent()
                                )

                                _resultado.value = Result.failure(
                                    RuntimeException("HTTP ${resp.code()} - $errorBody")
                                )
                }
            } catch (e: Exception) {
                _resultado.value = Result.failure(e)
            } finally {
                _loading.value = false
            }
        }
    }
}
