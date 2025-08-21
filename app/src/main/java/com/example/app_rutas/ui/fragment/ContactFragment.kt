package com.example.app_rutas.ui.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.app_rutas.R
import com.example.app_rutas.ui.activity.MainActivity
import com.google.android.material.textfield.TextInputEditText
import java.net.URLEncoder
import android.widget.TextView

class ContactFragment : Fragment() {

    private lateinit var etNombre: TextInputEditText
    private lateinit var etCorreo: TextInputEditText
    private lateinit var etAsunto: TextInputEditText
    private lateinit var etMensaje: TextInputEditText

    // 🔧 Cambia estos valores a los de tu proyecto
    private val soporteEmail = "soporte@busmappiura.com"
    private val telefonoSoporte = "51926386534"
    private val whatsappSoporte = "51926386534"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        val v = inflater.inflate(R.layout.fragment_contact, container, false)

        v.findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            (activity as? MainActivity)?.toggleDrawer()
        }

        val tvHeaderTitle = v.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvHeaderSubtitle = v.findViewById<TextView>(R.id.tvHeaderSubtitle)

        // 2) Personaliza el saludo
        val prefs = requireContext().getSharedPreferences("rutas_prefs", android.content.Context.MODE_PRIVATE)
        val nombre = prefs.getString("nombre", "") ?: ""
        val first = nombre.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

        tvHeaderTitle.text = if (first.isNotBlank()) "Hola, $first 👋" else "Hola 👋"
        tvHeaderSubtitle.text = "Cuéntanos tu consulta. Respondemos en menos de 24 h."

        etNombre = v.findViewById(R.id.etNombre)
        etCorreo = v.findViewById(R.id.etCorreo)
        etAsunto = v.findViewById(R.id.etAsunto)
        etMensaje = v.findViewById(R.id.etMensaje)

        // Prefill desde preferencias
        etNombre.setText(prefs.getString("nombre", "") ?: "")
        etCorreo.setText(prefs.getString("correo", "") ?: "")

        v.findViewById<View>(R.id.btnEnviarCorreo).setOnClickListener { enviarCorreo() }
        v.findViewById<View>(R.id.btnWhatsApp).setOnClickListener { abrirWhatsApp() }
        v.findViewById<View>(R.id.btnLlamar).setOnClickListener { llamar() }

        return v
    }

    private fun enviarCorreo() {
        val nombre = etNombre.text?.toString()?.trim().orEmpty()
        val correo = etCorreo.text?.toString()?.trim().orEmpty()
        val asunto = etAsunto.text?.toString()?.trim().orEmpty()
        val mensaje = etMensaje.text?.toString()?.trim().orEmpty()

        if (mensaje.isEmpty()) {
            Toast.makeText(requireContext(), "Escribe tu mensaje", Toast.LENGTH_SHORT).show()
            return
        }

        val subject = if (asunto.isBlank()) "Consulta - Bus Map Piura" else asunto
        val cuerpo = buildString {
            appendLine("Nombre: $nombre")
            appendLine("Correo: $correo")
            appendLine()
            appendLine(mensaje)
        }

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:") // obliga a clientes de correo
            putExtra(Intent.EXTRA_EMAIL, arrayOf(soporteEmail))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, cuerpo)
        }

        try {
            startActivity(Intent.createChooser(intent, "Enviar correo"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No hay apps de correo instaladas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirWhatsApp() {
        val texto = etMensaje.text?.toString()?.takeIf { it.isNotBlank() } ?: "Hola, necesito ayuda con Bus Map Piura."
        val url = "https://wa.me/$whatsappSoporte?text=${URLEncoder.encode(texto, "UTF-8")}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun llamar() {
        // No requiere permiso; abre el marcador
        val uri = Uri.parse("tel:$telefonoSoporte")
        startActivity(Intent(Intent.ACTION_DIAL, uri))
    }
}
