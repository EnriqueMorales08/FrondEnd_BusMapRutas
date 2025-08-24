package com.example.app_rutas.ui.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.app_rutas.R
import com.example.app_rutas.infrastructure.repositories.RutaRepositoryImpl
import com.example.app_rutas.model.RutaCompleta
import com.example.app_rutas.ui.adapters.PlacesAutoCompleteAdapter
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.*

class RutaPlanificadaFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var etDestino: AutoCompleteTextView
    private lateinit var leyendaContainer: LinearLayout

    // Google Map
    private lateinit var googleMap: GoogleMap
    private var mapReady = false

    // Ubicación en tiempo real
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var ultimaUbicacion: Location? = null

    private var marcadorUbicacion: Marker? = null
    private var marcadorDestino: Marker? = null
    private val marcadoresParaderos = mutableListOf<Marker>()
    private val polylines = mutableListOf<Polyline>()

    // TODO: mueve la API key a BuildConfig/NDK para producción
    private val apiKey = "AIzaSyAFpBlDKWCOpGY7MliuGGd8pCThUjXLkbA"
    private val rutaRepository = RutaRepositoryImpl()

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
    private val PREFILTRO_HAVERSINE_METROS = 1500.0
    private val DIST_OCULTAR_EN_PARADERO_METROS = 10.0

    private val REQUEST_LOCATION_PERMISSIONS = 1001

    // OPTIMIZACIONES
    private val http by lazy { OkHttpClient() }
    private val networkGate = Semaphore(4)

    // Cachés
    private val cacheDistancia = mutableMapOf<String, Double>()
    private val cacheSnap = mutableMapOf<String, List<LatLng>>()

    // Patrón punteado para caminar
    private val walkPattern: List<PatternItem> = listOf(Dash(20f), Gap(15f))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_ruta_planificada, container, false)
        etDestino = view.findViewById(R.id.etDestino)
        mapView = view.findViewById(R.id.mapView)
        leyendaContainer = view.findViewById(R.id.leyendaContainer)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // Places
        if (!Places.isInitialized()) {
            Places.initialize(requireContext().applicationContext, apiKey)
        }
        val placesClient = com.google.android.libraries.places.api.Places.createClient(requireContext())
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
                        viewLifecycleOwner.lifecycleScope.launch {
                            buscarYMostrarCombinacionDeRutas(destinoLatLng)
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "No se pudo obtener ubicación del lugar", Toast.LENGTH_SHORT).show()
                }
        }

        // Fused Location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        prepararLocationCallback()

        return view
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapReady = true
        habilitarMiUbicacionEnMapaSiPermiso()
    }

    // UBICACIÓN EN TIEMPO REAL
    private fun prepararLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                ultimaUbicacion = loc
                val latLng = LatLng(loc.latitude, loc.longitude)
                actualizarMarcadorUbicacion(latLng)

                // Ocultar si estás “sobre” un paradero
                viewLifecycleOwner.lifecycleScope.launch {
                    val cerca = estaCercaDeCualquierParadero(latLng, DIST_OCULTAR_EN_PARADERO_METROS)
                    if (cerca) {
                        marcadorUbicacion?.isVisible = false
                        limpiarPolylines()
                    } else {
                        marcadorUbicacion?.isVisible = true
                    }
                }
            }
        }
    }

    private fun crearLocationRequest(): LocationRequest {
        return LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).setMinUpdateIntervalMillis(2000L)
            .setWaitForAccurateLocation(false)
            .build()
    }

    private fun habilitarMiUbicacionEnMapaSiPermiso() {
        if (!mapReady) return
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION_PERMISSIONS
            )
            return
        }
        googleMap.isMyLocationEnabled = true
        iniciarActualizacionesUbicacion()
    }

    private fun iniciarActualizacionesUbicacion() {
        if (!::fusedLocationClient.isInitialized || locationCallback == null) return
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.requestLocationUpdates(
            crearLocationRequest(),
            locationCallback as LocationCallback,
            requireActivity().mainLooper
        )

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                ultimaUbicacion = it
                val here = LatLng(it.latitude, it.longitude)
                actualizarMarcadorUbicacion(here)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(here, 15f))
            }
        }
    }

    private fun detenerActualizacionesUbicacion() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    private fun actualizarMarcadorUbicacion(latLng: LatLng) {
        val icono = BitmapDescriptorFactory.fromBitmap(
            Bitmap.createScaledBitmap(
                BitmapFactory.decodeResource(resources, R.drawable.ic_persona), 100, 100, false
            )
        )
        if (marcadorUbicacion == null) {
            marcadorUbicacion = googleMap.addMarker(
                MarkerOptions().position(latLng).title("Tú").icon(icono)
            )
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        } else {
            marcadorUbicacion?.position = latLng
        }
    }

    // LÓGICA DE RUTAS (directa → combinada)
    private suspend fun buscarYMostrarCombinacionDeRutas(destino: LatLng) = withContext(Dispatchers.Main) {
        limpiarTodoMenosMiUbicacion()

        val origenLatLng = ultimaUbicacion?.let { LatLng(it.latitude, it.longitude) }
        if (origenLatLng == null) {
            Toast.makeText(requireContext(), "No tengo tu ubicación aún. Activa GPS o espera unos segundos.", Toast.LENGTH_LONG).show()
            mostrarMarcadorDestino(destino)
            return@withContext
        }

        val rutas = withContext(Dispatchers.IO) { rutaRepository.obtenerRutasCompletas() }
        Toast.makeText(requireContext(), "Rutas cargadas: ${rutas.size}", Toast.LENGTH_SHORT).show()

        // DIRECTA
        val rutasDirectas = withContext(Dispatchers.IO) {
            rutas.filter { ruta ->
                rutaCercaDePuntoPorCalle(ruta, origenLatLng) &&
                        rutaCercaDePuntoPorCalle(ruta, destino)
            }
        }

        if (rutasDirectas.isNotEmpty()) {
            val ruta = rutasDirectas.first()
            val colorHue = coloresHue[0]
            val colorInt = Color.HSVToColor(floatArrayOf(colorHue, 1f, 1f))

            val (idxOrigen, ptOrigen) = nearestIndexOnRuta(ruta, origenLatLng)
            val (idxDestino, ptDestino) = nearestIndexOnRuta(ruta, destino)
            val tramo = subListOrdered(ruta.coordenadas.map { LatLng(it.latitud, it.longitud) }, idxOrigen, idxDestino)

            dibujarTramoSnap(tramo, colorInt)
            agregarALeyenda(ruta.nombre, ruta.empresa.nombre, colorInt)
            dibujarCaminoAPie(origenLatLng, ptOrigen)
            dibujarCaminoAPie(ptDestino, destino)

            mostrarMarcadorDestino(destino)
            ajustarCamara(listOf(origenLatLng, destino, ptOrigen, ptDestino) + tramo)
            return@withContext
        }

        // COMBINADA (A, B)
        val rutasCercaOrigen = withContext(Dispatchers.IO) {
            rutas.filter { ruta -> rutaCercaDePuntoPorCalle(ruta, origenLatLng) }
        }

        val combinacionesValidas = withContext(Dispatchers.IO) {
            val combos = mutableListOf<Pair<RutaCompleta, RutaCompleta>>()
            val usados = mutableSetOf<Pair<Long, Long>>()

            for (ruta1 in rutasCercaOrigen) {
                for (ruta2 in rutas) {
                    if (ruta1.id == ruta2.id) continue
                    val p1 = ruta1.id to ruta2.id
                    val p2 = ruta2.id to ruta1.id
                    if (usados.contains(p1) || usados.contains(p2)) continue

                    if (rutasConectadasPorCalle(ruta1, ruta2) && rutaCercaDePuntoPorCalle(ruta2, destino)) {
                        combos.add(ruta1 to ruta2)
                        usados.add(p1)
                    }
                }
            }
            combos
        }

        if (combinacionesValidas.isEmpty()) {
            Toast.makeText(requireContext(), "No se encontraron rutas combinadas válidas", Toast.LENGTH_LONG).show()
            mostrarMarcadorDestino(destino)
            return@withContext
        }

        val boundsPoints = mutableListOf<LatLng>()
        boundsPoints += origenLatLng
        boundsPoints += destino

        combinacionesValidas.forEachIndexed { index, (ruta1, ruta2) ->
            val colorHue1 = coloresHue[(index * 2) % coloresHue.size]
            val colorHue2 = coloresHue[(index * 2 + 1) % coloresHue.size]
            val colorInt1 = Color.HSVToColor(floatArrayOf(colorHue1, 1f, 1f))
            val colorInt2 = Color.HSVToColor(floatArrayOf(colorHue2, 1f, 1f))

            viewLifecycleOwner.lifecycleScope.launch {
                val (idxA, ptA) = nearestIndexOnRuta(ruta1, origenLatLng)
                val transfer = bestTransferBetween(ruta1, ruta2)
                val (idxB, ptB) = nearestIndexOnRuta(ruta2, destino)
                if (transfer == null) return@launch
                val (idxTransA, idxTransB, ptTrans) = transfer

                val listA = ruta1.coordenadas.map { LatLng(it.latitud, it.longitud) }
                val listB = ruta2.coordenadas.map { LatLng(it.latitud, it.longitud) }

                val tramoA = subListOrdered(listA, idxA, idxTransA)
                val tramoB = subListOrdered(listB, idxTransB, idxB)

                dibujarTramoSnap(tramoA, colorInt1)
                agregarALeyenda(ruta1.nombre, ruta1.empresa.nombre, colorInt1)

                dibujarTramoSnap(tramoB, colorInt2)
                agregarALeyenda(ruta2.nombre, ruta2.empresa.nombre, colorInt2)

                dibujarCaminoAPie(origenLatLng, ptA)
                dibujarCaminoAPie(ptTrans, listA[idxTransA])
                dibujarCaminoAPie(listB[idxTransB], ptTrans)
                dibujarCaminoAPie(listB[idxB], destino)

                pintarParaderosRuta(ruta1, colorHue1)
                pintarParaderosRuta(ruta2, colorHue2)

                boundsPoints += listOf(ptA, ptTrans, ptB) + tramoA + tramoB
            }
        }

        mostrarMarcadorDestino(destino)
        viewLifecycleOwner.lifecycleScope.launch {
            delay(300)
            ajustarCamara(boundsPoints)
        }
    }

    private fun limpiarTodoMenosMiUbicacion() {
        marcadorDestino?.remove()
        marcadoresParaderos.forEach { it.remove() }
        polylines.forEach { it.remove() }
        marcadoresParaderos.clear()
        polylines.clear()
        leyendaContainer.removeAllViews()
        leyendaInicialAgregada = false
    }

    private fun limpiarPolylines() {
        polylines.forEach { it.remove() }
        polylines.clear()
    }

    // CERCANÍA
    private suspend fun rutaCercaDePuntoPorCalle(ruta: RutaCompleta, punto: LatLng): Boolean {
        val candidatos = ruta.coordenadas.filter {
            haversineMeters(punto.latitude, punto.longitude, it.latitud, it.longitud) <= PREFILTRO_HAVERSINE_METROS
        }
        if (candidatos.isEmpty()) return false

        for (c in candidatos) {
            val d = distanciaPorCalle(punto, LatLng(c.latitud, c.longitud))
            if (d < TOLERANCIA_METROS) return true
        }
        return false
    }

    private suspend fun rutasConectadasPorCalle(r1: RutaCompleta, r2: RutaCompleta): Boolean {
        val candidatos1 = r1.coordenadas
        val candidatos2 = r2.coordenadas

        val step1 = max(1, candidatos1.size / 200)
        val step2 = max(1, candidatos2.size / 200)

        for (i in candidatos1.indices step step1) {
            val c1 = candidatos1[i]
            for (j in candidatos2.indices step step2) {
                val c2 = candidatos2[j]
                val geo = haversineMeters(c1.latitud, c1.longitud, c2.latitud, c2.longitud)
                if (geo <= PREFILTRO_HAVERSINE_METROS) {
                    val d = distanciaPorCalle(LatLng(c1.latitud, c1.longitud), LatLng(c2.latitud, c2.longitud))
                    if (d < TOLERANCIA_METROS) return true
                }
            }
        }
        return false
    }

    // DIBUJO
    private suspend fun dibujarTramoSnap(tramo: List<LatLng>, colorInt: Int) {
        if (tramo.size < 2) return

        val stride = max(1, tramo.size / 60)
        val pares = mutableListOf<Pair<LatLng, LatLng>>()
        var i = 0
        while (i < tramo.size - 1) {
            val j = min(tramo.size - 1, i + stride)
            pares.add(tramo[i] to tramo[j])
            i = j
        }

        val tramos = withContext(Dispatchers.IO) {
            pares.map { (a, b) ->
                async {
                    val key = snapKey(a, b)
                    cacheSnap[key] ?: networkGate.withPermit {
                        val puntosSnap = obtenerPolylineaDesdeGoogle(a, b)
                        cacheSnap[key] = puntosSnap
                        puntosSnap
                    }
                }
            }.awaitAll().flatten()
        }

        withContext(Dispatchers.Main) {
            if (tramos.isNotEmpty()) {
                polylines.add(
                    googleMap.addPolyline(
                        PolylineOptions()
                            .addAll(tramos)
                            .width(10f)
                            .color(colorInt)
                    )
                )
            }
        }
    }

    private fun dibujarCaminoAPie(a: LatLng, b: LatLng) {
        polylines.add(
            googleMap.addPolyline(
                PolylineOptions()
                    .add(a, b)
                    .width(6f)
                    .pattern(walkPattern)
                    .color(Color.DKGRAY)
            )
        )
    }

    private fun pintarParaderosRuta(ruta: RutaCompleta, colorHue: Float) {
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
    }

    // DISTANCIAS / APIS
    private suspend fun distanciaPorCalle(origen: LatLng, destino: LatLng): Double = withContext(Dispatchers.IO) {
        val key = distKey(origen, destino)
        cacheDistancia[key]?.let { return@withContext it }

        val url = "https://maps.googleapis.com/maps/api/distancematrix/json?" +
                "origins=${origen.latitude},${origen.longitude}" +
                "&destinations=${destino.latitude},${destino.longitude}" +
                "&mode=walking&key=$apiKey"

        return@withContext networkGate.withPermit {
            try {
                val request = Request.Builder().url(url).build()
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use Double.MAX_VALUE
                    val json = JSONObject(body)
                    val elements = json.getJSONArray("rows")
                        .getJSONObject(0)
                        .getJSONArray("elements")
                    val element0 = elements.getJSONObject(0)
                    val status = element0.optString("status", "ZERO_RESULTS")
                    val value = if (status == "OK") element0.getJSONObject("distance").getDouble("value") else Double.MAX_VALUE
                    cacheDistancia[key] = value
                    return@use value
                }
            } catch (_: Exception) {
                Double.MAX_VALUE
            }
        }
    }

    private suspend fun obtenerPolylineaDesdeGoogle(start: LatLng, end: LatLng): List<LatLng> = withContext(Dispatchers.IO) {
        val url = "https://roads.googleapis.com/v1/snapToRoads?path=${start.latitude},${start.longitude}|${end.latitude},${end.longitude}&interpolate=true&key=$apiKey"
        try {
            val request = Request.Builder().url(url).build()
            networkGate.withPermit {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<LatLng>()
                    val body = response.body?.string() ?: return@use emptyList<LatLng>()
                    val json = JSONObject(body)
                    val snappedPoints = json.optJSONArray("snappedPoints") ?: return@use emptyList<LatLng>()
                    val snappedLatLngs = ArrayList<LatLng>(snappedPoints.length())
                    for (i in 0 until snappedPoints.length()) {
                        val location = snappedPoints.getJSONObject(i).getJSONObject("location")
                        val lat = location.getDouble("latitude")
                        val lng = location.getDouble("longitude")
                        snappedLatLngs.add(LatLng(lat, lng))
                    }
                    return@use snappedLatLngs
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // GEO HELPERS
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return 2 * R * asin(sqrt(a))
    }

    private suspend fun nearestIndexOnRuta(ruta: RutaCompleta, p: LatLng): Pair<Int, LatLng> {
        val list = ruta.coordenadas.map { LatLng(it.latitud, it.longitud) }
        var bestIdx = 0
        var bestGeo = Double.MAX_VALUE
        for (i in list.indices) {
            val g = haversineMeters(p.latitude, p.longitude, list[i].latitude, list[i].longitude)
            if (g < bestGeo) {
                bestGeo = g
                bestIdx = i
            }
        }
        return bestIdx to list[bestIdx]
    }

    private suspend fun bestTransferBetween(r1: RutaCompleta, r2: RutaCompleta): Triple<Int, Int, LatLng>? {
        val list1 = r1.coordenadas.map { LatLng(it.latitud, it.longitud) }
        val list2 = r2.coordenadas.map { LatLng(it.latitud, it.longitud) }
        var best: Triple<Int, Int, LatLng>? = null
        var bestCost = Double.MAX_VALUE

        val step1 = max(1, list1.size / 200)
        val step2 = max(1, list2.size / 200)

        for (i in list1.indices step step1) {
            val a = list1[i]
            for (j in list2.indices step step2) {
                val b = list2[j]
                val geo = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
                if (geo <= PREFILTRO_HAVERSINE_METROS) {
                    val d = distanciaPorCalle(a, b)
                    if (d < bestCost && d < TOLERANCIA_METROS) {
                        bestCost = d
                        val mid = LatLng((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)
                        best = Triple(i, j, mid)
                    }
                }
            }
        }
        return best
    }

    private fun subListOrdered(list: List<LatLng>, i: Int, j: Int): List<LatLng> {
        return if (i <= j) list.subList(i, j + 1) else list.subList(j, i + 1)
    }

    private suspend fun estaCercaDeCualquierParadero(p: LatLng, umbralMetros: Double): Boolean = withContext(Dispatchers.Default) {
        marcadoresParaderos.any { m ->
            haversineMeters(p.latitude, p.longitude, m.position.latitude, m.position.longitude) <= umbralMetros
        }
    }

    private fun mostrarMarcadorDestino(latLng: LatLng) {
        marcadorDestino?.remove()
        marcadorDestino = googleMap.addMarker(MarkerOptions().position(latLng).title("Destino"))
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
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

        val texto = "$nombreRuta - $empresa"
        for (i in 0 until leyendaContainer.childCount) {
            val child = leyendaContainer.getChildAt(i)
            if (child is LinearLayout) {
                val tv = child.getChildAt(1) as? TextView
                if (tv?.text?.toString() == texto) return
            }
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
            text = texto
            setTextColor(Color.BLACK)
            textSize = 14f
        }

        container.addView(colorView)
        container.addView(textView)
        leyendaContainer.addView(container)
    }

    private fun ajustarCamara(points: List<LatLng>) {
        val valid = points.filterNotNull()
        if (valid.isEmpty()) return
        val builder = LatLngBounds.Builder()
        valid.forEach { builder.include(it) }
        val bounds = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }

    // Ciclo de vida
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (mapReady) {
            habilitarMiUbicacionEnMapaSiPermiso()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        detenerActualizacionesUbicacion()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        detenerActualizacionesUbicacion()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    // Permisos
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                if (mapReady) habilitarMiUbicacionEnMapaSiPermiso()
            } else {
                Toast.makeText(requireContext(), "Permisos de ubicación denegados", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Keys de caché
    private fun distKey(a: LatLng, b: LatLng) = "${a.latitude},${a.longitude}|${b.latitude},${b.longitude}"
    private fun snapKey(a: LatLng, b: LatLng) = "snap:${a.latitude},${a.longitude}|${b.latitude},${b.longitude}"
}
