package com.example.app_rutas.ui.notifications

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.example.app_rutas.R
import com.example.app_rutas.domain.entities.Notificacion
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

class NotificationDetailBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_NOTIF = "arg_notif"
        fun newInstance(n: Notificacion) = NotificationDetailBottomSheet().apply {
            arguments = Bundle().apply { putParcelable(ARG_NOTIF, n) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.bottomsheet_notification_detail, container, false)

        val tvTitle = v.findViewById<TextView>(R.id.tvTitle)
        val tvDate = v.findViewById<TextView>(R.id.tvDate)
        val tvMessage = v.findViewById<TextView>(R.id.tvMessage)
        val chipType = v.findViewById<Chip>(R.id.chipType)
        val btnClose = v.findViewById<Button>(R.id.btnClose)
        val btnShare = v.findViewById<Button>(R.id.btnShare)

        val n = requireArguments().getParcelable<Notificacion>(ARG_NOTIF)!!

        tvTitle.text = n.title ?: "Notificación"
        tvMessage.text = n.message ?: ""
        tvDate.text = (n.createdAt ?: "").replace('T',' ').take(16)
        chipType.text = (n.type ?: "info").uppercase()

        btnClose.setOnClickListener { dismiss() }
        btnShare.setOnClickListener {
            val text = buildString {
                appendLine(n.title ?: "")
                appendLine()
                appendLine(n.message ?: "")
                appendLine()
                append("Fecha: ${tvDate.text}")
            }
            startActivity(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            })
        }

        return v
    }
}
