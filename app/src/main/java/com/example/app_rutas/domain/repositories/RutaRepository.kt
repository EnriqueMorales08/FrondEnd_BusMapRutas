package com.example.app_rutas.domain.repositories

import com.example.app_rutas.model.Coordenada
import com.example.app_rutas.model.Ruta

interface RutaRepository {
    suspend fun obtenerCoordenadasDeRuta(rutaId: Long): List<Coordenada>
    suspend fun obtenerRutas(): List<Ruta>
}