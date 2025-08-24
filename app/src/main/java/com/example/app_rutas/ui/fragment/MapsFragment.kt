package com.example.app_rutas.ui.fragment

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.app_rutas.R
import com.example.app_rutas.domain.usecases.ObtenerParaderoCercanoUseCase
import com.example.app_rutas.infrastructure.repositories.InformacionRepositoryImpl
import com.example.app_rutas.infrastructure.repositories.ParaderoRepositoryImpl
import com.example.app_rutas.model.Coordenada
import com.example.app_rutas.model.Empresa
import com.example.app_rutas.model.Paradero
import com.example.app_rutas.model.Ruta
import com.example.app_rutas.ui.activity.MainActivity
import com.example.app_rutas.ui.viewmodel.*
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import com.example.app_rutas.infrastructure.telemetry.TelemetryTracker

class MapsFragment : Fragment(), OnMapReadyCallback {

    // ===== Configura aquí el/los IDs de empresas que SÍ tienen GPS =====
    private val empresas_con_gps = setOf(1L)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap
    private lateinit var btnParaderoCercano: View
    private lateinit var dropdownRutas: AutoCompleteTextView
    private var rutaSeleccionadaActual: Ruta? = null
    private var marcadorParadero: Marker? = null
    private var marcadorUsuario: Marker? = null
    private var ultimaRutaHastaParadero: Polyline? = null
    private var paraderoActual: Paradero? = null
    private lateinit var locationCallback: LocationCallback
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var bottomSheetView: View

    private val apiKey = "AIzaSyAFpBlDKWCOpGY7MliuGGd8pCThUjXLkbA"

    // --- Bus en tiempo real (Firebase vía ViewModel) ---
    private val busViewModel: BusViewModel by viewModels {
        BusViewModelFactory("ubicacion") // nodo actual de tu Firebase
    }
    private var marcadorBusSuperStar: Marker? = null
    private var ultimaPosBus: LatLng? = null
    private var busAnimator: ValueAnimator? = null
    private val BUS_ANIM_DURATION = 5_000L // animación entre updates

    // Polilínea “snappeada” y umbral para pegar el bus a la ruta
    private var rutaActualLatLngs: List<LatLng> = emptyList()
    private val BUS_SNAP_MAX_METERS = 60.0

    // ====== NUEVO: ETA ======
    private var velocidadBusKmh: Double? = null
    private var etaJob: Job? = null
    private val ETA_RECALC_MS = 60_000L

    private val paraderoViewModel: ParaderoViewModel by viewModels {
        ParaderoViewModelFactory(ObtenerParaderoCercanoUseCase(ParaderoRepositoryImpl()))
    }
    private val informacionViewModel: InformacionViewModel by viewModels {
        InformacionViewModelFactory(InformacionRepositoryImpl())
    }
    private val rutaViewModel: RutaViewModel by viewModels { RutaViewModelFactory() }
    private val rutasDisponiblesViewModel: RutasDisponiblesViewModel by viewModels()

    // ====== NUEVO: ocultar ubicación y línea al estar muy cerca (≤10 m) ======
    private var ocultarUsuarioPorProximidad = false

    // ====== NUEVO: control Directions para evitar spam ======
    private var ultimoOrigen: LatLng? = null
    private var ultimoDestino: LatLng? = null
    private var ultimaPeticionTs: Long = 0L
    private val MIN_DIRECTIONS_INTERVAL_MS = 15_000L

    // ====== NUEVO: Jobs para cancelar en onDestroyView ======
    private var directionsJob: Job? = null
    private var snapJob: Job? = null

    private val tracker by lazy { TelemetryTracker.get(requireContext()) }
    private fun currentUserId(): String? =
        requireContext().getSharedPreferences("rutas_prefs", Context.MODE_PRIVATE)
            .getLong("userId", -1L).takeIf { it > 0 }?.toString()
    private var firstBusFixLogged = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_maps, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bottomSheetView = view.findViewById(R.id.bottomSheetInformacion)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        btnParaderoCercano = view.findViewById(R.id.btnParaderoCercano)
        dropdownRutas = view.findViewById(R.id.auto)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        view.findViewById<ImageButton>(R.id.btnDrawer)?.setOnClickListener {
            tracker.buttonClick("MapsFragment", "btnDrawer", currentUserId(), null)
            (activity as? MainActivity)?.toggleDrawer()
        }

