package com.example.app_rutas.ui.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.app_rutas.R
import com.example.app_rutas.infrastructure.repositories.RutaRepositoryImpl
import com.example.app_rutas.infrastructure.telemetry.TelemetryTracker
import com.example.app_rutas.model.RutaCompleta
import com.example.app_rutas.ui.activity.MainActivity
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

    // -------------------- UI --------------------
    private lateinit var mapView: MapView
    private lateinit var etDestino: AutoCompleteTextView
    private lateinit var leyendaContainer: LinearLayout
    private lateinit var googleMap: GoogleMap
    private var mapReady = false

    // -------------------- Ubicación --------------------
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var ultimaUbicacion: Location? = null
    private var marcadorUbicacion: Marker? = null
    private var marcadorDestino: Marker? = null

    // -------------------- Datos / dibujo --------------------
    private val rutaRepository = RutaRepositoryImpl()
    private val marcadoresParaderos = mutableListOf<Marker>()
    private val polylines = mutableListOf<Polyline>()
    private val coloresHue = listOf(
        BitmapDescriptorFactory.HUE_BLUE,
        BitmapDescriptorFactory.HUE_GREEN,
        BitmapDescriptorFactory.HUE_ORANGE,
        BitmapDescriptorFactory.HUE_RED,
        BitmapDescriptorFactory.HUE_CYAN,
        BitmapDescriptorFactory.HUE_MAGENTA
    )

    // -------------------- Config --------------------
    private val REQUEST_LOCATION_PERMISSIONS = 1001
    private val apiKey = "AIzaSyAFpBlDKWCOpGY7MliuGGd8pCThUjXLkbA"

    // Umbrales del algoritmo
    private val PREFILTRO_HAVERSINE_METROS = 1500.0  // filtro rápido
    private val TOLERANCIA_METROS = 300.0            // confirma cercanía “por calle”
    private val DIST_OCULTAR_EN_PARADERO_METROS = 10.0

    // Red / control de cuotas
    private val HTTP_TIMEOUT_MS = 5000L
    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(HTTP_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .connectTimeout(HTTP_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(HTTP_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(HTTP_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
    }
    private val networkGate = Semaphore(4)

    // Cachés
    private val cacheDistancia = mutableMapOf<String, Double>()
    private val cacheSnap = mutableMapOf<String, List<LatLng>>()

    // NUEVO: caché de rutas caminando (Directions)
    private val cacheWalking = mutableMapOf<String, List<LatLng>>()

    // Patrón punteado para “a pie”
    private val walkPattern: List<PatternItem> = listOf(Dash(20f), Gap(15f))

    // -------------------- Flags de prueba --------------------
    // Usa sólo Haversine (sin Distance Matrix ni Roads) para garantizar que pinte.
    // NOTA: Este flag NO afecta al trazado peatonal; a pie siempre intenta por calles reales (Directions).
    private val USE_ONLY_HAVERSINE = true

    // Simular tu ubicación para pruebas
    private val USE_SIMULATED_LOCATION = false
    private val SIM_LAT = -5.174843185366898
    private val SIM_LNG =  -80.69098676969931

    // Para conservar tu diseño de la leyenda definido en XML
    private var leyendaBaseChildren = 0
    private var leyendaInicialAgregada = true // si el XML ya tiene título, déjalo así

    private val tracker by lazy { TelemetryTracker.get(requireContext()) }
    private fun currentUserId(): String? =
        requireContext().getSharedPreferences("rutas_prefs", Context.MODE_PRIVATE)
            .getLong("userId", -1L).takeIf { it > 0 }?.toString()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_ruta_planificada, container, false)

        view.findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            tracker.buttonClick("RutaPlanificadaFragment", "btnMenu", currentUserId())
            (activity as? MainActivity)?.toggleDrawer()
        }

        etDestino = view.findViewById(R.id.etDestino)
        mapView = view.findViewById(R.id.mapView)
        leyendaContainer = view.findViewById(R.id.leyendaContainer)
        leyendaBaseChildren = leyendaContainer.childCount

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // Places Autocomplete (solo al seleccionar)
        if (!Places.isInitialized()) {
            Places.initialize(requireContext().applicationContext, apiKey)
        }
        val placesClient = com.google.android.libraries.places.api.Places.createClient(requireContext())
        val adapter = PlacesAutoCompleteAdapter(requireContext(), placesClient)
        etDestino.setAdapter(adapter)

        etDestino.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
            val placeId = item?.placeId ?: return@setOnItemClickListener
            tracker.buttonClick(
                "RutaPlanificadaFragment",
                "etDestino_select",
                currentUserId(),
                detalles = mapOf("placeId" to placeId)
            )
            val request = FetchPlaceRequest.builder(placeId, listOf(Place.Field.LAT_LNG)).build()
            placesClient.fetchPlace(request)
                .addOnSuccessListener { response ->
                    response.place.latLng?.let { destinoLatLng ->
                        Toast.makeText(requireContext(), "Destino seleccionado", Toast.LENGTH_SHORT).show()
                        mostrarMarcadorDestino(destinoLatLng)
                        viewLifecycleOwner.lifecycleScope.launch {
                            buscarYMostrarCombinacionDeRutas(destinoLatLng)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    tracker.error(
                        "RutaPlanificadaFragment",
                        "fetchPlace",
                        e.message ?: "No se pudo obtener ubicación del lugar",
                        currentUserId()
                    )
                    Toast.makeText(requireContext(), "No se pudo obtener ubicación del lugar", Toast.LENGTH_SHORT).show()
                }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        prepararLocationCallback()
        return view
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapReady = true
        habilitarMiUbicacionEnMapaSiPermiso()
    }

    // -------------------- Ubicación --------------------
    private fun prepararLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (USE_SIMULATED_LOCATION) return
                val loc = result.lastLocation ?: return
                ultimaUbicacion = loc
                val latLng = LatLng(loc.latitude, loc.longitude)
                actualizarMarcadorUbicacion(latLng)

                viewLifecycleOwner.lifecycleScope.launch {
                    val cerca = estaCercaDeCualquierParadero(latLng, DIST_OCULTAR_EN_PARADERO_METROS)
                    if (cerca) {
                        marcadorUbicacion?.isVisible = false
                        limpiarPolylines()
                    } else marcadorUbicacion?.isVisible = true
                }
            }
        }
    }

    private fun crearLocationRequest(): LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setWaitForAccurateLocation(false)
            .build()

    private fun habilitarMiUbicacionEnMapaSiPermiso() {
        if (!mapReady) return

        if (!USE_SIMULATED_LOCATION) {
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
        } else {
            googleMap.isMyLocationEnabled = false
        }

        iniciarActualizacionesUbicacion()
    }

    private fun iniciarActualizacionesUbicacion() {
        if (USE_SIMULATED_LOCATION) {
            val simulated = Location("simulated").apply {
                latitude = SIM_LAT
                longitude = SIM_LNG
                accuracy = 5f
            }
            ultimaUbicacion = simulated
            val here = LatLng(simulated.latitude, simulated.longitude)
            actualizarMarcadorUbicacion(here)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(here, 15f))
            return
        }

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
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_persona)
        val scaled: Bitmap = Bitmap.createScaledBitmap(bmp, 70, 70, false) // tamaño cómodo
        val icono = BitmapDescriptorFactory.fromBitmap(scaled)
        if (marcadorUbicacion == null) {
            marcadorUbicacion = googleMap.addMarker(
                MarkerOptions().position(latLng).title("Tú").icon(icono)
            )
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        } else {
            marcadorUbicacion?.position = latLng
        }
    }

    // -------------------- Algoritmo: Directa → Combinada --------------------
    private suspend fun buscarYMostrarCombinacionDeRutas(destino: LatLng) = withContext(Dispatchers.Main) {
        limpiarTodoMenosMiUbicacion()

        val origenLatLng = ultimaUbicacion?.let { LatLng(it.latitude, it.longitude) }
        if (origenLatLng == null) {
            Toast.makeText(requireContext(), "Ubicación no disponible aún.", Toast.LENGTH_LONG).show()
            mostrarMarcadorDestino(destino)
            return@withContext
        }

        val rutas = withContext(Dispatchers.IO) { rutaRepository.obtenerRutasCompletas() }
        Toast.makeText(requireContext(), "Rutas=${rutas.size}", Toast.LENGTH_SHORT).show()

        // ---- DIRECTA ----
        val rutasDirectas = withContext(Dispatchers.IO) {
            rutas.filter { r ->
                rutaCercaDePuntoPorCalle(r, origenLatLng) && rutaCercaDePuntoPorCalle(r, destino)
            }
        }
        Toast.makeText(requireContext(), "Directas=${rutasDirectas.size}", Toast.LENGTH_SHORT).show()

        if (rutasDirectas.isNotEmpty()) {
            val r = rutasDirectas.first()
            val hue = coloresHue[0]
            val colorInt = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))

            val (idxO, ptO) = nearestIndexOnRuta(r, origenLatLng)
            val (idxD, ptD) = nearestIndexOnRuta(r, destino)
            val tramo = subListOrdered(r.coordenadas.map { LatLng(it.latitud, it.longitud) }, idxO, idxD)

            dibujarTramoSnap(tramo, colorInt)
            agregarALeyenda(r.nombre, r.empresa.nombre, colorInt)
            pintarParaderosRuta(r, hue)

            // NUEVO: trazo a pie por calles reales (suspend)
            dibujarCaminoAPie(origenLatLng, ptO)
            dibujarCaminoAPie(ptD, destino)

            mostrarMarcadorDestino(destino)
            ajustarCamara(listOf(origenLatLng, destino, ptO, ptD) + tramo)
            return@withContext
        }

        // ---- COMBINADA (r1, r2) ----
        val rutasCercaOrigen = withContext(Dispatchers.IO) {
            rutas.filter { r -> rutaCercaDePuntoPorCalle(r, origenLatLng) }
        }

        val combos = withContext(Dispatchers.IO) {
            val out = mutableListOf<Pair<RutaCompleta, RutaCompleta>>()
            val usados = mutableSetOf<Pair<Long, Long>>()
            for (r1 in rutasCercaOrigen) {
                for (r2 in rutas) {
                    if (r1.id == r2.id) continue
                    val p1 = r1.id to r2.id
                    val p2 = r2.id to r1.id
                    if (usados.contains(p1) || usados.contains(p2)) continue
                    if (rutasConectadasPorCalle(r1, r2) && rutaCercaDePuntoPorCalle(r2, destino)) {
                        out.add(r1 to r2); usados.add(p1)
                    }
                }
            }
            out
        }
        Toast.makeText(requireContext(), "combinadas=${combos.size}", Toast.LENGTH_SHORT).show()

        if (combos.isEmpty()) {
            Toast.makeText(requireContext(), "No se encontraron rutas combinadas válidas", Toast.LENGTH_LONG).show()
            mostrarMarcadorDestino(destino)
            return@withContext
        }

        val bounds = mutableListOf<LatLng>()
        bounds += listOf(origenLatLng, destino)

        combos.forEachIndexed { i, (r1, r2) ->
            val hue1 = coloresHue[(i * 2) % coloresHue.size]
            val hue2 = coloresHue[(i * 2 + 1) % coloresHue.size]
            val col1 = Color.HSVToColor(floatArrayOf(hue1, 1f, 1f))
            val col2 = Color.HSVToColor(floatArrayOf(hue2, 1f, 1f))

            viewLifecycleOwner.lifecycleScope.launch {
                val (idxA, ptA) = nearestIndexOnRuta(r1, origenLatLng)
                val transfer = bestTransferBetween(r1, r2) ?: return@launch
                val (idxTA, idxTB, ptTrans) = transfer
                val (idxB, ptB) = nearestIndexOnRuta(r2, destino)

                val listA = r1.coordenadas.map { LatLng(it.latitud, it.longitud) }
                val listB = r2.coordenadas.map { LatLng(it.latitud, it.longitud) }

                val tramoA = subListOrdered(listA, idxA, idxTA)
                val tramoB = subListOrdered(listB, idxTB, idxB)

                dibujarTramoSnap(tramoA, col1); agregarALeyenda(r1.nombre, r1.empresa.nombre, col1); pintarParaderosRuta(r1, hue1)
                dibujarTramoSnap(tramoB, col2); agregarALeyenda(r2.nombre, r2.empresa.nombre, col2); pintarParaderosRuta(r2, hue2)

                // NUEVO: tramos a pie por calles reales (suspend)
                dibujarCaminoAPie(origenLatLng, listA[idxA])
                dibujarCaminoAPie(listA[idxTA], ptTrans)
                dibujarCaminoAPie(ptTrans, listB[idxTB])
                dibujarCaminoAPie(listB[idxB], destino)

                bounds += listOf(ptA, ptTrans, ptB) + tramoA + tramoB
            }
        }

        mostrarMarcadorDestino(destino)
        viewLifecycleOwner.lifecycleScope.launch {
            delay(300)
            ajustarCamara(bounds)
        }
    }

    // -------------------- Limpieza --------------------
    private fun limpiarTodoMenosMiUbicacion() {
        marcadorDestino?.remove()
        marcadoresParaderos.forEach { it.remove() }
        polylines.forEach { it.remove() }
        marcadoresParaderos.clear()
        polylines.clear()
        // Conserva la estructura base de la leyenda definida en tu XML
        for (i in leyendaContainer.childCount - 1 downTo leyendaBaseChildren) {
            leyendaContainer.removeViewAt(i)
        }
        leyendaInicialAgregada = true
    }

    private fun limpiarPolylines() {
        polylines.forEach { it.remove() }
        polylines.clear()
    }

    // -------------------- Cercanía (con fallback) --------------------
    private suspend fun rutaCercaDePuntoPorCalle(ruta: RutaCompleta, punto: LatLng): Boolean {
        val candidatos = ruta.coordenadas.filter {
            haversineMeters(punto.latitude, punto.longitude, it.latitud, it.longitud) <= PREFILTRO_HAVERSINE_METROS
        }
        if (candidatos.isEmpty()) return false
        for (c in candidatos) {
            val d = walkingDistance(punto, LatLng(c.latitud, c.longitud))
            if (d < TOLERANCIA_METROS) return true
        }
        return false
    }

    private suspend fun rutasConectadasPorCalle(r1: RutaCompleta, r2: RutaCompleta): Boolean {
        val l1 = r1.coordenadas
        val l2 = r2.coordenadas
        val step1 = max(1, l1.size / 200)
        val step2 = max(1, l2.size / 200)
        for (i in l1.indices step step1) {
            val a = l1[i]
            for (j in l2.indices step step2) {
                val b = l2[j]
                val geo = haversineMeters(a.latitud, a.longitud, b.latitud, b.longitud)
                if (geo <= PREFILTRO_HAVERSINE_METROS) {
                    val d = walkingDistance(LatLng(a.latitud, a.longitud), LatLng(b.latitud, b.longitud))
                    if (d < TOLERANCIA_METROS) return true
                }
            }
        }
        return false
    }

    // -------------------- Dibujo --------------------
    private suspend fun dibujarTramoSnap(tramo: List<LatLng>, colorInt: Int) {
        if (tramo.size < 2) return

        // Si estamos en modo offline, pinta la línea cruda
        if (USE_ONLY_HAVERSINE) {
            withContext(Dispatchers.Main) {
                polylines.add(
                    googleMap.addPolyline(
                        PolylineOptions().addAll(tramo).width(10f).color(colorInt)
                    )
                )
            }
            return
        }

        // Roads: reducir llamadas con saltos
        val stride = max(1, tramo.size / 60)
        val pares = mutableListOf<Pair<LatLng, LatLng>>()
        var i = 0
        while (i < tramo.size - 1) {
            val j = min(tramo.size - 1, i + stride)
            pares.add(tramo[i] to tramo[j])
            i = j
        }

        val tramosSnap = withContext(Dispatchers.IO) {
            pares.map { (a, b) ->
                async {
                    val key = snapKey(a, b)
                    cacheSnap[key] ?: networkGate.withPermit {
                        val puntos = obtenerPolylineaDesdeGoogle(a, b)
                        cacheSnap[key] = puntos
                        puntos
                    }
                }
            }.awaitAll().flatten()
        }

        val paraPintar = if (tramosSnap.isNotEmpty()) tramosSnap else tramo
        withContext(Dispatchers.Main) {
            polylines.add(
                googleMap.addPolyline(
                    PolylineOptions().addAll(paraPintar).width(10f).color(colorInt)
                )
            )
        }
    }

    // ====== NUEVO: ruta a pie por calles reales (Directions) ======
    private suspend fun dibujarCaminoAPie(a: LatLng, b: LatLng) {
        // Intentar con Directions (calles reales)
        val ruta = walkingRoutePointsViaDirections(a, b)

        val puntos = if (ruta.isNotEmpty()) ruta else listOf(a, b) // fallback recto si falla
        withContext(Dispatchers.Main) {
            polylines.add(
                googleMap.addPolyline(
                    PolylineOptions()
                        .addAll(puntos)
                        .width(6f)
                        .pattern(walkPattern)   // mantiene punteado
                        .color(Color.DKGRAY)
                )
            )
        }
    }

    private fun pintarParaderosRuta(ruta: RutaCompleta, colorHue: Float) {
        ruta.paraderos.forEach { p ->
            marcadoresParaderos.add(
                googleMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(p.latitud, p.longitud))
                        .title("${p.nombre} (${ruta.empresa.nombre})")
                        .icon(BitmapDescriptorFactory.defaultMarker(colorHue))
                )!!
            )
        }
    }

    // -------------------- Distancias --------------------
    private suspend fun walkingDistance(origen: LatLng, destino: LatLng): Double {
        if (USE_ONLY_HAVERSINE) {
            return haversineMeters(origen.latitude, origen.longitude, destino.latitude, destino.longitude)
        }
        val dm = distanciaPorCalle(origen, destino)
        if (dm != Double.MAX_VALUE) return dm
        val geo = haversineMeters(origen.latitude, origen.longitude, destino.latitude, destino.longitude)
        return geo * 1.35 + 50.0 // fallback
    }

    private suspend fun distanciaPorCalle(origen: LatLng, destino: LatLng): Double = withContext(Dispatchers.IO) {
        val key = distKey(origen, destino)
        cacheDistancia[key]?.let { return@withContext it }
        val url = "https://maps.googleapis.com/maps/api/distancematrix/json?" +
                "origins=${origen.latitude},${origen.longitude}" +
                "&destinations=${destino.latitude},${destino.longitude}" +
                "&mode=walking&key=$apiKey"
        return@withContext networkGate.withPermit {
            try {
                val req = Request.Builder().url(url).build()
                http.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: return@use Double.MAX_VALUE
                    val json = JSONObject(body)
                    val el = json.getJSONArray("rows").getJSONObject(0).getJSONArray("elements").getJSONObject(0)
                    val status = el.optString("status", "ZERO_RESULTS")
                    val value = if (status == "OK") el.getJSONObject("distance").getDouble("value") else Double.MAX_VALUE
                    cacheDistancia[key] = value
                    return@use value
                }
            } catch (e: Exception) {
                tracker.error("RutaPlanificadaFragment", "distance_matrix", e.message ?: "error distancia", currentUserId())
                Double.MAX_VALUE
            }
        }
    }

    private suspend fun obtenerPolylineaDesdeGoogle(start: LatLng, end: LatLng): List<LatLng> = withContext(Dispatchers.IO) {
        val url = "https://roads.googleapis.com/v1/snapToRoads?path=${start.latitude},${start.longitude}|${end.latitude},${end.longitude}&interpolate=true&key=$apiKey"
        try {
            val req = Request.Builder().url(url).build()
            networkGate.withPermit {
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<LatLng>()
                    val body = resp.body?.string() ?: return@use emptyList<LatLng>()
                    val json = JSONObject(body)
                    val snapped = json.optJSONArray("snappedPoints") ?: return@use emptyList<LatLng>()
                    val out = ArrayList<LatLng>(snapped.length())
                    for (i in 0 until snapped.length()) {
                        val loc = snapped.getJSONObject(i).getJSONObject("location")
                        out.add(LatLng(loc.getDouble("latitude"), loc.getDouble("longitude")))
                    }
                    return@use out
                }
            }
        } catch (e: Exception) {
            tracker.error("RutaPlanificadaFragment", "roads_snap", e.message ?: "error roads", currentUserId())
            emptyList()
        }
    }

    // ====== NUEVO: Directions API (walking polyline) y helpers ======
    private fun dirKey(a: LatLng, b: LatLng) = "walk:${a.latitude},${a.longitude}|${b.latitude},${b.longitude}"

    private suspend fun walkingRoutePointsViaDirections(a: LatLng, b: LatLng): List<LatLng> =
        withContext(Dispatchers.IO) {
            val key = dirKey(a, b)
            cacheWalking[key]?.let { return@withContext it }

            val url = ("https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=${a.latitude},${a.longitude}" +
                    "&destination=${b.latitude},${b.longitude}" +
                    "&mode=walking&key=$apiKey")

            return@withContext try {
                val req = Request.Builder().url(url).build()
                networkGate.withPermit {
                    http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use emptyList<LatLng>()
                        val body = resp.body?.string() ?: return@use emptyList<LatLng>()
                        val json = JSONObject(body)
                        val status = json.optString("status", "ZERO_RESULTS")
                        if (status != "OK") return@use emptyList<LatLng>()

                        val routes = json.optJSONArray("routes") ?: return@use emptyList<LatLng>()
                        if (routes.length() == 0) return@use emptyList<LatLng>()
                        val overview = routes.getJSONObject(0).getJSONObject("overview_polyline")
                        val encoded = overview.getString("points")
                        val decoded = decodePolyline(encoded)
                        cacheWalking[key] = decoded
                        decoded
                    }
                }
            } catch (e: Exception) {
                tracker.error("RutaPlanificadaFragment", "directions", e.message ?: "error directions", currentUserId())
                emptyList()
            }
        }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val latD = lat / 1E5
            val lngD = lng / 1E5
            poly.add(LatLng(latD, lngD))
        }
        return poly
    }

    // -------------------- Geo helpers --------------------
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        return 2 * R * asin(sqrt(a))
    }

    private suspend fun nearestIndexOnRuta(r: RutaCompleta, p: LatLng): Pair<Int, LatLng> {
        val list = r.coordenadas.map { LatLng(it.latitud, it.longitud) }
        var bestIdx = 0; var bestGeo = Double.MAX_VALUE
        for (i in list.indices) {
            val g = haversineMeters(p.latitude, p.longitude, list[i].latitude, list[i].longitude)
            if (g < bestGeo) { bestGeo = g; bestIdx = i }
        }
        return bestIdx to list[bestIdx]
    }

    private suspend fun bestTransferBetween(r1: RutaCompleta, r2: RutaCompleta): Triple<Int, Int, LatLng>? {
        val l1 = r1.coordenadas.map { LatLng(it.latitud, it.longitud) }
        val l2 = r2.coordenadas.map { LatLng(it.latitud, it.longitud) }
        var best: Triple<Int, Int, LatLng>? = null
        var bestCost = Double.MAX_VALUE
        val step1 = max(1, l1.size / 200)
        val step2 = max(1, l2.size / 200)
        for (i in l1.indices step step1) {
            val a = l1[i]
            for (j in l2.indices step step2) {
                val b = l2[j]
                val geo = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
                if (geo <= PREFILTRO_HAVERSINE_METROS) {
                    val d = walkingDistance(a, b)
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

    private fun subListOrdered(list: List<LatLng>, i: Int, j: Int): List<LatLng> =
        if (i <= j) list.subList(i, j + 1) else list.subList(j, i + 1)

    private suspend fun estaCercaDeCualquierParadero(p: LatLng, umbralMetros: Double): Boolean = withContext(Dispatchers.Default) {
        marcadoresParaderos.any { m -> haversineMeters(p.latitude, p.longitude, m.position.latitude, m.position.longitude) <= umbralMetros }
    }

    // -------------------- UI helpers --------------------
    private fun mostrarMarcadorDestino(latLng: LatLng) {
        marcadorDestino?.remove()
        marcadorDestino = googleMap.addMarker(MarkerOptions().position(latLng).title("Destino"))
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }

    private fun agregarALeyenda(nombreRuta: String, empresa: String, color: Int) {
        val context = requireContext()
        val texto = "$nombreRuta - $empresa"

        // evita duplicados
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
            ).apply { setMargins(0, 8, 0, 8) }
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
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100))
    }

    // -------------------- Ciclo de vida --------------------
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        tracker.screenView("RutaPlanificadaFragment", currentUserId())
        if (mapReady) habilitarMiUbicacionEnMapaSiPermiso()
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

    // -------------------- Permisos --------------------
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                if (mapReady) habilitarMiUbicacionEnMapaSiPermiso()
            } else {
                tracker.error("RutaPlanificadaFragment", "location_permission", "denegado", currentUserId())
                Toast.makeText(requireContext(), "Permisos de ubicación denegados", Toast.LENGTH_LONG).show()
            }
        }
    }

    // -------------------- Keys de caché --------------------
    private fun distKey(a: LatLng, b: LatLng) = "${a.latitude},${a.longitude}|${b.latitude},${b.longitude}"
    private fun snapKey(a: LatLng, b: LatLng) = "snap:${a.latitude},${a.longitude}|${b.latitude},${b.longitude}"
}
