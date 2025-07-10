// Versión completa con Snap to Roads para ajustar la ruta a las calles reales
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
import com.example.app_rutas.model.Paradero
import com.example.app_rutas.model.Ruta
import com.example.app_rutas.ui.viewmodel.*
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
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
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var bottomSheetView: View

    private val apiKey = "AIzaSyAFpBlDKWCOpGY7MliuGGd8pCThUjXLkbA"

    private val paraderoViewModel: ParaderoViewModel by viewModels {
        ParaderoViewModelFactory(ObtenerParaderoCercanoUseCase(ParaderoRepositoryImpl()))
    }

    private val informacionViewModel: InformacionViewModel by viewModels {
        InformacionViewModelFactory(InformacionRepositoryImpl())
    }

    private val rutaViewModel: RutaViewModel by viewModels {
        RutaViewModelFactory()
    }

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

        paraderoViewModel.paraderoCercano.observe(viewLifecycleOwner) {
            mostrarParaderoEnMapa(it)
        }

        rutaViewModel.coordenadasRuta.observe(viewLifecycleOwner) { coordenadas ->
            snapToRoadsYMostrarRuta(coordenadas)
        }

        rutasDisponiblesViewModel.rutasDisponibles.observe(viewLifecycleOwner) { rutas ->
            configurarDropdown(rutas)
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
            }
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
        }
    }

    private fun configurarDropdown(rutas: List<Ruta>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, rutas)
        dropdownRutas.setAdapter(adapter)

        dropdownRutas.setOnItemClickListener { _, _, position, _ ->
            val rutaSeleccionada = rutas[position]
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

            rutaSeleccionadaActual = rutaSeleccionada
            googleMap.clear()
            marcadorParadero = null
            marcadorUsuario = null
            ultimaRutaHastaParadero?.remove()
            ultimaRutaHastaParadero = null
            paraderoActual = null

            rutaViewModel.obtenerRuta(rutaSeleccionada.id)
            informacionViewModel.obtenerInformacion(rutaSeleccionada.empresa.id)
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

                    val bounds = LatLngBounds.builder()
                    snappedLatLngs.forEach { bounds.include(it) }
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
