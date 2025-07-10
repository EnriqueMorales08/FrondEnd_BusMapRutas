package com.example.app_rutas.model

data class RutaCompleta(
    val id: Long,
    val nombre: String,
    val empresa: Empresa,
    val coordenadas: List<Coordenada>,
    val paraderos: List<Paradero> // todos los paraderos de la ruta
)
