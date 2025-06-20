package com.example.app_rutas.domain.repositories

import com.example.app_rutas.model.Paradero

interface ParaderoRepository {
    suspend fun obtenerParaderoCercano(lat: Double, lng: Double, rutaId: Long): Paradero?

}