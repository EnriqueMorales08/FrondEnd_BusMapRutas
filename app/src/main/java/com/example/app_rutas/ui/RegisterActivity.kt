package com.example.app_rutas.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.app_rutas.R
import com.example.app_rutas.domain.entities.UsuarioRequest
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import com.example.app_rutas.infrastructure.telemetry.TelemetryTracker

class RegisterActivity : AppCompatActivity() {

    private lateinit var imgFoto: ImageView
    private lateinit var imgDniAnverso: ImageView
    private lateinit var imgDniReverso: ImageView
    private lateinit var etUsuario: EditText
    private lateinit var etCorreo: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmarPassword: EditText
    private lateinit var etCelular: EditText
    private lateinit var etDni: EditText
    private lateinit var btnRegistrar: Button
    private lateinit var btnSeleccionarFoto: Button
    private lateinit var btnSeleccionarDniFrente: Button
    private lateinit var btnSeleccionarDniReverso: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var viewModel: RegisterViewModel

    private val PICK_FOTO_PERFIL = 1
    private val PICK_DNI_FRENTE = 2
    private val PICK_DNI_REVERSO = 3

    private var imagenUri: Uri? = null
    private var uriDniFrente: Uri? = null
    private var uriDniReverso: Uri? = null

    private val tracker by lazy { TelemetryTracker.get(this) }
    private fun userIdOrNull(): String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        imgFoto = findViewById(R.id.imgFoto)
        imgDniAnverso = findViewById(R.id.imgDniAnverso)
        imgDniReverso = findViewById(R.id.imgDniReverso)
        etUsuario = findViewById(R.id.etUsuario)
        etCorreo = findViewById(R.id.etCorreo)
        etPassword = findViewById(R.id.etPassword)
        etConfirmarPassword = findViewById(R.id.etConfirmarPassword)
        etCelular = findViewById(R.id.etCelular)
        etDni = findViewById(R.id.etDni)
        btnRegistrar = findViewById(R.id.btnRegistrar)
        btnSeleccionarFoto = findViewById(R.id.btnSeleccionarFoto)
        btnSeleccionarDniFrente = findViewById(R.id.btnSeleccionarDniFrente)
        btnSeleccionarDniReverso = findViewById(R.id.btnSeleccionarDniReverso)
        progressBar = findViewById(R.id.progressBar)

        viewModel = ViewModelProvider(this, RegisterViewModelFactory())[RegisterViewModel::class.java]

        btnSeleccionarFoto.setOnClickListener {
            tracker.buttonClick("RegisterActivity", "btnSeleccionarFoto", userIdOrNull(),
                detalles = mapOf("action" to "pick_profile_photo"))
            seleccionarImagen(PICK_FOTO_PERFIL)
        }
        btnSeleccionarDniFrente.setOnClickListener {
            tracker.buttonClick("RegisterActivity", "btnSeleccionarDniFrente", userIdOrNull(),
                detalles = mapOf("action" to "pick_dni_front"))
            seleccionarImagen(PICK_DNI_FRENTE)
        }
        btnSeleccionarDniReverso.setOnClickListener {
            tracker.buttonClick("RegisterActivity", "btnSeleccionarDniReverso", userIdOrNull(),
                detalles = mapOf("action" to "pick_dni_back"))
            seleccionarImagen(PICK_DNI_REVERSO)
        }
        btnRegistrar.setOnClickListener {
            tracker.buttonClick("RegisterActivity", "btnRegistrar", userIdOrNull(),
                detalles = mapOf(
                    "hasFoto" to (imagenUri != null),
                    "hasDniFront" to (uriDniFrente != null),
                    "hasDniBack" to (uriDniReverso != null)
                ))
            validarYRegistrar()
        }

