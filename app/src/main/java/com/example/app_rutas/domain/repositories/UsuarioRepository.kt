
package com.example.app_rutas.domain.repositories

import android.net.Uri
import com.example.app_rutas.domain.entities.UsuarioRequest

interface UsuarioRepository {
    suspend fun login(dni: String, contraseña: String): UsuarioRequest?

    // Nueva firma con 3 imágenes
    suspend fun registrar(usuario: UsuarioRequest, fotoPerfilUri: Uri, dniFrenteUri: Uri, dniReversoUri: Uri): Boolean
}

