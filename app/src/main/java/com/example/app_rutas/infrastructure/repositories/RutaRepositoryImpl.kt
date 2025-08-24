
package com.example.app_rutas.infrastructure.repositories

import com.example.app_rutas.domain.repositories.RutaRepository
import com.example.app_rutas.model.Coordenada
import com.example.app_rutas.model.Empresa
import com.example.app_rutas.model.Ruta
import com.example.app_rutas.model.RutaCompleta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class RutaRepositoryImpl : RutaRepository {

    private val client = OkHttpClient()
    private val backendUrl = "http://192.168.101.9:8080/api/rutas"

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

    suspend fun obtenerRutasCompletas(): List<RutaCompleta> {
        val rutasBasicas = obtenerRutas()
        return rutasBasicas.map { ruta ->
            val coordenadas = obtenerCoordenadasDeRuta(ruta.id)
            val paraderoRepo = ParaderoRepositoryImpl()
            val paraderos = paraderoRepo.obtenerTodosLosParaderosDeRuta(ruta.id)
            RutaCompleta(
                id = ruta.id,
                nombre = ruta.nombre,
                empresa = ruta.empresa,
                coordenadas = coordenadas,
                paraderos = paraderos
            )
        }
    }

    override suspend fun obtenerRutas(): List<Ruta> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(backendUrl).build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val jsonArray = JSONArray(body)

            val lista = mutableListOf<Ruta>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getLong("id")
                val nombre = obj.getString("nombre")

                val empresaJson: JSONObject = obj.getJSONObject("empresa")
                val empresa = Empresa(
                    id = empresaJson.getLong("id"),
                    nombre = empresaJson.getString("nombre")
                )

                lista.add(Ruta(id, nombre, empresa))
            }
            lista
        } catch (e: Exception) {
            emptyList()
        }
    }
}
