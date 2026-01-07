package com.nxd1frnt.clockdesk2.music.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.music.ClockDeskMediaService
import java.util.Collections

class MusicSourcesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SourcesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            layoutManager = LinearLayoutManager(context)
        }
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

        val savedOrder = prefs.getString("music_provider_order", "system_media,lastfm") ?: "system_media,lastfm"
        val orderList = savedOrder.split(",").map { it.trim() }.toMutableList()

        val items = orderList.mapNotNull { id ->
            createPluginModel(id, prefs)
        }.toMutableList()

        adapter = SourcesAdapter(
            items = items,
            onClick = { pluginId ->
                when (pluginId) {
                    "lastfm" -> {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.settings_container, LastFmSettingsFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                    "system_media" -> {
                        checkAndRequestNotificationPermission()
                    }
                }
            },
            onSwitchChanged = { pluginId, isEnabled ->
                val key = getPrefKeyForPlugin(pluginId)
                prefs.edit().putBoolean(key, isEnabled).apply()

                if (pluginId == "system_media" && isEnabled) {
                    if (!isNotificationServiceEnabled()) {
                        checkAndRequestNotificationPermission()
                    }
                }
            }
        )

        recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPos = source.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false

                Collections.swap(items, fromPos, toPos)
                adapter.notifyItemMoved(fromPos, toPos)

                val newOrderString = items.joinToString(",") { it.id }
                prefs.edit().putString("music_provider_order", newOrderString).apply()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }


    private fun isNotificationServiceEnabled(): Boolean {
        val context = requireContext()
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":").toTypedArray()
            val myComponent = ComponentName(context, ClockDeskMediaService::class.java)
            for (name in names) {
                val component = ComponentName.unflattenFromString(name)
                if (component != null && component == myComponent) {
                    return true
                }
            }
        }
        return false
    }

    private fun checkAndRequestNotificationPermission() {
        if (isNotificationServiceEnabled()) {
            Toast.makeText(context, R.string.permission_already_granted, Toast.LENGTH_SHORT).show()
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.permission_required_title)
                .setMessage(R.string.permission_notification_explanation)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun createPluginModel(id: String, prefs: android.content.SharedPreferences): PluginUiModel? {
        val key = getPrefKeyForPlugin(id)
        val isEnabled = prefs.getBoolean(key, true)

        return when (id) {
            "lastfm" -> PluginUiModel(
                id,
                getString(R.string.lastfm_plugin_name),
                getString(R.string.lastfm_plugin_description),
                true,
                isEnabled
            )
            "system_media" -> PluginUiModel(
                id,
                getString(R.string.system_media_plugin_name),
                getString(R.string.system_media_plugin_description),
                true,
                isEnabled
            )
            else -> {
                PluginUiModel(id, id, "External Provider", false, isEnabled)
            }
        }
    }

    private fun getPrefKeyForPlugin(pluginId: String): String {
        return if (pluginId == "lastfm") "enable_lastfm" else "enable_$pluginId"
    }
}
data class PluginUiModel(
    val id: String,
    val name: String,
    val desc: String,
    val hasSettings: Boolean,
    var isEnabled: Boolean
)