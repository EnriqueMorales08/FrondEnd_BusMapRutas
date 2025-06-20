package com.example.app_rutas.domain.usecases

import com.example.app_rutas.domain.repositories.RutaRepository  // Usa la interfaz, no la implementación
import com.example.app_rutas.model.Coordenada

class ObtenerRutaUseCase(private val repository: RutaRepository) {
    suspend fun ejecutar(rutaId: Long): List<Coordenada> {
        return repository.obtenerCoordenadasDeRuta(rutaId)
    }
}
