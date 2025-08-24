package com.example.app_rutas.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentTransaction
import coil.request.ImageRequest
import okhttp3.Credentials
import coil.imageLoader
import coil.transform.CircleCropTransformation
import com.example.app_rutas.R
import com.example.app_rutas.ui.LoginActivity
import com.google.android.material.navigation.NavigationView
import com.example.app_rutas.ui.fragment.MapsFragment
import com.example.app_rutas.ui.fragment.ContactFragment
import com.example.app_rutas.ui.fragment.RutaPlanificadaFragment
import com.example.app_rutas.infrastructure.telemetry.TelemetryTracker
import com.google.android.material.imageview.ShapeableImageView
import androidx.lifecycle.lifecycleScope
import com.example.app_rutas.domain.repositories.NotificationsRepository
import com.example.app_rutas.ui.fragment.NotificationsFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener{

    private lateinit var drawerLayout: DrawerLayout

    private val tracker by lazy { TelemetryTracker.get(this) }

    private fun currentUserId(): String? =
        getSharedPreferences("rutas_prefs", MODE_PRIVATE)
            .getLong("userId", -1L).takeIf { it > 0 }?.toString()
    private var badgeText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("rutas_prefs", MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("isLoggedIn", false)

        if (!isLoggedIn) {
            tracker.buttonClick("MainActivity", "redirectToLogin", currentUserId(), detalles = mapOf("isLoggedIn" to false))
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

        val request = ImageRequest.Builder(this)
            .data(foto)
            .crossfade(true)
            .placeholder(R.drawable.ic_avatar_placeholder)
            .error(R.drawable.ic_avatar_placeholder)
            .transformations(CircleCropTransformation())
            .addHeader(
                "Authorization",
                Credentials.basic("gustavo", "busmappiura")
            )
            .target(ivAvatar)
            .build()

        ivAvatar.context.imageLoader.enqueue(request)

        val toggle = ActionBarDrawerToggle(this, drawerLayout, R.string.open, R.string.close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupNotificationsBadge(navigationView)
        refreshNotificationsBadge()

        if (savedInstanceState == null) {
            val open = intent.getStringExtra("open_fragment")
            tracker.buttonClick("MainActivity", "init_fragment", currentUserId(),
                detalles = mapOf("open_fragment" to (open ?: "default")))
            if (open == "maps") {
                replaceFragment(MapsFragment())
                navigationView.setCheckedItem(R.id.nav_home)
            } else {
                replaceFragment(MapsFragment())
                navigationView.setCheckedItem(R.id.nav_home)
            }
        }

        // Log automático de SCREEN_VIEW cuando un Fragment entra a onResume()
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    tracker.screenView(f::class.java.simpleName, currentUserId())
                }
            },
            /* recursive = */ true
        )
    }

    override fun onResume() {
        super.onResume()
        tracker.screenView("MainActivity", currentUserId())
        refreshNotificationsBadge()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        tracker.buttonClick(
            "MainActivity", "nav_item_click", currentUserId(),
            detalles = mapOf("itemId" to item.itemId, "title" to (item.title?.toString() ?: ""))
        )
        when(item.itemId){
            R.id.nav_home -> replaceFragment(MapsFragment())
            R.id.nav_planificador -> replaceFragment(RutaPlanificadaFragment())
            R.id.nav_contacto -> replaceFragment(ContactFragment())
            R.id.nav_notifications -> replaceFragment(NotificationsFragment())
            R.id.nav_logout -> {
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Cerrar sesión")
                    .setMessage("¿Estas seguro de cerrar sesión?")
                    .setPositiveButton("Sí") { _, _ ->
                        tracker.buttonClick("MainActivity", "logout_confirmed", currentUserId(), null)
                        logout()
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        tracker.buttonClick("MainActivity", "logout_cancelled", currentUserId(), null)
                    }
                    .create()

                dialog.setOnShowListener {
                    tracker.buttonClick("MainActivity", "logout_dialog_show", currentUserId(), null)
                }
                dialog.show()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun replaceFragment(fragment: Fragment){
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container,fragment)
        transaction.commit()
        tracker.screenView(fragment::class.java.simpleName, currentUserId())
    }

    override fun onBackPressed() {
        val drawerWasOpen = drawerLayout.isDrawerOpen(GravityCompat.START)
        tracker.buttonClick("MainActivity", "back_pressed", currentUserId(), detalles = mapOf("drawerOpen" to drawerWasOpen))
        super.onBackPressed()
        if(drawerLayout.isDrawerOpen(GravityCompat.START)){
            drawerLayout.closeDrawer(GravityCompat.START)
        }else{
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun logout() {
        val prefs = getSharedPreferences("rutas_prefs", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("isLoggedIn", false)
            .remove("userId")
            .remove("dni")
            .remove("nombre")
            .remove("correo")
            .remove("fotoPerfil")
            .apply()

        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun setupNotificationsBadge(navigationView: NavigationView) {
        val menuItem = navigationView.menu.findItem(R.id.nav_notifications)
        val actionView = menuItem.actionView
        badgeText = actionView?.findViewById(R.id.tvBadge)
        actionView?.setOnClickListener { onNavigationItemSelected(menuItem) }
    }

    fun refreshNotificationsBadge() {
        val prefs = getSharedPreferences("rutas_prefs", MODE_PRIVATE)
        val dni = prefs.getString("dni", null)

        lifecycleScope.launch {
            try {
                val repo = NotificationsRepository()
                val list = repo.listForUserIncludingGlobal(dni)
                val unread = list.count { it.isRead == false && (dni != null && it.userId == dni) }
                tracker.buttonClick(
                    "MainActivity", "notifications_badge_refresh", currentUserId(),
                    detalles = mapOf("unread" to unread, "hasDni" to (dni != null))
                )
                if (unread > 0) {
                    badgeText?.text = if (unread > 99) "99+" else unread.toString()
                    badgeText?.visibility = View.VISIBLE
                } else {
                    badgeText?.visibility = View.GONE
                }
            } catch (_: Exception) {
                tracker.error("MainActivity", "notifications_badge_refresh", "fetch_error", currentUserId())
                badgeText?.visibility = View.GONE
            }
        }
    }

    fun toggleDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            tracker.buttonClick("MainActivity", "drawer_toggle", currentUserId(), detalles = mapOf("action" to "close"))
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            tracker.buttonClick("MainActivity", "drawer_toggle", currentUserId(), detalles = mapOf("action" to "close"))
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }
}
