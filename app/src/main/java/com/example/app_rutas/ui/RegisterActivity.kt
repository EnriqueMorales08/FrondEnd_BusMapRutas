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

        btnSeleccionarFoto.setOnClickListener { seleccionarImagen(PICK_FOTO_PERFIL) }
        btnSeleccionarDniFrente.setOnClickListener { seleccionarImagen(PICK_DNI_FRENTE) }
        btnSeleccionarDniReverso.setOnClickListener { seleccionarImagen(PICK_DNI_REVERSO) }
        btnRegistrar.setOnClickListener { validarYRegistrar() }

        lifecycleScope.launch {
            viewModel.resultado.collect { result ->
                progressBar.visibility = View.GONE
                if (result != null) {
                    result.onSuccess {
                        mostrarMensaje("Registro exitoso: ${it.nombre}")
                        irALogin()
                    }.onFailure {
                        mostrarMensaje("Error: ${it.message}")
                    }
                }
            }
        }
    }

    private fun seleccionarImagen(codigo: Int) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, codigo)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data
            val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)

            when (requestCode) {
                PICK_FOTO_PERFIL -> {
                    imagenUri = uri
                    imgFoto.setImageBitmap(bitmap)
                }
                PICK_DNI_FRENTE -> {
                    uriDniFrente = uri
                    imgDniAnverso.setImageBitmap(bitmap)
                }
                PICK_DNI_REVERSO -> {
                    uriDniReverso = uri
                    imgDniReverso.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun validarYRegistrar() {
        val usuario = etUsuario.text.toString().trim()
        val correo = etCorreo.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmarPassword = etConfirmarPassword.text.toString()
        val celular = etCelular.text.toString().trim()
        val dni = etDni.text.toString().trim()

        if (usuario.isEmpty() || correo.isEmpty() || password.isEmpty() || confirmarPassword.isEmpty()
            || celular.isEmpty() || dni.isEmpty()
        ) {
            mostrarMensaje("Por favor completa todos los campos")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            mostrarMensaje("Correo electrónico no válido")
            return
        }

        if (!Pattern.matches("^9[0-9]{8}$", celular)) {
            mostrarMensaje("Número de celular inválido (ej: 9XXXXXXXX)")
            return
        }

        if (password.length < 6) {
            mostrarMensaje("La contraseña debe tener al menos 6 caracteres")
            return
        }

        if (password != confirmarPassword) {
            mostrarMensaje("Las contraseñas no coinciden")
            return
        }

        if (imagenUri == null) {
            mostrarMensaje("Selecciona una foto de perfil")
            return
        }

        if (uriDniFrente == null || uriDniReverso == null) {
            mostrarMensaje("Selecciona las fotos del DNI (frontal y reverso)")
            return
        }

        progressBar.visibility = View.VISIBLE
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
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
