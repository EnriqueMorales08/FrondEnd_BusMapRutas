package com.example.app_rutas.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.app_rutas.R
import com.example.app_rutas.domain.entities.Notificacion

class NotificationsAdapter : RecyclerView.Adapter<NotificationsAdapter.VH>() {

    private val data = mutableListOf<Notificacion>()
    var onItemClick: ((Notificacion) -> Unit)? = null

    fun submit(list: List<Notificacion>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val viewStripe: View = v.findViewById(R.id.viewStripe)
        val viewUnreadDot: View = v.findViewById(R.id.viewUnreadDot)
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvMessage: TextView = v.findViewById(R.id.tvMessage)
        val tvDate: TextView = v.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val n = data[pos]

        h.tvTitle.text = n.title ?: "Notificación"
        h.tvMessage.text = n.message ?: ""
        h.tvDate.text = (n.createdAt ?: "").replace('T', ' ').take(16)

        val stripeColor = when (n.type?.lowercase()) {
            "error" -> 0xFFF44336.toInt()
            "warn", "warning", "alert" -> 0xFFFFB300.toInt()
            else -> 0xFF1E88E5.toInt()
        }
        h.viewStripe.setBackgroundColor(stripeColor)

        val unread = (n.isRead == false)
        h.viewUnreadDot.visibility = if (unread) View.VISIBLE else View.GONE
        h.tvTitle.setTypeface(h.tvTitle.typeface, if (unread) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)


        h.itemView.setOnClickListener { onItemClick?.invoke(n) }
    }

    override fun getItemCount() = data.size
}
