package com.example.app_rutas.ui
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.app_rutas.R
import com.example.app_rutas.domain.usecases.UsuarioUseCase
import com.example.app_rutas.infrastructure.UsuarioUseCaseImpl
import com.example.app_rutas.infrastructure.repositories.UsuarioRepositoryImpl
import com.example.app_rutas.ui.activity.MainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var editTextDni: EditText
    private lateinit var btn_login: Button

    private val useCase: UsuarioUseCase = UsuarioUseCaseImpl(UsuarioRepositoryImpl())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editTextDni = findViewById(R.id.editTextDni)
        btn_login = findViewById(R.id.btn_login)

        btn_login.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))

            val prefs = getSharedPreferences("rutas_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("is_logged_in", true).apply()

// Ahora sí lanzas MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()

            /*
            val dni = editTextDni.text.toString()

            if (dni.length != 8) {
                Toast.makeText(this, "DNI inválido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

             ✅ Ejecutar dentro de una corrutina
            kotlinx.coroutines.GlobalScope.launch {
                val usuario = useCase.login(dni, "")

                runOnUiThread {
                    if (usuario != null) {
                        Toast.makeText(this@LoginActivity, "Bienvenido, ${usuario.nombre}", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "Usuario no aprobado o no existe", Toast.LENGTH_SHORT).show()
                    }
                }
            }*/
        }


    }
}
