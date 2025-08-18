package com.example.app_rutas.domain.entities

data class UsuarioRequest(
    val dni: String,
    val nombre: String,
    val correo: String,
    val celular: String,
    val password: String
)
