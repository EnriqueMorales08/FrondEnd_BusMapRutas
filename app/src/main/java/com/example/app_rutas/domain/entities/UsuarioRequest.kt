package com.example.app_rutas.domain.entities

data class UsuarioRequest(
    val nombre: String,
    val correo: String,
    val celular: String,
    val password: String,
    val fotoPerfil: String,
    val dni: String,
    val dniFrontal: String = "",
    val dniPosterior: String = ""
)