        btnParaderoCercano.setOnClickListener {
            val rutaSel = rutaSeleccionadaActual
            val hasPerm = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            tracker.buttonClick(
                "MapsFragment", "btnParaderoCercano", currentUserId(),
                detalles = mapOf(
                    "hasPermission" to hasPerm,
                    "rutaId" to (rutaSel?.id ?: -1),
                    "rutaNombre" to (rutaSel?.nombre ?: "none")
                )
            )
            obtenerUbicacion()
        }

        paraderoViewModel.paraderoCercano.observe(viewLifecycleOwner) {
            val paraderoId = anyLongField(it, "id", "idParadero", "paraderoId")
            tracker.buttonClick(
                "MapsFragment", "paradero_obtenido", currentUserId(),
                detalles = mapOf("paraderoId" to (paraderoId ?: -1), "hasParadero" to (it != null))
            )
            mostrarParaderoEnMapa(it)
        }
        rutaViewModel.coordenadasRuta.observe(viewLifecycleOwner) {
            tracker.buttonClick(
                "MapsFragment", "ruta_coordenadas_recibidas", currentUserId(),
                detalles = mapOf("count" to it.size)
            )
            snapToRoadsYMostrarRuta(it)
        }
        rutasDisponiblesViewModel.rutasDisponibles.observe(viewLifecycleOwner) {
            tracker.buttonClick(
                "MapsFragment", "rutas_disponibles", currentUserId(),
                detalles = mapOf("count" to it.size)
            )
            configurarDropdown(it)
        }

        informacionViewModel.informacion.observe(viewLifecycleOwner) { informacion ->
            informacion?.let {
                view.findViewById<TextView>(R.id.txtUnidades).text = it.numeroUnidades.toString()
                view.findViewById<TextView>(R.id.txtDuracion).text = it.duracionRecorrido
                view.findViewById<TextView>(R.id.txtLongitud).text = "${it.longitudRecorrido} km"
                view.findViewById<TextView>(R.id.txtInicioServicio).text =
                    "Lunes a Viernes: ${it.inicioServicioLunesViernes}\nSábado: ${it.inicioServicioSabado}\nDomingo: ${it.inicioServicioDomingo}"
                view.findViewById<TextView>(R.id.txtFinServicio).text =
                    "Lunes a Viernes: ${it.finServicioLunesViernes}\nSábado: ${it.finServicioSabado}\nDomingo: ${it.finServicioDomingo}"
                view.findViewById<TextView>(R.id.txtMensaje).text = it.mensaje
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

                val empId = anyLongField(rutaSeleccionadaActual?.empresa, "id", "idEmpresa", "empresaId")
                tracker.buttonClick(
                    "MapsFragment", "info_ruta_cargada", currentUserId(),
                    detalles = mapOf("empresaId" to (empId ?: -1), "msgLen" to (it.mensaje?.length ?: 0))
                )
            }
        }

        rutasDisponiblesViewModel.obtenerRutas()

        // Ubicación del usuario (tu lógica original + ajustes proximidad/directions)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                paraderoActual?.let { paradero ->
                    val origen = LatLng(location.latitude, location.longitude)
                    val destino = LatLng(paradero.latitud, paradero.longitud)

                    val distancia = FloatArray(1)
                    Location.distanceBetween(
                        origen.latitude, origen.longitude,
                        destino.latitude, destino.longitude, distancia
                    )

