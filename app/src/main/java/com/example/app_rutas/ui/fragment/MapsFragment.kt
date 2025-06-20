package com.example.app_rutas.ui.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.app_rutas.R
import com.example.app_rutas.domain.usecases.ObtenerParaderoCercanoUseCase
import com.example.app_rutas.infrastructure.repositories.ParaderoRepositoryImpl
import com.example.app_rutas.model.Coordenada
import com.example.app_rutas.model.Paradero
import com.example.app_rutas.model.Ruta
import com.example.app_rutas.ui.viewmodel.*
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MapsFragment : Fragment(), OnMapReadyCallback {

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

    private val apiKey = "AIzaSyAFpBlDKWCOpGY7MliuGGd8pCThUjXLkbA"

    private val paraderoViewModel: ParaderoViewModel by viewModels {
        ParaderoViewModelFactory(ObtenerParaderoCercanoUseCase(ParaderoRepositoryImpl()))
    }

    private val rutaViewModel: RutaViewModel by viewModels {
        RutaViewModelFactory()
    }

    private val rutasDisponiblesViewModel: RutasDisponiblesViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_maps, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        btnParaderoCercano = view.findViewById(R.id.btnParaderoCercano)
        dropdownRutas = view.findViewById(R.id.auto)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnParaderoCercano.setOnClickListener { obtenerUbicacion() }

        paraderoViewModel.paraderoCercano.observe(viewLifecycleOwner) {
            mostrarParaderoEnMapa(it)
        }

        rutaViewModel.coordenadasRuta.observe(viewLifecycleOwner) { coordenadas ->
            trazarRutaConCalles(coordenadas)
        }

        rutasDisponiblesViewModel.rutasDisponibles.observe(viewLifecycleOwner) { rutas ->
            configurarDropdown(rutas)
        }

        rutasDisponiblesViewModel.obtenerRutas()

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
                        destino.latitude, destino.longitude,
                        distancia
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
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.clear()

        // Coordenadas de Piura, Perú
        val piuraLatLng = LatLng(-5.19449, -80.63282)

        // Mover la cámara al iniciar
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(piuraLatLng, 14f)) // Zoom 14 = ciudad
    }

    private fun getScaledMarkerIcon(resourceId: Int, width: Int = 100, height: Int = 100): BitmapDescriptor {
        val bitmap = BitmapFactory.decodeResource(resources, resourceId)
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
        }
    }

    private fun configurarDropdown(rutas: List<Ruta>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, rutas.map { it.nombre })
        dropdownRutas.setAdapter(adapter)

        dropdownRutas.setOnItemClickListener { _, _, position, _ ->
            val rutaSeleccionada = rutas[position]
            rutaSeleccionadaActual = rutaSeleccionada

            googleMap.clear()
            marcadorParadero = null
            marcadorUsuario = null
            ultimaRutaHastaParadero?.remove()
            ultimaRutaHastaParadero = null
            paraderoActual = null


            rutaViewModel.obtenerRuta(rutaSeleccionada.id)
        }
    }

    private fun actualizarUbicacionEnMapa(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        marcadorUsuario?.remove()
        marcadorUsuario = googleMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Tu ubicación")
                .icon(getScaledMarkerIcon(R.drawable.ic_persona, 80, 80))

        )
    }

    private fun trazarRutaHastaParadero(origen: LatLng, destino: LatLng) {
        val url = "https://maps.googleapis.com/maps/api/directions/json?origin=${origen.latitude},${origen.longitude}&destination=${destino.latitude},${destino.longitude}&key=$apiKey"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                response.body?.string()?.let { responseData ->
                    val json = JSONObject(responseData)
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val polyline = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                        val path = decodePolyline(polyline)

                        launch(Dispatchers.Main) {
                            ultimaRutaHastaParadero?.remove()
                            val polylineOptions = PolylineOptions()
                                .color(android.graphics.Color.RED)
                                .width(8f)
                            path.forEach { polylineOptions.add(it) }
                            ultimaRutaHastaParadero = googleMap.addPolyline(polylineOptions)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun trazarRutaConCalles(coordenadas: List<Coordenada>) {
        if (coordenadas.size < 2) return

        val origen = coordenadas.first()
        val destino = coordenadas.last()
        val waypoints = coordenadas.drop(1).dropLast(1).joinToString("|") { "${it.latitud},${it.longitud}" }

        val url = "https://maps.googleapis.com/maps/api/directions/json?origin=${origen.latitud},${origen.longitud}&destination=${destino.latitud},${destino.longitud}&waypoints=$waypoints&key=$apiKey"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                response.body?.string()?.let { responseData ->
                    val json = JSONObject(responseData)
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val polyline = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                        val path = decodePolyline(polyline)

                        launch(Dispatchers.Main) {
                            val polylineOptions = PolylineOptions().color(android.graphics.Color.BLUE).width(10f)
                            path.forEach { polylineOptions.add(it) }
                            googleMap.addPolyline(polylineOptions)

                            val boundsBuilder = LatLngBounds.builder()
                            path.forEach { boundsBuilder.include(it) }
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
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
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val latLng = LatLng(lat / 1E5, lng / 1E5)
            poly.add(latLng)
        }

        return poly
    }
}