        lifecycleScope.launch {
            viewModel.resultado.collect { result ->
                progressBar.visibility = View.GONE
                if (result != null) {
                    result.onSuccess {
                        tracker.buttonClick("RegisterActivity", "register_success", userIdOrNull(),
                            detalles = mapOf("nombreLen" to it.nombre.length))
                        getSharedPreferences("rutas_prefs", MODE_PRIVATE).edit()
                            .putBoolean("is_registered", true)
                            .apply()
                        mostrarMensaje("Registro exitoso: ${it.nombre}")
                        irALogin()
                    }.onFailure {
                        tracker.error("RegisterActivity", "register_submit",
                            it.message ?: "registro_error", userIdOrNull())
                        mostrarMensaje("Error: ${it.message}")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.screenView("RegisterActivity", userIdOrNull())
    }

    private fun seleccionarImagen(codigo: Int) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, codigo)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && data != null) {
            try{
                val uri = data.data
                val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)

                when (requestCode) {
                    PICK_FOTO_PERFIL -> {
                        imagenUri = uri
                        imgFoto.setImageBitmap(bitmap)
                        tracker.buttonClick("RegisterActivity", "pick_result", userIdOrNull(),
                            detalles = mapOf("which" to "FOTO_PERFIL", "status" to "OK"))
                    }
                    PICK_DNI_FRENTE -> {
                        uriDniFrente = uri
                        imgDniAnverso.setImageBitmap(bitmap)
                        tracker.buttonClick("RegisterActivity", "pick_result", userIdOrNull(),
                            detalles = mapOf("which" to "DNI_FRENTE", "status" to "OK"))
                    }
                    PICK_DNI_REVERSO -> {
                        uriDniReverso = uri
                        imgDniReverso.setImageBitmap(bitmap)
                        tracker.buttonClick("RegisterActivity", "pick_result", userIdOrNull(),
                            detalles = mapOf("which" to "DNI_REVERSO", "status" to "OK"))
                    }
                }
            }catch (e: Exception){
                tracker.error("RegisterActivity", "pick_result", e.message ?: "pick_error", userIdOrNull())
            }
        }else if (resultCode != Activity.RESULT_OK) {
            tracker.buttonClick("RegisterActivity", "pick_cancelled", userIdOrNull(),
                detalles = mapOf("requestCode" to requestCode, "status" to "CANCELLED"))
        }
    }

    private fun validarYRegistrar() {
        val usuario = etUsuario.text.toString().trim()
        val correo = etCorreo.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmarPassword = etConfirmarPassword.text.toString()
        val celular = etCelular.text.toString().trim()
        val dni = etDni.text.toString().trim()

        fun fail(msg: String): Boolean {
            mostrarMensaje(msg)
            tracker.error("RegisterActivity", "btnRegistrar", "Validación: $msg", userIdOrNull())
            return false
        }

        if (usuario.isEmpty() || correo.isEmpty() || password.isEmpty() || confirmarPassword.isEmpty()
            || celular.isEmpty() || dni.isEmpty()
        ){ if (!fail("Por favor completa todos los campos")) return }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            if (!fail("Correo electrónico no válido")) return
        }

        if (!Pattern.matches("^9[0-9]{8}$", celular)) {
            if (!fail("Número de celular inválido (ej: 9XXXXXXXX)")) return
        }

        if (password.length < 6) {
            if (!fail("La contraseña debe tener al menos 6 caracteres")) return
        }

        if (password != confirmarPassword) {
            if (!fail("Las contraseñas no coinciden")) return
        }

        if (imagenUri == null) {
            if (!fail("Selecciona una foto de perfil")) return
        }

        if (uriDniFrente == null || uriDniReverso == null) {
            if (!fail("Selecciona las fotos del DNI (frontal y reverso)")) return
        }

        progressBar.visibility = View.VISIBLE

        tracker.buttonClick("RegisterActivity", "register_submit", userIdOrNull(),
            detalles = mapOf(
                "usuarioLen" to usuario.length,
                "correoDomain" to correo.substringAfter("@", "n/a"),
                "celularValid" to true,
                "dniLen" to dni.length
            )
        )

        registrarConFotos(usuario, correo, celular, password, dni)
    }

    private fun registrarConFotos(nombre: String, correo: String, celular: String, password: String, dni: String) {
        val usuario = UsuarioRequest(
            dni = dni,
            nombre = nombre,
            correo = correo,
            celular = celular,
            password = password
        )
        viewModel.registrar(
            dni = usuario.dni,
            nombre = usuario.nombre,
            correo = usuario.correo,
            celular = usuario.celular,
            password = usuario.password,
            fotoPerfil = imagenUri!!,
            dniFrontal = uriDniFrente!!,
            dniPosterior = uriDniReverso!!,
            contentResolver = contentResolver
        )
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
    }

    private fun irALogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
}
