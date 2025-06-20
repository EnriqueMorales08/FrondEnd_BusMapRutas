package com.example.app_rutas.infrastructure.repositories

import com.example.app_rutas.domain.repositories.RutaRepository
import com.example.app_rutas.model.Coordenada
import com.example.app_rutas.model.Ruta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class RutaRepositoryImpl : RutaRepository {

    private val client = OkHttpClient()
    private val backendUrl = "http://192.168.101.14:8080/api/rutas"  // Ajusta IP si cambia

    override suspend fun obtenerCoordenadasDeRuta(rutaId: Long): List<Coordenada> = withContext(Dispatchers.IO) {
        val url = "$backendUrl/$rutaId/coordenadas"
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val jsonArray = JSONArray(body)

            val lista = mutableListOf<Coordenada>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val lat = obj.getDouble("latitud")
                val lng = obj.getDouble("longitud")
                lista.add(Coordenada(lat, lng))
            }
            lista
        } catch (e: Exception) {
            emptyList()
        }
    }


    override suspend fun obtenerRutas(): List<Ruta> = withContext(Dispatchers.IO) {
        val url = "$backendUrl"
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val jsonArray = JSONArray(body)

            val lista = mutableListOf<Ruta>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getLong("id")
                val nombre = obj.getString("nombre")
                lista.add(Ruta(id, nombre))
            }
            lista
        } catch (e: Exception) {
            emptyList()
        }
    }

}
