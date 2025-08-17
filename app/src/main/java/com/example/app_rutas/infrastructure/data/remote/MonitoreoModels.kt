package com.example.app_rutas.infrastructure.data.remote

data class BaseResponse<T>(
    val message: String?,
    val status: String?,
    val data: T?
)

data class CountData(val count: Int)

data class MonitoreoEventoDTO(
    val evento: String,
    val resultado: String,          // "OK" | "ERROR"
    val mensaje: String? = null,
    val actividad: String? = null,
    val componente: String? = null,
    val referencia: String? = null,
    val detalles: Map<String, Any?>? = null,
    val usuarioId: String? = null,
    val sesionId: String? = null,
    val appVersion: String? = null,
    val deviceModel: String? = null,
    val osVersion: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Double? = null
)
