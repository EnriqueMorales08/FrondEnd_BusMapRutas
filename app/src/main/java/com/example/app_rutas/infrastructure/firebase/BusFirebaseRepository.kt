package com.example.app_rutas.infrastructure.firebase

import android.util.Log
import com.example.app_rutas.model.BusPosFirebase
import com.google.firebase.database.*

class BusFirebaseRepository(
    private var defaultPath: String = "ubicacion" // nodo por defecto
) {
    private var ref: DatabaseReference? = null
    private var listener: ValueEventListener? = null

    fun startListening(
        onUpdate: (BusPosFirebase?) -> Unit,
        onError: ((String) -> Unit)? = null,
        pathOverride: String? = null
    ) {
        stopListening()

        val path = pathOverride ?: defaultPath
        ref = FirebaseDatabase.getInstance().getReference(path)

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitud").value?.toString()?.toDoubleOrNull()
                val lng = snapshot.child("longitud").value?.toString()?.toDoubleOrNull()
                val vel = snapshot.child("velocidad").value?.toString()?.toDoubleOrNull()
                Log.d("BusRepo", "update path=$path lat=$lat lng=$lng vel=$vel")
                onUpdate(BusPosFirebase(latitud = lat, longitud = lng, velocidad = vel))
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BusRepo", "Firebase cancelado: ${error.message}")
                onError?.invoke(error.message)
                onUpdate(null)
            }
        }

        ref?.addValueEventListener(listener as ValueEventListener)
    }

    fun stopListening() {
        listener?.let { ref?.removeEventListener(it) }
        listener = null
        ref = null
    }
}
