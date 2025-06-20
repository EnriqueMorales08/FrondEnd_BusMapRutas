package com.example.app_rutas.infrastructure.repositories

import com.example.app_rutas.model.Paradero
import com.example.app_rutas.domain.repositories.ParaderoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ParaderoRepositoryImpl : ParaderoRepository {

    private val client = OkHttpClient()
    private val backendUrl = "http://192.168.101.14:8080/api/paraderos/cercano"

    override suspend fun obtenerParaderoCercano(lat: Double, lng: Double, rutaId: Long): Paradero? = withContext(Dispatchers.IO) {
        val url = "$backendUrl?lat=$lat&lng=$lng&rutaId=$rutaId"
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: return@withContext null)
            return@withContext Paradero(
                nombre = json.getString("nombre"),
                latitud = json.getDouble("latitud"),
                longitud = json.getDouble("longitud")
            )
        } catch (e: Exception) {
            null
        }
    }
}
