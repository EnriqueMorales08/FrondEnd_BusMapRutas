package com.example.app_rutas.model


data class Ruta(
    val id: Long,
    val nombre: String,
    val empresa: Empresa
) {
    override fun toString(): String {
        return "${empresa.nombre} : ${nombre}"
    }
}
