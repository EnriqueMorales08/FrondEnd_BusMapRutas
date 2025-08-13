package com.example.app_rutas.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.app_rutas.R
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
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
            viewModel.registroExitoso.collect { exitoso ->
                if (exitoso != null) {
                    progressBar.visibility = View.GONE
                    if (exitoso) {
                        mostrarMensaje("Registro exitoso")
                        irALogin()
                    } else {
                        mostrarMensaje("Error al registrar usuario")
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
            val uri = data.data ?: return
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)

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
        val nombre = etUsuario.text.toString().trim()
        val correo = etCorreo.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmarPassword = etConfirmarPassword.text.toString()
        val celular = etCelular.text.toString().trim()
        val dni = etDni.text.toString().trim()

        if (nombre.isEmpty() || correo.isEmpty() || password.isEmpty() ||
            confirmarPassword.isEmpty() || celular.isEmpty() || dni.isEmpty()
        ) {
            mostrarMensaje("Completa todos los campos")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            mostrarMensaje("Correo inválido")
            return
        }

        if (!Pattern.matches("^9[0-9]{8}$", celular)) {
            mostrarMensaje("Celular inválido (ej: 9XXXXXXXX)")
            return
        }

        if (password.length < 6 || password != confirmarPassword) {
            mostrarMensaje("Las contraseñas no coinciden o son cortas")
            return
        }

        if (imagenUri == null || uriDniFrente == null || uriDniReverso == null) {
            mostrarMensaje("Selecciona todas las imágenes")
            return
        }

        progressBar.visibility = View.VISIBLE

        val nombrePart = nombre.toRequestBody("text/plain".toMediaTypeOrNull())
        val correoPart = correo.toRequestBody("text/plain".toMediaTypeOrNull())
        val celularPart = celular.toRequestBody("text/plain".toMediaTypeOrNull())
        val passwordPart = password.toRequestBody("text/plain".toMediaTypeOrNull())
        val dniPart = dni.toRequestBody("text/plain".toMediaTypeOrNull())
        val estadoPart = "false".toRequestBody("text/plain".toMediaTypeOrNull())

        val fotoPerfilPart = crearPartDeUri(imagenUri!!, "fotoPerfil")
        val dniFrontalPart = crearPartDeUri(uriDniFrente!!, "dniFrontal")
        val dniPosteriorPart = crearPartDeUri(uriDniReverso!!, "dniPosterior")

        viewModel.registrarUsuario(
            nombrePart,
            correoPart,
            celularPart,
            passwordPart,
            dniPart,
            estadoPart,
            fotoPerfilPart,
            dniFrontalPart,
            dniPosteriorPart
        )
    }

    private fun crearPartDeUri(uri: Uri, nombreCampo: String): MultipartBody.Part {
        val inputStream = contentResolver.openInputStream(uri)!!
        val archivo = File.createTempFile(nombreCampo, ".jpg", cacheDir)
        val outputStream = FileOutputStream(archivo)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()

        val requestFile = archivo.asRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(nombreCampo, archivo.name, requestFile)
    }

    private fun mostrarMensaje(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
    }

    private fun irALogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
