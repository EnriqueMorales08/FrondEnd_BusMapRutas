package com.example.app_rutas.utils

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.ByteArrayOutputStream

fun ContentResolver.getFileName(uri: Uri): String {
    val returnCursor = query(uri, null, null, null, null)
    returnCursor?.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        it.moveToFirst()
        return it.getString(nameIndex)
    }
    return "archivo.jpg"
}

fun ContentResolver.readBytes(uri: Uri): ByteArray {
    val inputStream = openInputStream(uri) ?: return ByteArray(0)
    val buffer = ByteArrayOutputStream()
    val data = ByteArray(1024)
    var nRead: Int
    while (inputStream.read(data, 0, data.size).also { nRead = it } != -1) {
        buffer.write(data, 0, nRead)
    }
    return buffer.toByteArray()
}

fun ContentResolver.toImagePart(partName: String, uri: Uri?): MultipartBody.Part? {
    if (uri == null) return null
    val mimeType = getType(uri) ?: "image/jpeg"
    val bytes = readBytes(uri)
    val requestFile = RequestBody.create(mimeType.toMediaTypeOrNull(), bytes)
    val fileName = getFileName(uri)
    return MultipartBody.Part.createFormData(partName, fileName, requestFile)
}
