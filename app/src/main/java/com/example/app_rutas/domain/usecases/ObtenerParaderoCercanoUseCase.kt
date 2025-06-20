package com.example.app_rutas.domain.usecases

import com.example.app_rutas.model.Paradero
import com.example.app_rutas.domain.repositories.ParaderoRepository

class ObtenerParaderoCercanoUseCase(private val repository: ParaderoRepository) {
    suspend operator fun invoke(lat: Double, lng: Double, rutaId: Long): Paradero? {
        return repository.obtenerParaderoCercano(lat, lng, rutaId)
    }

}
