package com.example.app_rutas.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class StartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("rutas_prefs", MODE_PRIVATE)
        val isRegistered = prefs.getBoolean("is_registered", false)

        val next = when {
            isRegistered -> Intent(this, LoginActivity::class.java)
            else -> Intent(this, RegisterActivity::class.java)
        }

        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(next)
        overridePendingTransition(0, 0)
        finish()
    }
}
