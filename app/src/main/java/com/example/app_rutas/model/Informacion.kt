package com.example.app_rutas.model

data class Informacion(
    val numeroUnidades: Int,
    val duracionRecorrido: String,
    val longitudRecorrido: Double,
    val inicioServicioLunesViernes: String,
    val inicioServicioSabado: String,
    val inicioServicioDomingo: String,
    val finServicioLunesViernes: String,
    val finServicioSabado: String,
    val finServicioDomingo: String,
    val mensaje: String
)