package com.example.app_rutas.ui.fragment

import android.graphics.*
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.app_rutas.R
import com.example.app_rutas.infrastructure.repositories.RutaRepositoryImpl
import com.example.app_rutas.model.RutaCompleta
import com.example.app_rutas.ui.adapters.PlacesAutoCompleteAdapter
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class RutaPlanificadaFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var etDestino: AutoCompleteTextView
    private lateinit var leyendaContainer: LinearLayout

    private var marcadorUbicacion: Marker? = null
    private var marcadorDestino: Marker? = null
    private val marcadoresParaderos = mutableListOf<Marker>()
    private val polylines = mutableListOf<Polyline>()

    private val apiKey = "AIzaSyAFpBlDKWCOpGY7MliuGGd8pCThUjXLkbA"
    private val rutaRepository = RutaRepositoryImpl()
    private val ubicacionActual = LatLng(-5.19449, -80.63282)

    private val coloresHue = listOf(
        BitmapDescriptorFactory.HUE_BLUE,
        BitmapDescriptorFactory.HUE_GREEN,
        BitmapDescriptorFactory.HUE_ORANGE,
        BitmapDescriptorFactory.HUE_RED,
        BitmapDescriptorFactory.HUE_CYAN,
        BitmapDescriptorFactory.HUE_MAGENTA
    )

    private var leyendaInicialAgregada = false
    private val TOLERANCIA_METROS = 600.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_ruta_planificada, container, false)
        etDestino = view.findViewById(R.id.etDestino)
        mapView = view.findViewById(R.id.mapView)
        leyendaContainer = view.findViewById(R.id.leyendaContainer)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        if (!Places.isInitialized()) {
            Places.initialize(requireContext().applicationContext, apiKey)
        }

        val placesClient = Places.createClient(requireContext())
        val adapter = PlacesAutoCompleteAdapter(requireContext(), placesClient)
        etDestino.setAdapter(adapter)

        etDestino.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
            val placeId = item?.placeId ?: return@setOnItemClickListener

            val request = FetchPlaceRequest.builder(placeId, listOf(Place.Field.LAT_LNG)).build()
            placesClient.fetchPlace(request)
                .addOnSuccessListener { response ->
                    response.place.latLng?.let { destinoLatLng ->
                        mostrarMarcadorDestino(destinoLatLng)
                        CoroutineScope(Dispatchers.Main).launch {
                            buscarYMostrarCombinacionDeRutas(destinoLatLng)
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "No se pudo obtener ubicación del lugar", Toast.LENGTH_SHORT).show()
                }
        }

        return view
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        agregarIconoUbicacion()
    }

    private fun agregarIconoUbicacion() {
        val icono = BitmapDescriptorFactory.fromBitmap(
            Bitmap.createScaledBitmap(
                BitmapFactory.decodeResource(resources, R.drawable.ic_persona), 100, 100, false
            )
        )
        marcadorUbicacion?.remove()
        marcadorUbicacion = googleMap.addMarker(
            MarkerOptions().position(ubicacionActual).title("Tú (Ubicación simulada)").icon(icono)
        )
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ubicacionActual, 15f))
    }

    private fun mostrarMarcadorDestino(latLng: LatLng) {
        marcadorDestino?.remove()
        marcadorDestino = googleMap.addMarker(MarkerOptions().position(latLng).title("Destino"))
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }

    private suspend fun buscarYMostrarCombinacionDeRutas(destino: LatLng) {
        marcadorDestino?.remove()
        marcadoresParaderos.forEach { it.remove() }
        polylines.forEach { it.remove() }
        marcadoresParaderos.clear()
        polylines.clear()
        leyendaContainer.removeAllViews()
        leyendaInicialAgregada = false

        val rutas = rutaRepository.obtenerRutasCompletas()
        Toast.makeText(requireContext(), "Rutas cargadas: ${rutas.size}", Toast.LENGTH_SHORT).show()

        val rutasDirectas = rutas.filter { ruta ->
            ruta.coordenadas.any { c -> distanciaPorCalle(ubicacionActual, LatLng(c.latitud, c.longitud)) < TOLERANCIA_METROS } &&
                    ruta.coordenadas.any { c -> distanciaPorCalle(destino, LatLng(c.latitud, c.longitud)) < TOLERANCIA_METROS }
        }

        Toast.makeText(requireContext(), "Rutas directas: ${rutasDirectas.size}", Toast.LENGTH_SHORT).show()

        if (rutasDirectas.isNotEmpty()) {
            val ruta = rutasDirectas.first()
            val colorHue = coloresHue[0]
            val colorInt = Color.HSVToColor(floatArrayOf(colorHue, 1f, 1f))

            val coordenadas = ruta.coordenadas.map { LatLng(it.latitud, it.longitud) }
            for (i in 0 until coordenadas.size - 1) {
                val tramos = obtenerPolylineaDesdeGoogle(coordenadas[i], coordenadas[i + 1])
                if (tramos.isEmpty()) {
                    Log.w("Polylinea", "Sin datos entre ${coordenadas[i]} y ${coordenadas[i+1]}")
                }
                polylines.add(googleMap.addPolyline(PolylineOptions().addAll(tramos).width(10f).color(colorInt)))
            }

            ruta.paraderos.forEach { paradero ->
                marcadoresParaderos.add(
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(paradero.latitud, paradero.longitud))
                            .title("${paradero.nombre} (${ruta.empresa.nombre})")
                            .icon(BitmapDescriptorFactory.defaultMarker(colorHue))
                    )!!
                )
            }

            agregarALeyenda(ruta.nombre, ruta.empresa.nombre, colorInt)
            mostrarMarcadorDestino(destino)
            return
        }

        val rutasCercaOrigen = rutas.filter {
            it.coordenadas.any { c -> distanciaPorCalle(ubicacionActual, LatLng(c.latitud, c.longitud)) < TOLERANCIA_METROS }
        }

        Toast.makeText(requireContext(), "Rutas cerca del origen: ${rutasCercaOrigen.size}", Toast.LENGTH_SHORT).show()

        val combinacionesValidas = mutableListOf<Pair<RutaCompleta, RutaCompleta>>()
        val combinacionesAgregadas = mutableSetOf<Pair<Long, Long>>()

        for (ruta1 in rutasCercaOrigen) {
            for (ruta2 in rutas) {
                if (ruta1.id == ruta2.id) continue

                if (combinacionesAgregadas.contains(Pair(ruta1.id, ruta2.id)) ||
                    combinacionesAgregadas.contains(Pair(ruta2.id, ruta1.id))
                ) continue

                val conectadas = ruta1.coordenadas.any { c1 ->
                    ruta2.coordenadas.any { c2 ->
                        distanciaPorCalle(LatLng(c1.latitud, c1.longitud), LatLng(c2.latitud, c2.longitud)) < TOLERANCIA_METROS
                    }
                }

                val cercaDestino = ruta2.coordenadas.any { c ->
                    distanciaPorCalle(destino, LatLng(c.latitud, c.longitud)) < TOLERANCIA_METROS
                }

                if (conectadas && cercaDestino) {
                    combinacionesValidas.add(Pair(ruta1, ruta2))
                    combinacionesAgregadas.add(Pair(ruta1.id, ruta2.id))
                }
            }
        }

        Toast.makeText(requireContext(), "Combinaciones válidas: ${combinacionesValidas.size}", Toast.LENGTH_SHORT).show()

        if (combinacionesValidas.isEmpty()) {
            Toast.makeText(requireContext(), "No se encontraron rutas combinadas válidas", Toast.LENGTH_LONG).show()
            return
        }

        for ((index, pair) in combinacionesValidas.withIndex()) {
            val (ruta1, ruta2) = pair
            val colorHue1 = coloresHue[(index * 2) % coloresHue.size]
            val colorHue2 = coloresHue[(index * 2 + 1) % coloresHue.size]
            val colorInt1 = Color.HSVToColor(floatArrayOf(colorHue1, 1f, 1f))
            val colorInt2 = Color.HSVToColor(floatArrayOf(colorHue2, 1f, 1f))

            listOf(Triple(ruta1, colorHue1, colorInt1), Triple(ruta2, colorHue2, colorInt2)).forEach { (ruta, colorHue, colorInt) ->
                val coordenadas = ruta.coordenadas.map { LatLng(it.latitud, it.longitud) }
                for (i in 0 until coordenadas.size - 1) {
                    val tramos = obtenerPolylineaDesdeGoogle(coordenadas[i], coordenadas[i + 1])
                    if (tramos.isEmpty()) {
                        Log.w("Polylinea", "Sin datos entre ${coordenadas[i]} y ${coordenadas[i+1]}")
                    }
                    polylines.add(googleMap.addPolyline(PolylineOptions().addAll(tramos).width(10f).color(colorInt)))
                }

                ruta.paraderos.forEach { paradero ->
                    marcadoresParaderos.add(
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(paradero.latitud, paradero.longitud))
                                .title("${paradero.nombre} (${ruta.empresa.nombre})")
                                .icon(BitmapDescriptorFactory.defaultMarker(colorHue))
                        )!!
                    )
                }

                agregarALeyenda(ruta.nombre, ruta.empresa.nombre, colorInt)
            }
        }

        mostrarMarcadorDestino(destino)
    }

    private suspend fun distanciaPorCalle(origen: LatLng, destino: LatLng): Double = withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/distancematrix/json?" +
                    "origins=${origen.latitude},${origen.longitude}" +
                    "&destinations=${destino.latitude},${destino.longitude}" +
                    "&mode=walking&key=$apiKey"

            val request = Request.Builder().url(url).build()
            val client = OkHttpClient()
            val response = client.newCall(request).execute()

            val body = response.body?.string() ?: return@withContext Double.MAX_VALUE
            val json = JSONObject(body)
            val rows = json.getJSONArray("rows")
            val elements = rows.getJSONObject(0).getJSONArray("elements")
            val distance = elements.getJSONObject(0).getJSONObject("distance").getDouble("value")
            distance
        } catch (e: Exception) {
            e.printStackTrace()
            Double.MAX_VALUE
        }
    }

    private suspend fun obtenerPolylineaDesdeGoogle(start: LatLng, end: LatLng): List<LatLng> = withContext(Dispatchers.IO) {
        val url = "https://roads.googleapis.com/v1/snapToRoads?path=${start.latitude},${start.longitude}|${end.latitude},${end.longitude}&interpolate=true&key=$apiKey"

        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            if (!response.isSuccessful) return@withContext emptyList()

            val json = JSONObject(body)
            val snappedPoints = json.getJSONArray("snappedPoints")

            val snappedLatLngs = mutableListOf<LatLng>()
            for (i in 0 until snappedPoints.length()) {
                val location = snappedPoints.getJSONObject(i).getJSONObject("location")
                val lat = location.getDouble("latitude")
                val lng = location.getDouble("longitude")
                snappedLatLngs.add(LatLng(lat, lng))
            }

            snappedLatLngs
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun agregarALeyenda(nombreRuta: String, empresa: String, color: Int) {
        val context = requireContext()
        if (!leyendaInicialAgregada) {
            val titulo = TextView(context).apply {
                text = "Leyenda - Información de rutas combinadas"
                setTextColor(Color.BLACK)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            }
            leyendaContainer.addView(titulo)
            leyendaInicialAgregada = true
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 4) }
            gravity = Gravity.CENTER_VERTICAL
        }

        val colorView = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(30, 30).apply { marginEnd = 16 }
            setBackgroundColor(color)
        }

        val textView = TextView(context).apply {
            text = "$nombreRuta - $empresa"
            setTextColor(Color.BLACK)
            textSize = 14f
        }

        container.addView(colorView)
        container.addView(textView)
        leyendaContainer.addView(container)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}

