package com.example.app_rutas.domain.repositories

import com.example.app_rutas.application.RetrofitClient
import com.example.app_rutas.domain.entities.Notificacion

class NotificationsRepository {
    suspend fun listAll(): List<Notificacion> =
        RetrofitClient.notificationsApi.listAll().let { r ->
            if (!r.isSuccessful) error("HTTP ${r.code()}") else r.body().orEmpty()
        }

    suspend fun listByUserId(userId: String): List<Notificacion> =
        RetrofitClient.notificationsApi.listByUser(userId).let { r ->
            if (!r.isSuccessful) error("HTTP ${r.code()}") else r.body().orEmpty()
        }

    suspend fun listForUserIncludingGlobal(dni: String?): List<Notificacion> {
        val all = listAll()
        val filtered = if (dni.isNullOrBlank()) {
            all
        } else {
            all.filter { it.userId.isNullOrBlank() || it.userId == dni }
        }
        return filtered.sortedByDescending { it.createdAt ?: "" }
    }

    suspend fun markAllRead(userId: String) {
        val resp = RetrofitClient.notificationsApi.updateByUser(userId, Notificacion(isRead = true))
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
    }
}
