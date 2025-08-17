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
import com.example.app_rutas.ui.activity.MainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var editTextDni: EditText
    private lateinit var editPassword: EditText
    private lateinit var btn_login: Button
    private val repo = UsuarioLoginRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editTextDni = findViewById(R.id.editTextDni)
        editPassword = findViewById(R.id.editPassword)
        btn_login = findViewById(R.id.btn_login)

        btn_login.setOnClickListener { intentarLogin() }
    }

    private fun intentarLogin() {
        val dniInput = editTextDni.text.toString().trim()
        val passInput = editPassword.text.toString()

        if (dniInput.isEmpty() || passInput.isEmpty()) {
            Toast.makeText(this, "Completa DNI y contraseña", Toast.LENGTH_SHORT).show()
            return
        }

        btn_login.isEnabled = false

        lifecycleScope.launch {
            try {
                val users = repo.listarUsuarios()
                val user = users.firstOrNull { it.dni.trim() == dniInput }

                if (user == null) {
                    showError("DNI no encontrado")
                    clearAllFields()
                    return@launch
                }

                if (!user.estado) {
                    showError("Tu cuenta aún no ha sido validada")
                    clearAllFields()
                    return@launch
                }

                val ok = BCrypt.verifyer()
                    .verify(passInput.toCharArray(), user.password.toCharArray())
                    .verified

                if (!ok) {
                    showError("Contraseña incorrecta")
                    clearPassword()
                    return@launch
                }

                val prefs = getSharedPreferences("rutas_prefs", MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("isLoggedIn", true)
                    .putLong("userId", user.id)
                    .putString("dni", user.dni)
                    .putString("nombre", user.nombre)
                    .putString("correo", user.correo)
                    .putString("fotoPerfil", user.fotoPerfil)
                    .apply()

                startActivity(
                    Intent(this@LoginActivity, MainActivity::class.java)
                        .putExtra("open_fragment", "maps")
                )
                finish()

            } catch (e: Exception) {
                showError("Error de red: ${e.message ?: "intenta nuevamente"}")
            } finally {
                btn_login.isEnabled = true
            }
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
