package com.example.app_rutas.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_rutas.domain.entities.UsuarioRequest
import com.example.app_rutas.domain.repositories.UsuarioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(private val usuarioRepository: UsuarioRepository) : ViewModel() {

    private val _registroExitoso = MutableStateFlow<Boolean?>(null)
    val registroExitoso: StateFlow<Boolean?> = _registroExitoso

    fun registrarUsuario(usuario: UsuarioRequest, fotoUri: Uri, dniFrontalUri: Uri, dniReversoUri: Uri) {
        viewModelScope.launch {
            val resultado = usuarioRepository.registrar(usuario, fotoUri, dniFrontalUri, dniReversoUri)
            _registroExitoso.value = resultado
        }
    }

}

