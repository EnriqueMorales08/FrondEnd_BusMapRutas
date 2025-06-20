package com.example.app_rutas.domain.usecases

import android.net.Uri
import com.example.app_rutas.domain.entities.UsuarioRequest

interface UsuarioUseCase {
    suspend fun login(dni: String, contraseña: String): UsuarioRequest?
    suspend fun registrar(usuario: UsuarioRequest, fotoUri: Uri, dniFrontalUri: Uri, dniReversoUri: Uri): Boolean

}
