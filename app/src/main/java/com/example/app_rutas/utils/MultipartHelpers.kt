package com.example.app_rutas.utils

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.IOException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

object MultipartHelpers {

    // Crea el part "data" con content-type application/json
    fun buildDataJsonPart(dni: String, nombre: String, correo: String, celular: String, password: String): RequestBody {
        val json = JSONObject().apply {
            put("dni", dni)
            put("nombre", nombre)
            put("correo", correo)
            put("celular", celular)
            put("password", password)
        }.toString()

        return RequestBody.create("application/json".toMediaType(), json)
    }

    // Crea un MultipartBody.Part desde un Uri (galería)
    fun buildImagePart(
        partName: String,
        uri: Uri,
        contentResolver: ContentResolver,
        defaultFileName: String
    ): MultipartBody.Part {
        val mime = contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = readAllBytes(contentResolver.openInputStream(uri)!!)
        val body = RequestBody.create(mime.toMediaTypeOrNull(), bytes)
        val fileName = guessFilename(uri, defaultFileName)
        return MultipartBody.Part.createFormData(partName, fileName, body)
    }

    private fun readAllBytes(input: InputStream): ByteArray {
        input.use { i ->
            val buffer = ByteArrayOutputStream()
            val tmp = ByteArray(8 * 1024)
            while (true) {
                val n = i.read(tmp)
                if (n < 0) break
                buffer.write(tmp, 0, n)
            }
            return buffer.toByteArray()
        }
    }

    private fun guessFilename(uri: Uri, fallback: String): String {
        // Si quieres, intenta obtener display name con query a MediaStore,
        // aquí devolvemos un fallback seguro.
        return fallback
    }
}
