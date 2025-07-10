package com.example.app_rutas.domain.repositories

import com.example.app_rutas.model.Informacion

interface InformacionRepository {
    suspend fun obtenerInformacion(empresaId: Long): Informacion?
}
