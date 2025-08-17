package com.example.app_rutas.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import coil.load
import coil.transform.CircleCropTransformation
import com.example.app_rutas.R
import com.example.app_rutas.ui.LoginActivity
import com.google.android.material.navigation.NavigationView
import com.example.app_rutas.ui.fragment.MapsFragment
import com.example.app_rutas.ui.fragment.RutaPlanificadaFragment
import com.example.app_rutas.infrastructure.telemetry.TelemetryTracker
import com.google.android.material.imageview.ShapeableImageView

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener{

    private lateinit var drawerLayout: DrawerLayout

    private val tracker by lazy { TelemetryTracker.get(this) }
    private fun currentUserId(): String? = getSharedPreferences("rutas_prefs", MODE_PRIVATE).getString("usuario_id", null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("rutas_prefs", MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("isLoggedIn", false)

        if (!isLoggedIn) {
            //tracker.buttonClick("MainActivity", "redirectToLogin", null, detalles = mapOf("isLoggedIn" to false))
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        drawerLayout = findViewById(R.id.hotel_rese_)

        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        val header = navigationView.getHeaderView(0)
        val ivAvatar = header.findViewById<ShapeableImageView>(R.id.ivAvatar)
        val tvNombre = header.findViewById<TextView>(R.id.tvNombreHeader)
        val tvCorreo = header.findViewById<TextView>(R.id.tvCorreoHeader)

        val nombre = prefs.getString("nombre", "Usuario")
        val correo = prefs.getString("correo", "")
        val foto   = prefs.getString("fotoPerfil", null)

        tvNombre.text = nombre
        tvCorreo.text = correo

        ivAvatar.load(foto) {
            crossfade(true)
            placeholder(R.drawable.ic_avatar_placeholder)
            error(R.drawable.ic_avatar_placeholder)
            transformations(CircleCropTransformation())
        }

        val toggle = ActionBarDrawerToggle(this, drawerLayout, R.string.open, R.string.close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        if (savedInstanceState == null) {
            val open = intent.getStringExtra("open_fragment")
            if (open == "maps") {
                replaceFragment(MapsFragment())
                navigationView.setCheckedItem(R.id.nav_home)
            } else {
                replaceFragment(MapsFragment())
                navigationView.setCheckedItem(R.id.nav_home)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //tracker.screenView("MainActivity", currentUserId())
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        //tracker.buttonClick("MainActivity", "nav_item_click", currentUserId(), detalles = mapOf("itemId" to item.itemId, "title" to (item.title?.toString() ?: "")))
        when(item.itemId){
            R.id.nav_home -> replaceFragment(MapsFragment())
            R.id.nav_planificador -> replaceFragment(RutaPlanificadaFragment())
            //R.id.nav_contac -> replaceFragment(ContactFragment())
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun replaceFragment(fragment: Fragment){
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container,fragment)
        transaction.commit()

        //tracker.screenView(fragment::class.java.simpleName, currentUserId())
    }

    override fun onBackPressed() {
        val drawerWasOpen = drawerLayout.isDrawerOpen(GravityCompat.START)
        //tracker.buttonClick("MainActivity", "back_pressed", currentUserId(), detalles = mapOf("drawerOpen" to drawerWasOpen))
        super.onBackPressed()
        if(drawerLayout.isDrawerOpen(GravityCompat.START)){
            drawerLayout.closeDrawer(GravityCompat.START)
        }else{
            onBackPressedDispatcher.onBackPressed()
        }
    }
}
