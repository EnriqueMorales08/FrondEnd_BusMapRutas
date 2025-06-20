package com.example.app_rutas.domain.entities


data class UsuarioResponse(
    val id: Int,
    val nombre: String,
    val correo: String,
    val celular: String,
    val rol: String,
    val estado: String,
    val fotoUrl: String
)
