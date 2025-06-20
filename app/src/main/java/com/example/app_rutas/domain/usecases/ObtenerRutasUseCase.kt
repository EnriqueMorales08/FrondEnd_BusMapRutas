package com.example.app_rutas.domain.usecases

import com.example.app_rutas.domain.repositories.RutaRepository

import com.example.app_rutas.model.Ruta

class ObtenerRutasUseCase(private val repository: RutaRepository) {
    suspend fun ejecutar(): List<Ruta> {
        return repository.obtenerRutas()
    }
}
