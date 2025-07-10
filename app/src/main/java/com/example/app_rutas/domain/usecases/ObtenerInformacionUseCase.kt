package com.example.app_rutas.domain.usecases

import com.example.app_rutas.domain.repositories.InformacionRepository
import com.example.app_rutas.model.Informacion

class ObtenerInformacionUseCase(private val repository: InformacionRepository) {
    suspend fun ejecutar(empresaId: Long): Informacion? {
        return repository.obtenerInformacion(empresaId)
    }
}
