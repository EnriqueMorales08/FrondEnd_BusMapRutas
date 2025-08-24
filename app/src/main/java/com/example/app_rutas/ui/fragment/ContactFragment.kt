package com.example.app_rutas.ui.fragment

import android.content.ActivityNotFoundException
import android.content.Context
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
import com.example.app_rutas.infrastructure.telemetry.TelemetryTracker

class ContactFragment : Fragment() {

    private lateinit var etNombre: TextInputEditText
    private lateinit var etCorreo: TextInputEditText
    private lateinit var etAsunto: TextInputEditText
    private lateinit var etMensaje: TextInputEditText

    private val soporteEmail = "soporte@busmappiura.com"
    private val telefonoSoporte = "51926386534"
    private val whatsappSoporte = "51926386534"

    private val tracker by lazy { TelemetryTracker.get(requireContext()) }
    private fun currentUserId(): String? =
        requireContext().getSharedPreferences("rutas_prefs", Context.MODE_PRIVATE)
            .getLong("userId", -1L).takeIf { it > 0 }?.toString()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        val v = inflater.inflate(R.layout.fragment_contact, container, false)

        v.findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            (activity as? MainActivity)?.toggleDrawer()
        }

        val tvHeaderTitle = v.findViewById<TextView>(R.id.tvHeaderTitle)
        val tvHeaderSubtitle = v.findViewById<TextView>(R.id.tvHeaderSubtitle)

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

    override fun onResume() {
        super.onResume()
        tracker.screenView("ContactFragment", currentUserId())
    }

    private fun enviarCorreo() {
        val nombre = etNombre.text?.toString()?.trim().orEmpty()
        val correo = etCorreo.text?.toString()?.trim().orEmpty()
        val asunto = etAsunto.text?.toString()?.trim().orEmpty()
        val mensaje = etMensaje.text?.toString()?.trim().orEmpty()

        if (mensaje.isEmpty()) {
            tracker.error("ContactFragment", "btnEnviarCorreo", "Validación: mensaje vacío", currentUserId())
            Toast.makeText(requireContext(), "Escribe tu mensaje", Toast.LENGTH_SHORT).show()
            return
        }

        tracker.buttonClick(
            "ContactFragment", "btnEnviarCorreo", currentUserId(),
            detalles = mapOf(
                "hasNombre" to nombre.isNotEmpty(),
                "hasCorreo" to correo.isNotEmpty(),
                "subjectBlank" to asunto.isBlank(),
                "messageLen" to mensaje.length
            )
        )

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
            tracker.buttonClick("ContactFragment", "email_intent_opened", currentUserId(), null)
        } catch (e: ActivityNotFoundException) {
            tracker.error("ContactFragment", "btnEnviarCorreo", "No email apps: ${e.message}", currentUserId())
            Toast.makeText(requireContext(), "No hay apps de correo instaladas", Toast.LENGTH_SHORT).show()
        }catch (e: Exception) {
            tracker.error("ContactFragment", "btnEnviarCorreo", "Excepción: ${e.message}", currentUserId())
            Toast.makeText(requireContext(), "Error al abrir email", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirWhatsApp() {
        val texto = etMensaje.text?.toString()?.takeIf { it.isNotBlank() } ?: "Hola, necesito ayuda con Bus Map Piura."
        val url = "https://wa.me/$whatsappSoporte?text=${URLEncoder.encode(texto, "UTF-8")}"

        tracker.buttonClick(
            "ContactFragment", "btnWhatsApp", currentUserId(),
            detalles = mapOf("hasMessage" to (etMensaje.text?.isNotBlank() == true), "msgLen" to texto.length)
        )

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            tracker.buttonClick("ContactFragment", "whatsapp_intent_opened", currentUserId(), null)
        } catch (e: ActivityNotFoundException) {
            tracker.error("ContactFragment", "btnWhatsApp", "WhatsApp no disponible", currentUserId())
            Toast.makeText(requireContext(), "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            tracker.error("ContactFragment", "btnWhatsApp", "Excepción: ${e.message}", currentUserId())
            Toast.makeText(requireContext(), "Error al abrir WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun llamar() {
        tracker.buttonClick(
            "ContactFragment", "btnLlamar", currentUserId(),
            detalles = mapOf("phone" to telefonoSoporte)
        )
        val uri = Uri.parse("tel:$telefonoSoporte")
        try {
            startActivity(Intent(Intent.ACTION_DIAL, uri))
            tracker.buttonClick("ContactFragment", "dialer_opened", currentUserId(), null)
        } catch (e: Exception) {
            tracker.error("ContactFragment", "btnLlamar", "Excepción: ${e.message}", currentUserId())
            Toast.makeText(requireContext(), "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show()
        }
    }
}
