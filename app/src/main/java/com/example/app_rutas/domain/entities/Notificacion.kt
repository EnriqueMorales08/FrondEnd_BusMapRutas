package com.example.app_rutas.domain.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Notificacion(
    val id: Long? = null,
    val title: String? = null,
    val message: String? = null,
    val type: String? = null,
    val isRead: Boolean? = null,
    val createdAt: String? = null,
    val userId: String? = null
): Parcelable
