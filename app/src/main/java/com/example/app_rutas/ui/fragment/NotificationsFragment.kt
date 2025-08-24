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
import android.widget.ImageButton
import com.example.app_rutas.infrastructure.telemetry.TelemetryTracker

class NotificationsFragment : Fragment() {

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var rv: RecyclerView
    private lateinit var tvEmpty: TextView

    private val adapter = NotificationsAdapter()
    private val repo = NotificationsRepository()

    private val tracker by lazy { TelemetryTracker.get(requireContext()) }
    private fun currentUserId(): String? =
        requireContext().getSharedPreferences("rutas_prefs", Context.MODE_PRIVATE)
            .getLong("userId", -1L).takeIf { it > 0 }?.toString()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_notifications, container, false)

        v.findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            (activity as? MainActivity)?.toggleDrawer()
        }

        swipe = v.findViewById(R.id.swipe)
        rv = v.findViewById(R.id.rvNotifications)
        tvEmpty = v.findViewById(R.id.tvEmpty)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        adapter.onItemClick = { n ->
            tracker.buttonClick(
                "NotificationsFragment", "item_click", currentUserId(),
                detalles = mapOf("notifId" to (n.id ?: "null"), "isRead" to (n.isRead == true))
            )
            NotificationDetailBottomSheet.newInstance(n)
                .show(childFragmentManager, "notifDetail")
        }

        swipe.setOnRefreshListener {
            tracker.buttonClick("NotificationsFragment", "swipe_refresh", currentUserId(), null)
            loadData(markAsReadAfterLoad = false)
        }
        return v
    }

    override fun onResume() {
        super.onResume()
        tracker.screenView("NotificationsFragment", currentUserId())
        loadData(markAsReadAfterLoad = true)
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

                val unread = list.count { it.isRead == false && (dni != null && it.userId == dni) }
                tracker.buttonClick(
                    "NotificationsFragment", "load_success", currentUserId(),
                    detalles = mapOf("total" to list.size, "unread" to unread, "markAfterLoad" to markAsReadAfterLoad)
                )

                (activity as? MainActivity)?.refreshNotificationsBadge()

                if (markAsReadAfterLoad && !dni.isNullOrBlank() && unread > 0) {
                    try {
                        repo.markAllRead(dni)
                        tracker.buttonClick(
                            "NotificationsFragment", "mark_all_read", currentUserId(),
                            detalles = mapOf("unreadBefore" to unread)
                        )
                        (activity as? MainActivity)?.refreshNotificationsBadge()
                    } catch (e: Exception) {
                        tracker.error("NotificationsFragment", "mark_all_read", e.message ?: "error", currentUserId())
                    }
                }
            } catch (e: Exception) {
                tracker.error("NotificationsFragment", "load_error", e.message ?: "error", currentUserId())
                Toast.makeText(requireContext(), "Error al cargar notificaciones", Toast.LENGTH_SHORT).show()
            } finally {
                swipe.isRefreshing = false
            }
        }
    }
}
