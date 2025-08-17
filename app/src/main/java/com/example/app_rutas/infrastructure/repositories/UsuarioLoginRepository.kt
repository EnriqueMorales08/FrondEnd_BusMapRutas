package com.example.app_rutas.infrastructure.repositories

import com.example.app_rutas.application.RetrofitClient
import com.example.app_rutas.domain.entities.UsuarioRemoto

class UsuarioLoginRepository {
    suspend fun listarUsuarios(): List<UsuarioRemoto> =
        RetrofitClient.usuarioApiLogin.listarUsuariosRemotos()
}
