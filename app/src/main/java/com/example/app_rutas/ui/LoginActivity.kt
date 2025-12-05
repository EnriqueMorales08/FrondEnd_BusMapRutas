package com.example.app_rutas.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import at.favre.lib.crypto.bcrypt.BCrypt
import com.example.app_rutas.R
import com.example.app_rutas.infrastructure.repositories.UsuarioLoginRepository
import com.example.app_rutas.ui.common.LoadingDialogFragment
import com.example.app_rutas.ui.activity.MainActivity
import android.os.SystemClock
import com.example.app_rutas.application.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.app_rutas.infrastructure.telemetry.TelemetryTracker

class LoginActivity : AppCompatActivity() {

    private var loading: LoadingDialogFragment? = null
    private var navigating = false
    private lateinit var editTextDni: EditText
    private lateinit var editPassword: EditText
    private lateinit var btn_login: Button
    private lateinit var btn_go_register: Button
    private val repo = UsuarioLoginRepository()

    private val tracker by lazy { TelemetryTracker.get(this) }
    private fun currentUserId(): String? =
        getSharedPreferences("rutas_prefs", MODE_PRIVATE)
            .getLong("userId", -1L)
            .takeIf { it > 0 }?.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editTextDni = findViewById(R.id.editTextDni)
        editPassword = findViewById(R.id.editPassword)
        btn_login = findViewById(R.id.btn_login)
        btn_go_register = findViewById(R.id.btn_go_register)

        btn_login.setOnClickListener { intentarLogin() }

        btn_go_register.setOnClickListener {
            tracker.buttonClick(
                activity = "LoginActivity",
                componente = "btn_go_register",
                usuarioId = currentUserId(),
                detalles = mapOf("action" to "open_register")
            )

            val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.screenView("LoginActivity", currentUserId())
    }

    private fun intentarLogin() {
        val dniInput = editTextDni.text.toString().trim()
        val passInput = editPassword.text.toString()

        tracker.buttonClick(
            activity = "LoginActivity",
            componente = "btn_login",
            usuarioId = currentUserId(),
            detalles = mapOf("dniLen" to dniInput.length, "hasPassword" to passInput.isNotEmpty())
        )

        if (dniInput.isEmpty() || passInput.isEmpty()) {
            tracker.error("LoginActivity", "btn_login", "Validación: campos vacíos", currentUserId())
            Toast.makeText(this, "Completa DNI y contraseña", Toast.LENGTH_SHORT).show()
            return
        }

        btn_login.isEnabled = false
        navigating = false

        lifecycleScope.launch {
            try {
                val abierto = try {
                    val resp = RetrofitClient.adminApi.getEstadoServicio()
                    resp.abierto
                } catch (e: Exception) {
                    false
                }

                if (!abierto) {
                    tracker.error("LoginActivity", "btn_login", "Servicio no disponible", currentUserId())
                    showError("El servicio no está disponible en este momento. Inténtalo más tarde.")
                    return@launch
                }

                val t0 = SystemClock.elapsedRealtime()
                val users = repo.listarUsuarios()
                val user = users.firstOrNull { it.dni.trim() == dniInput }
                val t1 = SystemClock.elapsedRealtime()

                if (user == null) {
                    tracker.error("LoginActivity", "btn_login", "DNI no encontrado", null)
                    showError("DNI no encontrado"); clearAllFields(); return@launch
                }
                if (user.estado != true) {
                    tracker.error("LoginActivity", "btn_login", "Cuenta no validada", user.id.toString())
                    showError("Tu cuenta aún no ha sido validada"); clearAllFields(); return@launch
                }

                val ok = BCrypt.verifyer()
                    .verify(passInput.toCharArray(), user.password.toCharArray())
                    .verified

                if (!ok) {
                    tracker.error("LoginActivity", "btn_login", "Contraseña incorrecta", user.id.toString())
                    showError("Contraseña incorrecta"); clearPassword(); return@launch
                }

                showLoading(true)
                val start = SystemClock.elapsedRealtime()

                getSharedPreferences("rutas_prefs", MODE_PRIVATE).edit()
                    .putBoolean("isLoggedIn", true)
                    .putLong("userId", user.id)
                    .putString("dni", user.dni)
                    .putString("nombre", user.nombre)
                    .putString("correo", user.correo)
                    .putString("fotoPerfil", user.fotoPerfil)
                    .apply()

                tracker.newSession()
                tracker.buttonClick(
                    activity = "LoginActivity",
                    componente = "login_success",
                    usuarioId = user.id.toString(),
                    detalles = mapOf(
                        "lookupMs" to (t1 - t0),
                        "minShowMs" to 700
                    )
                )

                val minShowMs = 700L
                val elapsed = SystemClock.elapsedRealtime() - start
                if (elapsed < minShowMs) delay(minShowMs - elapsed)

                showLoading(false)

                startActivity(
                    Intent(this@LoginActivity, MainActivity::class.java)
                        .putExtra("open_fragment", "maps")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )

            } catch (e: Exception) {
                tracker.error("LoginActivity", "btn_login", "Excepción: ${e.message ?: "unknown"}", currentUserId())
                showError("Error de red: ${e.message ?: "intenta nuevamente"}")
            } finally {
                btn_login.isEnabled = true
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            if (loading?.isAdded != true) {
                loading = LoadingDialogFragment.newInstance()
                loading?.show(supportFragmentManager, "loading")
            }
        } else {
            loading?.dismissAllowingStateLoss()
            loading = null
        }
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        btn_login.isEnabled = true
    }

    private fun clearPassword() {
        editPassword.text?.clear()
        editPassword.requestFocus()
    }

    private fun clearAllFields() {
        editTextDni.text?.clear()
        editPassword.text?.clear()
        editTextDni.requestFocus()
    }

}
