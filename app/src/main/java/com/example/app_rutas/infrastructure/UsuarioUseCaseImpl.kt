package com.example.app_rutas.infrastructure

import android.net.Uri
import com.example.app_rutas.App
import com.example.app_rutas.domain.entities.UsuarioRequest
import com.example.app_rutas.domain.entities.UsuarioResponse
import com.example.app_rutas.domain.repositories.UsuarioRepository
import com.example.app_rutas.domain.usecases.UsuarioUseCase
import retrofit2.Response

class UsuarioUseCaseImpl(
    private val repository: UsuarioRepository
) : UsuarioUseCase {

    override suspend fun registrar(
        usuario: UsuarioRequest,
        fotoUri: Uri,
        dniFrontalUri: Uri,
        dniReversoUri: Uri
    ): Response<UsuarioResponse> {
        return repository.registrarUsuario(
            dni = usuario.dni,
            nombre = usuario.nombre,
            correo = usuario.correo,
            celular = usuario.celular,
            password = usuario.password,
            fotoPerfilUri = fotoUri,
            dniFrontalUri = dniFrontalUri,
            dniPosteriorUri = dniReversoUri,
            cr = App.instance.contentResolver // o pásalo como parámetro
        )
    }
}
