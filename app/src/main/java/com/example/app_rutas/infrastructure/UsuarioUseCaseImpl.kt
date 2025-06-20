package com.example.app_rutas.infrastructure
import android.net.Uri
import com.example.app_rutas.domain.entities.UsuarioRequest
import com.example.app_rutas.domain.repositories.UsuarioRepository
import com.example.app_rutas.domain.usecases.UsuarioUseCase

class UsuarioUseCaseImpl(private val repository: UsuarioRepository) : UsuarioUseCase {
    override suspend fun login(dni: String, contraseña: String): UsuarioRequest? {
        return repository.login(dni, contraseña)
    }

    override suspend fun registrar(usuario: UsuarioRequest, fotoUri: Uri, dniFrontalUri: Uri, dniReversoUri: Uri): Boolean {
        return repository.registrar(usuario, fotoUri, dniFrontalUri, dniReversoUri)
    }


}

