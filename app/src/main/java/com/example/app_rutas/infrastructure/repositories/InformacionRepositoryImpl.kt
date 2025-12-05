package com.example.app_rutas.infrastructure.repositories

import com.example.app_rutas.domain.repositories.InformacionRepository
import com.example.app_rutas.model.Informacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class InformacionRepositoryImpl : InformacionRepository {

    private val client = OkHttpClient()
    private val backendUrl = "http://143.198.176.30:9003/api/informacion"

    override suspend fun obtenerInformacion(empresaId: Long): Informacion? = withContext(Dispatchers.IO) {
        val url = "$backendUrl/$empresaId"
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyString)

            return@withContext Informacion(
                numeroUnidades = json.getInt("numeroUnidades"),
                duracionRecorrido = json.getString("duracionRecorrido"),
                longitudRecorrido = json.getDouble("longitudRecorrido"),
                inicioServicioLunesViernes = json.getString("inicioServicioLunesViernes"),
                inicioServicioSabado = json.getString("inicioServicioSabado"),
                inicioServicioDomingo = json.getString("inicioServicioDomingo"),
                finServicioLunesViernes = json.getString("finServicioLunesViernes"),
                finServicioSabado = json.getString("finServicioSabado"),
                finServicioDomingo = json.getString("finServicioDomingo"),
                mensaje = json.getString("mensaje")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
