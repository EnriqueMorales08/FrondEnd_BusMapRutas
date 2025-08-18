package com.example.app_rutas.ui.fragment

import android.Manifest
import android.animation.ValueAnimator
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot

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
    private var velocidadBusKmh: Double? = null        // última velocidad reportada por Firebase
    private var etaJob: Job? = null                    // loop de 1 minuto para recalcular ETA
    private val ETA_RECALC_MS = 60_000L                // cada 1 minuto
    // ========================

    private val paraderoViewModel: ParaderoViewModel by viewModels {
        ParaderoViewModelFactory(ObtenerParaderoCercanoUseCase(ParaderoRepositoryImpl()))
    }
    private val informacionViewModel: InformacionViewModel by viewModels {
        InformacionViewModelFactory(InformacionRepositoryImpl())
    }
    private val rutaViewModel: RutaViewModel by viewModels { RutaViewModelFactory() }
    private val rutasDisponiblesViewModel: RutasDisponiblesViewModel by viewModels()

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

        btnParaderoCercano.setOnClickListener { obtenerUbicacion() }

        paraderoViewModel.paraderoCercano.observe(viewLifecycleOwner) { mostrarParaderoEnMapa(it) }
        rutaViewModel.coordenadasRuta.observe(viewLifecycleOwner) { snapToRoadsYMostrarRuta(it) }
        rutasDisponiblesViewModel.rutasDisponibles.observe(viewLifecycleOwner) { configurarDropdown(it) }

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
            }
        }

        rutasDisponiblesViewModel.obtenerRutas()

        // Ubicación del usuario (tu lógica original)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                actualizarUbicacionEnMapa(location)

                paraderoActual?.let { paradero ->
                    val origen = LatLng(location.latitude, location.longitude)
                    val destino = LatLng(paradero.latitud, paradero.longitud)

                    val distancia = FloatArray(1)
                    Location.distanceBetween(
                        origen.latitude, origen.longitude,
                        destino.latitude, destino.longitude, distancia
                    )
                    if (distancia[0] > 20) {
                        trazarRutaHastaParadero(origen, destino)
                    } else {
                        ultimaRutaHastaParadero?.remove()
                    }
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
        }

        // Observa la posición del bus (PEGADO a la polilínea si está cerca)
        busViewModel.posicion.observe(viewLifecycleOwner) { pos ->
            val lat = pos?.latitud ?: return@observe
            val lng = pos.longitud ?: return@observe
            val vel = pos.velocidad
            velocidadBusKmh = vel  // ====== NUEVO: guardar última velocidad del Firebase ======
            val posCruda = LatLng(lat, lng)

            // Por defecto usamos la lectura cruda; si la ruta está cargada, probamos “snap”
            var destino = posCruda
            if (rutaActualLatLngs.size >= 2) {
                val (puntoSnap, distM) = closestPointOnPath(posCruda, rutaActualLatLngs)
                if (distM <= BUS_SNAP_MAX_METERS) destino = puntoSnap
            }

            if (marcadorBusSuperStar == null) {
                val icon = getScaledMarkerIcon(R.drawable.ic_bus, 90, 90) // tu drawable del bus
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

            // ====== NUEVO: si ya hay paradero seleccionado, refrescar ETA inmediatamente ======
            if (paraderoActual != null) {
                actualizarEtaYUi(mostrarToast = false)
            }
        }

        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageButton>(R.id.btnDrawer)?.setOnClickListener {
            (activity as? MainActivity)?.toggleDrawer()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.clear()
        val piuraLatLng = LatLng(-5.19449, -80.63282)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(piuraLatLng, 14f))
    }

    private fun getScaledMarkerIcon(resourceId: Int, width: Int = 100, height: Int = 100): BitmapDescriptor? {
        val res = context?.resources ?: return null
        val bitmap = BitmapFactory.decodeResource(res, resourceId)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
        return BitmapDescriptorFactory.fromBitmap(scaledBitmap)
    }

    private fun obtenerUbicacion() {
        if (rutaSeleccionadaActual == null) {
            Toast.makeText(requireContext(), "Primero selecciona una ruta", Toast.LENGTH_SHORT).show()
            return
        }
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                paraderoViewModel.obtenerParaderoMasCercano(it.latitude, it.longitude, rutaSeleccionadaActual!!.id)
            } ?: Toast.makeText(requireContext(), "Ubicación no disponible", Toast.LENGTH_SHORT).show()
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

            // ====== NUEVO: inicia/renueva el loop de ETA ======
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
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, rutas)
        dropdownRutas.setAdapter(adapter)

        dropdownRutas.setOnItemClickListener { _, _, position, _ ->
            val rutaSeleccionada = rutas[position]
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

            // ====== NUEVO: al cambiar de ruta, limpiar ETA/paradero ======
            stopEtaLoop()
            paraderoActual = null

            rutaSeleccionadaActual = rutaSeleccionada
            googleMap.clear()
            marcadorParadero = null
            marcadorUsuario = null
            ultimaRutaHastaParadero?.remove()
            ultimaRutaHastaParadero = null

            rutaActualLatLngs = emptyList() // limpiar polilínea actual

            rutaViewModel.obtenerRuta(rutaSeleccionada.id)
            informacionViewModel.obtenerInformacion(rutaSeleccionada.empresa.id)

            // Encender/apagar seguimiento de bus según la EMPRESA
            marcadorBusSuperStar?.remove()
            marcadorBusSuperStar = null
            ultimaPosBus = null
            busAnimator?.cancel()
            busAnimator = null

            if (empresaTieneGPS(rutaSeleccionada.empresa)) {
                busViewModel.start(path = "ubicacion") { msg ->
                    Toast.makeText(requireContext(), "Firebase: $msg", Toast.LENGTH_SHORT).show()
                }
            } else {
                busViewModel.stop()
            }
        }
    }

    private fun actualizarUbicacionEnMapa(location: Location) {
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

    private fun trazarRutaHastaParadero(origen: LatLng, destino: LatLng) {
        val polylineOptions = PolylineOptions()
            .add(origen)
            .add(destino)
            .color(android.graphics.Color.RED)
            .width(8f)
        ultimaRutaHastaParadero?.remove()
        ultimaRutaHastaParadero = googleMap.addPolyline(polylineOptions)
    }

    private fun snapToRoadsYMostrarRuta(coordenadas: List<Coordenada>) {
        if (coordenadas.isEmpty()) return
        val path = coordenadas.joinToString("|") { "${it.latitud},${it.longitud}" }
        val url = "https://roads.googleapis.com/v1/snapToRoads?path=$path&interpolate=true&key=$apiKey"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val responseData = response.body?.string()
                if (!response.isSuccessful || responseData.isNullOrEmpty()) return@launch

                val json = JSONObject(responseData)
                val snappedPoints = json.getJSONArray("snappedPoints")

                val snappedLatLngs = mutableListOf<LatLng>()
                for (i in 0 until snappedPoints.length()) {
                    val location = snappedPoints.getJSONObject(i).getJSONObject("location")
                    val lat = location.getDouble("latitude")
                    val lng = location.getDouble("longitude")
                    snappedLatLngs.add(LatLng(lat, lng))
                }

                launch(Dispatchers.Main) {
                    val polylineOptions = PolylineOptions()
                        .addAll(snappedLatLngs)
                        .color(android.graphics.Color.BLUE)
                        .width(10f)
                    googleMap.addPolyline(polylineOptions)

                    // guarda la polilínea para pegar el bus y calcular ETA
                    rutaActualLatLngs = snappedLatLngs

                    val bounds = LatLngBounds.builder()
                    snappedLatLngs.forEach { bounds.include(it) }
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
        busAnimator?.cancel()
        busAnimator = null
        busViewModel.stop()
        marcadorBusSuperStar = null
        ultimaPosBus = null
        rutaActualLatLngs = emptyList()
        stopEtaLoop()
    }

    // ===================== GEOMETRÍA (pegado a polilínea) =====================
    // Proyecta 'p' al punto más cercano sobre la polilínea 'path' (devuelve punto y distancia en metros)
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

    // Punto más cercano de 'p' al segmento [a,b], usando proyección equirectangular local (metros)
    private fun closestPointOnSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Pair<LatLng, Double> {
        val R = 6371000.0
        val deg2rad = Math.PI / 180.0
        val latRef = (a.latitude + b.latitude) / 2.0
        val cosRef = cos(latRef * deg2rad)

        // Vector AB en metros
        val dx = (b.longitude - a.longitude) * deg2rad * R * cosRef
        val dy = (b.latitude - a.latitude) * deg2rad * R

        // Vector AP en metros
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
        val index: Int,          // índice del segmento [i, i+1]
        val point: LatLng,       // punto proyectado sobre el segmento
        val t: Double,           // posición relativa dentro del segmento (0..1)
        val segLen: Double,      // longitud del segmento en m
        val offDist: Double      // distancia de p al segmento (m)
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

    // Proyección con parámetro t y longitudes en metros
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

    // Distancia mínima sobre la polilínea entre dos puntos proyectados
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
                // invertido
                var d = a.t * a.segLen
                for (i in a.index - 1 downTo b.index + 1) {
                    d += segLenMeters(path[i], path[i - 1])
                }
                d += (1 - b.t) * b.segLen
                d
            }
        }
        // Toma el menor (por si el bus va “al revés” y la ruta hace curvas)
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
        // cálculo inmediato y toast inicial
        actualizarEtaYUi(mostrarToast = true)

        etaJob?.cancel()
        etaJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && paraderoActual != null) {
                delay(ETA_RECALC_MS)
                actualizarEtaYUi(mostrarToast = false)
            }
        }
    }

    private fun stopEtaLoop() {
        etaJob?.cancel()
        etaJob = null
    }

    private fun actualizarEtaYUi(mostrarToast: Boolean) {
        val bus = marcadorBusSuperStar?.position ?: return
        val parada = paraderoActual ?: return

        // 1) Proyectar bus y paradero a la polilínea
        val distMetros: Double = if (rutaActualLatLngs.size >= 2) {
            val from = projectOnPathWithIndex(bus, rutaActualLatLngs) ?: return
            val to = projectOnPathWithIndex(LatLng(parada.latitud, parada.longitud), rutaActualLatLngs) ?: return
            distanceAlongPath(from, to, rutaActualLatLngs)
        } else {
            // Fallback: distancia recta si no hay polilínea
            val out = FloatArray(1)
            Location.distanceBetween(
                bus.latitude, bus.longitude,
                parada.latitud, parada.longitud, out
            )
            out[0].toDouble()
        }

        // 2) Velocidad en m/s (Firebase en km/h). Fallback si no hay dato fiable.
        val vKmh = velocidadBusKmh
        val speedMps = when {
            vKmh != null && vKmh > 3.0 -> (vKmh * 1000.0) / 3600.0
            else -> 30.0 * 1000.0 / 3600.0  // ~30 km/h por defecto
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

        if (mostrarToast) {
            Toast.makeText(requireContext(), "ETA al paradero: $etaStr (dist: $distStr)", Toast.LENGTH_SHORT).show()
        }
    }
    // ===========================================================
}
