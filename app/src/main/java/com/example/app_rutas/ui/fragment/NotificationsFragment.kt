package com.example.app_rutas.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.app_rutas.R
import com.example.app_rutas.domain.repositories.NotificationsRepository
import com.example.app_rutas.ui.activity.MainActivity
import com.example.app_rutas.ui.adapters.NotificationsAdapter
import com.example.app_rutas.ui.notifications.NotificationDetailBottomSheet
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView

    private val adapter = NotificationsAdapter()
    private val repo = NotificationsRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_notifications, container, false)
        swipe = v.findViewById(R.id.swipe)
        rv = v.findViewById(R.id.rvNotifications)
        tvEmpty = v.findViewById(R.id.tvEmpty)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Abrir detalle
        adapter.onItemClick = { n ->
            NotificationDetailBottomSheet.newInstance(n)
                .show(childFragmentManager, "notifDetail")
        }

        swipe.setOnRefreshListener { loadData(markAsReadAfterLoad = false) }
        return v
    }

    override fun onResume() {
        super.onResume()
        loadData(markAsReadAfterLoad = true) // si quieres que se marquen como leídas al entrar
    }

    private fun loadData(markAsReadAfterLoad: Boolean) {
        swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val prefs = requireContext().getSharedPreferences("rutas_prefs", Context.MODE_PRIVATE)
            val dni = prefs.getString("dni", null)
            try {
                val list = repo.listForUserIncludingGlobal(dni)
                adapter.submit(list)
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

                (activity as? MainActivity)?.refreshNotificationsBadge()

                if (markAsReadAfterLoad && !dni.isNullOrBlank() && list.any { it.isRead == false && it.userId == dni }) {
                    try {
                        repo.markAllRead(dni)
                        (activity as? MainActivity)?.refreshNotificationsBadge()
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al cargar notificaciones", Toast.LENGTH_SHORT).show()
            } finally {
                swipe.isRefreshing = false
            }
        }
    }
}
