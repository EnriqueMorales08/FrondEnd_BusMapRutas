package com.example.app_rutas.domain.usecases

import android.net.Uri
import com.example.app_rutas.domain.entities.UsuarioRequest
import com.example.app_rutas.domain.entities.UsuarioResponse
import retrofit2.Response

interface UsuarioUseCase {
    suspend fun registrar(
        usuario: UsuarioRequest,
        fotoUri: Uri,
        dniFrontalUri: Uri,
        dniReversoUri: Uri
    ): Response<UsuarioResponse>
}
