package com.example.app_rutas.domain.entities

data class UsuarioRemoto(
    val id: Long,
    val dni: String,
    val nombre: String,
    val correo: String,
    val celular: String,
    val estado: Boolean,
    val fotoPerfil: String?,
    val dniPosterior: String?,
    val dniFrontal: String?,
    val password: String,         // hash bcrypt
    val fechaRegistro: String?,
    val fechaValidacion: String?
)