                    // ---- control de 10 m para ocultar marcador y ruta ----
                    if (distancia[0] <= 10f) {
                        if (!ocultarUsuarioPorProximidad) {
                            tracker.buttonClick(
                                "MapsFragment", "proximity_hide", currentUserId(),
                                detalles = mapOf("distM" to distancia[0])
                            )
                        }
                        ocultarUsuarioPorProximidad = true
                        ultimaRutaHastaParadero?.remove()
                        ultimaRutaHastaParadero = null
                        marcadorUsuario?.remove()
                        marcadorUsuario = null
                    } else {
                        val wasHidden = ocultarUsuarioPorProximidad
                        ocultarUsuarioPorProximidad = false
                        if (distancia[0] > 20f) {
                            trazarRutaHastaParadero(origen, destino) // calles
                        } else {
                            if (wasHidden) {
                                tracker.buttonClick(
                                    "MapsFragment", "proximity_show", currentUserId(),
                                    detalles = mapOf("distM" to distancia[0])
                                )
                            }
                            ultimaRutaHastaParadero?.remove()
                            ultimaRutaHastaParadero = null
                        }
                    }
                }

                if (!ocultarUsuarioPorProximidad) {
                    actualizarUbicacionEnMapa(location)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                LocationRequest.create().apply {
                    interval = 5000
                    fastestInterval = 3000
                    priority = Priority.PRIORITY_HIGH_ACCURACY
                },
                locationCallback,
                requireActivity().mainLooper
            )
            tracker.buttonClick("MapsFragment", "location_updates_start", currentUserId(), null)
        }

        // Observa la posición del bus (PEGADO a la polilínea si está cerca)
        busViewModel.posicion.observe(viewLifecycleOwner) { pos ->
            val lat = pos?.latitud ?: return@observe
            val lng = pos.longitud ?: return@observe
            val vel = pos.velocidad
            velocidadBusKmh = vel
            val posCruda = LatLng(lat, lng)

            if (!firstBusFixLogged) {
                firstBusFixLogged = true
                tracker.buttonClick(
                    "MapsFragment", "bus_first_fix", currentUserId(),
                    detalles = mapOf("lat" to lat, "lng" to lng, "vel" to (vel ?: -1.0))
                )
            }

            var destino = posCruda
            if (rutaActualLatLngs.size >= 2) {
                val (puntoSnap, distM) = closestPointOnPath(posCruda, rutaActualLatLngs)
                if (distM <= BUS_SNAP_MAX_METERS) destino = puntoSnap
            }

            if (marcadorBusSuperStar == null) {
                val icon = getScaledMarkerIcon(R.drawable.ic_bus, 90, 90)
                marcadorBusSuperStar = googleMap.addMarker(
                    MarkerOptions()
                        .position(destino)
                        .title("Bus SUPER STAR")
                        .snippet(if (vel != null) "Vel: $vel km/h" else null)
                        .icon(icon)
                        .anchor(0.5f, 0.5f)
                        .flat(true)
                )
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destino, 16f))
                ultimaPosBus = destino
            } else {
                val desde = ultimaPosBus ?: destino
                animateBusMarker(desde, destino, BUS_ANIM_DURATION, vel)
                ultimaPosBus = destino
            }

            if (paraderoActual != null) {
                actualizarEtaYUi(mostrarToast = false)
            }
        }

        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageButton>(R.id.btnDrawer)?.setOnClickListener {
            (activity as? MainActivity)?.toggleDrawer()
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.screenView("MapsFragment", currentUserId())
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.clear()
        val piuraLatLng = LatLng(-5.19449, -80.63282)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(piuraLatLng, 14f))
        tracker.buttonClick("MapsFragment", "map_ready", currentUserId(), null)
    }

    private fun getScaledMarkerIcon(resourceId: Int, width: Int = 100, height: Int = 100): BitmapDescriptor? {
        val res = context?.resources ?: return null
        val bitmap = BitmapFactory.decodeResource(res, resourceId)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
        return BitmapDescriptorFactory.fromBitmap(scaledBitmap)
    }

    private fun obtenerUbicacion() {
        val ctx = context ?: return
        val rutaSel = rutaSeleccionadaActual
        if (rutaSel == null) {
            tracker.error("MapsFragment", "btnParaderoCercano", "No hay ruta seleccionada", currentUserId())
            Toast.makeText(ctx, "Primero selecciona una ruta", Toast.LENGTH_SHORT).show()
            return
        }
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            tracker.buttonClick("MapsFragment", "request_permission_fine_location", currentUserId(), null)
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                tracker.buttonClick("MapsFragment", "last_location_ok", currentUserId(), null)
                paraderoViewModel.obtenerParaderoMasCercano(it.latitude, it.longitude, rutaSel.id)
            } ?: run {
                tracker.error("MapsFragment", "last_location_null", "Ubicación no disponible", currentUserId())
                Toast.makeText(ctx, "Ubicación no disponible", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            tracker.error("MapsFragment", "last_location_fail", it.message ?: "error", currentUserId())
        }
    }

    private fun mostrarParaderoEnMapa(paradero: Paradero?) {
        paradero?.let {
            marcadorParadero?.remove()
            val latLng = LatLng(it.latitud, it.longitud)
            marcadorParadero = googleMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Paradero más cercano")
                    .icon(getScaledMarkerIcon(R.drawable.ic_paradero, 80, 80))
            )
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
            paraderoActual = it

            tracker.buttonClick(
                "MapsFragment", "paradero_marker_set", currentUserId(),
                detalles = mapOf("lat" to it.latitud, "lng" to it.longitud)
            )

            // inicia/renueva el loop de ETA
            startEtaLoop()
        }
    }

    // ---- clave: decidir por empresa real, no por el texto del combo ----
    private fun empresaTieneGPS(empresa: Empresa): Boolean {
        if (empresas_con_gps.contains(empresa.id)) return true
        val normalized = empresa.nombre.replace("\\s|-|_".toRegex(), "").lowercase()
        return normalized.contains("superstar") || normalized.contains("superestar")
    }

    private fun configurarDropdown(rutas: List<Ruta>) {
        val ctx = context ?: return
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, rutas)
        dropdownRutas.setAdapter(adapter)

        dropdownRutas.setOnItemClickListener { _, _, position, _ ->
            val rutaSeleccionada = rutas[position]
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

            // al cambiar de ruta, limpiar ETA/paradero
            stopEtaLoop()
            paraderoActual = null

            rutaSeleccionadaActual = rutaSeleccionada
            googleMap.clear()
            marcadorParadero = null
            marcadorUsuario = null
            ultimaRutaHastaParadero?.remove()
            ultimaRutaHastaParadero = null

            rutaActualLatLngs = emptyList() // limpiar polilínea actual

            tracker.buttonClick(
                "MapsFragment", "ruta_select", currentUserId(),
                detalles = mapOf(
                    "rutaId" to rutaSeleccionada.id,
                    "rutaNombre" to rutaSeleccionada.nombre,
                    "empresaId" to rutaSeleccionada.empresa.id,
                    "empresaNombre" to rutaSeleccionada.empresa.nombre
                )
            )

            rutaViewModel.obtenerRuta(rutaSeleccionada.id)
            informacionViewModel.obtenerInformacion(rutaSeleccionada.empresa.id)

            // Encender/apagar seguimiento de bus según la EMPRESA
            marcadorBusSuperStar?.remove()
            marcadorBusSuperStar = null
            ultimaPosBus = null
            busAnimator?.cancel()
            busAnimator = null

            if (empresaTieneGPS(rutaSeleccionada.empresa)) {
                tracker.buttonClick("MapsFragment", "bus_tracking_start", currentUserId(), null)
                busViewModel.start(path = "ubicacion") { msg ->
                    context?.let { Toast.makeText(it, "Firebase: $msg", Toast.LENGTH_SHORT).show() }
                }
            } else {
                tracker.buttonClick("MapsFragment", "bus_tracking_stop", currentUserId(), null)
                busViewModel.stop()
            }
        }
    }

    private fun actualizarUbicacionEnMapa(location: Location) {
        if (ocultarUsuarioPorProximidad) return
        if (!isAdded || context == null || view == null) return
        val latLng = LatLng(location.latitude, location.longitude)
        marcadorUsuario?.remove()
        val icon = getScaledMarkerIcon(R.drawable.ic_persona, 80, 80)
        if (icon != null) {
            marcadorUsuario = googleMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Tu ubicación")
                    .icon(icon)
            )
        }
    }

    // ---------- DIRECCIONES POR CALLES (Google Directions API) ----------
    private fun trazarRutaHastaParadero(origen: LatLng, destino: LatLng, mode: String = "walking") {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimaPeticionTs < MIN_DIRECTIONS_INTERVAL_MS &&
            ultimoOrigen != null && ultimoDestino != null
        ) {
            val distO = FloatArray(1)
            val distD = FloatArray(1)
            Location.distanceBetween(ultimoOrigen!!.latitude, ultimoOrigen!!.longitude, origen.latitude, origen.longitude, distO)
            Location.distanceBetween(ultimoDestino!!.latitude, ultimoDestino!!.longitude, destino.latitude, destino.longitude, distD)
            if (distO[0] < 10 && distD[0] < 10) return
        }

        ultimaPeticionTs = ahora
        ultimoOrigen = origen
        ultimoDestino = destino

        tracker.buttonClick(
            "MapsFragment", "directions_request", currentUserId(),
            detalles = mapOf("mode" to mode)
        )

        directionsJob?.cancel()
        directionsJob = viewLifecycleOwner.lifecycleScope.launch {
            val puntos: List<LatLng>? = withContext(IO) {
                try {
                    val client = OkHttpClient()
                    val url: HttpUrl = HttpUrl.Builder()
                        .scheme("https")
                        .host("maps.googleapis.com")
                        .addPathSegment("maps")
                        .addPathSegment("api")
                        .addPathSegment("directions")
                        .addPathSegment("json")
                        .addQueryParameter("origin", "${origen.latitude},${origen.longitude}")
                        .addQueryParameter("destination", "${destino.latitude},${destino.longitude}")
                        .addQueryParameter("mode", mode)
                        .addQueryParameter("key", apiKey)
                        .build()

                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()

                    if (!response.isSuccessful || body.isNullOrEmpty()) return@withContext null

                    val json = JSONObject(body)
                    val routes = json.optJSONArray("routes") ?: return@withContext null
                    if (routes.length() == 0) return@withContext null

                    val overview = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                    decodePolyline(overview)
                } catch (_: Exception) {
                    null
                }
            }

            if (!isAdded || view == null) return@launch

            if (puntos.isNullOrEmpty()) {
                tracker.error("MapsFragment", "directions_request", "sin_resultados", currentUserId())
                dibujarLineaRecta(origen, destino)
                context?.let { Toast.makeText(it, "No se pudo obtener la ruta por calles. Línea directa temporal.", Toast.LENGTH_SHORT).show() }
            } else {
                tracker.buttonClick("MapsFragment", "directions_ok", currentUserId(), detalles = mapOf("points" to puntos.size))
                ultimaRutaHastaParadero?.remove()
                ultimaRutaHastaParadero = googleMap.addPolyline(
                    PolylineOptions()
                        .addAll(puntos)
                        .color(android.graphics.Color.RED)
                        .width(8f)
                )
            }
        }
    }

    private fun dibujarLineaRecta(origen: LatLng, destino: LatLng) {
        ultimaRutaHastaParadero?.remove()
        ultimaRutaHastaParadero = googleMap.addPolyline(
            PolylineOptions()
                .add(origen, destino)
                .color(android.graphics.Color.RED)
                .width(8f)
        )
    }

    // ================== Snap to Roads por bloques (≤100) ==================
    private fun snapToRoadsYMostrarRuta(coordenadas: List<Coordenada>) {
        if (coordenadas.isEmpty()) return

        // 1) Validación básica de rangos
        val validas = coordenadas.filter {
            it.latitud in -90.0..90.0 && it.longitud in -180.0..180.0
        }
        if (validas.isEmpty()) return

        // 2) Límite del Roads API y solape
        val MAX_POINTS = 100
        val OVERLAP = 1

        fun chunkedWithOverlap(list: List<Coordenada>, size: Int, overlap: Int): List<List<Coordenada>> {
            if (list.isEmpty()) return emptyList()
            val chunks = mutableListOf<List<Coordenada>>()
            var start = 0
            while (start < list.size) {
                val endExclusive = (start + size).coerceAtMost(list.size)
                chunks.add(list.subList(start, endExclusive))
                if (endExclusive == list.size) break
                start = endExclusive - overlap
            }
            return chunks
        }

        snapJob?.cancel()
        snapJob = viewLifecycleOwner.lifecycleScope.launch {
            val (snappedLatLngsGlobal, anyChunkFailed) = withContext(IO) {
                val client = OkHttpClient()
                val snapped = mutableListOf<LatLng>()
                var failed = false

                val bloques = chunkedWithOverlap(validas, MAX_POINTS, OVERLAP)
                for ((idx, bloque) in bloques.withIndex()) {
                    val pathParam = bloque.joinToString("|") { "${it.latitud},${it.longitud}" }

                    val httpUrl: HttpUrl = HttpUrl.Builder()
                        .scheme("https")
                        .host("roads.googleapis.com")
                        .addPathSegment("v1")
                        .addPathSegment("snapToRoads")
                        .addQueryParameter("path", pathParam)
                        .addQueryParameter("interpolate", "true")
                        .addQueryParameter("key", apiKey)
                        .build()

                    val request = Request.Builder().url(httpUrl).build()

                    try {
                        val response = client.newCall(request).execute()
                        val bodyStr = response.body?.string()

                        if (!response.isSuccessful || bodyStr.isNullOrEmpty()) {
                            failed = true
                            continue
                        }

                        val json = JSONObject(bodyStr)
                        val snappedPoints = json.optJSONArray("snappedPoints") ?: continue

                        val partial = mutableListOf<LatLng>()
                        for (i in 0 until snappedPoints.length()) {
                            val loc = snappedPoints.getJSONObject(i).getJSONObject("location")
                            partial.add(LatLng(loc.getDouble("latitude"), loc.getDouble("longitude")))
                        }

                        // Quita duplicado por solape
                        if (idx > 0 && partial.isNotEmpty()) partial.removeAt(0)
                        snapped.addAll(partial)

                        // delay(100) // opcional si hay límites de QPS
                    } catch (_: Exception) {
                        failed = true
                    }
                }

                Pair(snapped, failed)
            }

            if (!isAdded || view == null) return@launch

            val puntosParaDibujar: List<LatLng> =
                if (snappedLatLngsGlobal.isNotEmpty()) snappedLatLngsGlobal
                else validas.map { LatLng(it.latitud, it.longitud) } // Fallback crudo

            // Dibuja polilínea
            googleMap.addPolyline(
                PolylineOptions()
                    .addAll(puntosParaDibujar)
                    .color(android.graphics.Color.BLUE)
                    .width(10f)
            )

            // Guarda para pegado del bus y ETA
            rutaActualLatLngs = puntosParaDibujar

            // Enfocar cámara
            if (puntosParaDibujar.isNotEmpty()) {
                val bounds = LatLngBounds.builder().apply { puntosParaDibujar.forEach { include(it) } }.build()
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            }

            if (snappedLatLngsGlobal.isNotEmpty()) {
                tracker.buttonClick("MapsFragment", "snap_ok", currentUserId(), detalles = mapOf("points" to snappedLatLngsGlobal.size))
            } else if (anyChunkFailed) {
                tracker.error("MapsFragment", "snap_failed", "chunks_failed_and_fallback_raw", currentUserId())
                context?.let { Toast.makeText(it, "No se pudo usar Snap to Roads; se dibujó la ruta cruda.", Toast.LENGTH_SHORT).show() }
            } else {
                tracker.buttonClick("MapsFragment", "snap_raw", currentUserId(), detalles = mapOf("points" to puntosParaDibujar.size))
            }
        }
    }
    // =============================================================================

    // ---------- Animación del marcador de bus ----------
    private fun bearing(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var brng = Math.toDegrees(atan2(y, x))
        brng = (brng + 360) % 360
        return brng.toFloat()
    }

    private fun animateBusMarker(from: LatLng, to: LatLng, durationMs: Long = BUS_ANIM_DURATION, velocidad: Double? = null) {
        busAnimator?.cancel()
        marcadorBusSuperStar?.let { marker ->
            marker.isFlat = true
            marker.rotation = bearing(from, to)
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val t = va.animatedFraction
                val lat = from.latitude + (to.latitude - from.latitude) * t
                val lng = from.longitude + (to.longitude - from.longitude) * t
                marcadorBusSuperStar?.position = LatLng(lat, lng)
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (velocidad != null) marcadorBusSuperStar?.snippet = "Vel: $velocidad km/h"
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }
        busAnimator = animator
        animator.start()
    }
    // ---------------------------------------------------

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            tracker.buttonClick("MapsFragment", "location_updates_stop", currentUserId(), null)
        } catch (_: Exception) { /* no-op */ }

        busAnimator?.cancel()
        busAnimator = null
        busViewModel.stop()
        tracker.buttonClick("MapsFragment", "bus_tracking_stop", currentUserId(), null)
        marcadorBusSuperStar = null
        ultimaPosBus = null

        directionsJob?.cancel()
        directionsJob = null
        snapJob?.cancel()
        snapJob = null

        rutaActualLatLngs = emptyList()
        stopEtaLoop()
    }

    // ===================== GEOMETRÍA (pegado a polilínea) =====================
    private fun closestPointOnPath(p: LatLng, path: List<LatLng>): Pair<LatLng, Double> {
        if (path.size < 2) return p to Double.POSITIVE_INFINITY

        var bestPoint = path[0]
        var bestDist = Double.POSITIVE_INFINITY

        for (i in 0 until path.lastIndex) {
            val a = path[i]
            val b = path[i + 1]
            val (proj, dist) = closestPointOnSegmentMeters(p, a, b)
            if (dist < bestDist) {
                bestDist = dist
                bestPoint = proj
            }
        }
        return bestPoint to bestDist
    }

    private fun closestPointOnSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Pair<LatLng, Double> {
        val R = 6371000.0
        val deg2rad = Math.PI / 180.0
        val latRef = (a.latitude + b.latitude) / 2.0
        val cosRef = cos(latRef * deg2rad)

        val dx = (b.longitude - a.longitude) * deg2rad * R * cosRef
        val dy = (b.latitude - a.latitude) * deg2rad * R

        val px = (p.longitude - a.longitude) * deg2rad * R * cosRef
        val py = (p.latitude - a.latitude) * deg2rad * R

        val denom = dx * dx + dy * dy
        if (denom == 0.0) {
            val distA = hypot(px, py)
            return a to distA
        }

        var t = (px * dx + py * dy) / denom
        if (t < 0) t = 0.0
        if (t > 1) t = 1.0

        val projX = dx * t
        val projY = dy * t

        val dist = hypot(px - projX, py - projY)

        val projLat = a.latitude + (projY / R) * (180.0 / Math.PI)
        val projLng = a.longitude + (projX / (R * cosRef)) * (180.0 / Math.PI)

        return LatLng(projLat, projLng) to dist
    }
    // ========================================================================

    // ===================== ETA SOBRE TU POLILÍNEA ============================
    private data class Proj(
        val index: Int,
        val point: LatLng,
        val t: Double,
        val segLen: Double,
        val offDist: Double
    )

    private fun projectOnPathWithIndex(p: LatLng, path: List<LatLng>): Proj? {
        if (path.size < 2) return null
        var best: Proj? = null
        for (i in 0 until path.lastIndex) {
            val a = path[i]
            val b = path[i + 1]
            val r = projectOnSegmentWithT(p, a, b)
            val candidate = Proj(i, r.point, r.t, r.segLen, r.offDist)
            if (best == null || candidate.offDist < best!!.offDist) best = candidate
        }
        return best
    }

    private data class ProjRaw(val point: LatLng, val t: Double, val segLen: Double, val offDist: Double)

    private fun projectOnSegmentWithT(p: LatLng, a: LatLng, b: LatLng): ProjRaw {
        val R = 6371000.0
        val deg2rad = Math.PI / 180.0
        val latRef = (a.latitude + b.latitude) / 2.0
        val cosRef = cos(latRef * deg2rad)

        val dx = (b.longitude - a.longitude) * deg2rad * R * cosRef
        val dy = (b.latitude - a.latitude) * deg2rad * R
        val segLen = hypot(dx, dy)

        val px = (p.longitude - a.longitude) * deg2rad * R * cosRef
        val py = (p.latitude - a.latitude) * deg2rad * R

        val denom = dx * dx + dy * dy
        val tUnclamped = if (denom == 0.0) 0.0 else (px * dx + py * dy) / denom
        val t = when {
            tUnclamped < 0 -> 0.0
            tUnclamped > 1 -> 1.0
            else -> tUnclamped
        }

        val projX = dx * t
        val projY = dy * t
        val offDist = hypot(px - projX, py - projY)

        val projLat = a.latitude + (projY / R) * (180.0 / Math.PI)
        val projLng = a.longitude + (projX / (R * cosRef)) * (180.0 / Math.PI)
        return ProjRaw(LatLng(projLat, projLng), t, segLen, offDist)
    }

    private fun segLenMeters(a: LatLng, b: LatLng): Double {
        val R = 6371000.0
        val deg2rad = Math.PI / 180.0
        val latRef = (a.latitude + b.latitude) / 2.0
        val cosRef = cos(latRef * deg2rad)
        val dx = (b.longitude - a.longitude) * deg2rad * R * cosRef
        val dy = (b.latitude - a.latitude) * deg2rad * R
        return hypot(dx, dy)
    }

    private fun distanceAlongPath(from: Proj, to: Proj, path: List<LatLng>): Double {
        if (path.size < 2) return Double.NaN

        fun forward(a: Proj, b: Proj): Double {
            return if (a.index == b.index) {
                kotlin.math.abs(b.t - a.t) * a.segLen
            } else if (a.index < b.index) {
                var d = (1 - a.t) * a.segLen
                for (i in a.index + 1 until b.index) {
                    d += segLenMeters(path[i], path[i + 1])
                }
                d += b.t * b.segLen
                d
            } else {
                var d = a.t * a.segLen
                for (i in a.index - 1 downTo b.index + 1) {
                    d += segLenMeters(path[i], path[i - 1])
                }
                d += (1 - b.t) * b.segLen
                d
            }
        }
        val d1 = forward(from, to)
        val d2 = forward(to, from)
        return kotlin.math.min(d1, d2)
    }

    private fun humanEta(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    private fun humanDist(meters: Double): String {
        return if (meters < 950) "${meters.toInt()} m" else String.format("%.1f km", meters / 1000.0)
    }
    // =======================================================================

    // ====== LOOP de ETA cada minuto + refrescos puntuales ======
    private fun startEtaLoop() {
        tracker.buttonClick("MapsFragment", "eta_start", currentUserId(), null)
        actualizarEtaYUi(mostrarToast = true)

        etaJob?.cancel()
        etaJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && paraderoActual != null) {
                delay(ETA_RECALC_MS)
                actualizarEtaYUi(mostrarToast = false)
            }
        }
    }

    private fun stopEtaLoop() {
        tracker.buttonClick("MapsFragment", "eta_stop", currentUserId(), null)
        etaJob?.cancel()
        etaJob = null
    }

    private fun actualizarEtaYUi(mostrarToast: Boolean) {
        val bus = marcadorBusSuperStar?.position ?: return
        val parada = paraderoActual ?: return

        val distMetros: Double = if (rutaActualLatLngs.size >= 2) {
            val from = projectOnPathWithIndex(bus, rutaActualLatLngs) ?: return
            val to = projectOnPathWithIndex(LatLng(parada.latitud, parada.longitud), rutaActualLatLngs) ?: return
            distanceAlongPath(from, to, rutaActualLatLngs)
        } else {
            val out = FloatArray(1)
            Location.distanceBetween(
                bus.latitude, bus.longitude,
                parada.latitud, parada.longitud, out
            )
            out[0].toDouble()
        }

        val vKmh = velocidadBusKmh
        val speedMps = when {
            vKmh != null && vKmh > 3.0 -> (vKmh * 1000.0) / 3600.0
            else -> 30.0 * 1000.0 / 3600.0
        }

        val etaSec = kotlin.math.max(1.0, distMetros / speedMps).toLong()
        val etaStr = humanEta(etaSec)
        val distStr = humanDist(distMetros)
        val velStr = vKmh?.let { String.format("%.0f km/h", it) } ?: "—"

        marcadorBusSuperStar?.apply {
            title = "Bus SUPER STAR"
            snippet = "Vel: $velStr · ETA: $etaStr · Dist: $distStr"
            showInfoWindow()
        }

        tracker.buttonClick(
            "MapsFragment", "eta_update", currentUserId(),
            detalles = mapOf("etaSec" to etaSec, "distM" to distMetros.toInt(), "velKmh" to (vKmh ?: -1.0))
        )

        if (mostrarToast) {
            context?.let { Toast.makeText(it, "ETA al paradero: $etaStr (dist: $distStr)", Toast.LENGTH_SHORT).show() }
        }
    }
    // ===========================================================

    // ---------- Decodificador de polilínea Google ----------
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                tracker.buttonClick("MapsFragment", "permission_granted_fine_location", currentUserId(), null)
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.requestLocationUpdates(
                        LocationRequest.create().apply {
                            interval = 5000
                            fastestInterval = 3000
                            priority = Priority.PRIORITY_HIGH_ACCURACY
                        },
                        locationCallback,
                        requireActivity().mainLooper
                    )
                    tracker.buttonClick("MapsFragment", "location_updates_start", currentUserId(), null)
                }
            } else {
                tracker.error("MapsFragment", "permission_denied_fine_location", "usuario_denego", currentUserId())
                Toast.makeText(requireContext(), "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun anyLongField(o: Any?, vararg names: String): Long? {
        if (o == null) return null
        for (n in names) {
            try {
                val f = o.javaClass.getDeclaredField(n)
                f.isAccessible = true
                val v = f.get(o)
                if (v is Number) return v.toLong()
            } catch (_: Exception) { /* intenta el siguiente nombre */ }
        }
        return null
    }
}
