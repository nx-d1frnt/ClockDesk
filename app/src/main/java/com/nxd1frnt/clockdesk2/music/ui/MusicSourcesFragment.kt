package com.nxd1frnt.clockdesk2.music.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Bundle
import android.util.Log
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
import com.nxd1frnt.clockdesk2.music.ExternalPluginContract
import com.nxd1frnt.clockdesk2.utils.Logger
import java.util.Collections

class MusicSourcesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SourcesAdapter
    private data class PluginInfo(
        val id: String,
        val name: String,
        val desc: String,
        val settingsActivityClassName: String? = null
    )

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

        val availablePluginsMap = loadAvailablePluginsMap()

        val savedOrderString = prefs.getString("music_provider_order", "system_media,lastfm") ?: "system_media,lastfm"
        val savedOrderList = savedOrderString.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val finalIdList = ArrayList<String>()

        for (id in savedOrderList) {
            if (availablePluginsMap.containsKey(id)) {
                finalIdList.add(id)
            }
        }

        for (id in availablePluginsMap.keys) {
            if (!finalIdList.contains(id)) {
                finalIdList.add(id)
            }
        }

        val items = finalIdList.mapNotNull { id ->
            val info = availablePluginsMap[id] ?: return@mapNotNull null
            val key = getPrefKeyForPlugin(id)
            val isEnabled = prefs.getBoolean(key, true)

            PluginUiModel(
                id = info.id,
                name = info.name,
                desc = info.desc,
                settingsActivityClassName = info.settingsActivityClassName,
                isEnabled = isEnabled
            )
        }.toMutableList()

        val newOrderString = items.joinToString(",") { it.id }
        if (newOrderString != savedOrderString) {
            prefs.edit().putString("music_provider_order", newOrderString).apply()
        }

        adapter = SourcesAdapter(
            items = items,
            onClick = { pluginId ->
                val info = availablePluginsMap[pluginId]

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
                    else -> {
                        if (info?.settingsActivityClassName != null) {
                            try {
                                val intent = Intent()
                                intent.component = ComponentName(pluginId, info.settingsActivityClassName)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open settings", Toast.LENGTH_SHORT).show()
                                Logger.e("MusicSources"){"Error launching settings for $pluginId"}
                            }
                        }
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

                val newOrder = items.joinToString(",") { it.id }
                prefs.edit().putString("music_provider_order", newOrder).apply()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun loadAvailablePluginsMap(): Map<String, PluginInfo> {
        val map = mutableMapOf<String, PluginInfo>()

        map["system_media"] = PluginInfo(
            "system_media",
            getString(R.string.system_media_plugin_name),
            getString(R.string.system_media_plugin_description),
            settingsActivityClassName = null
        )
        map["lastfm"] = PluginInfo(
            "lastfm",
            getString(R.string.lastfm_plugin_name),
            getString(R.string.lastfm_plugin_description),
            settingsActivityClassName = null
        )

        val pm = requireContext().packageManager
        val queryIntent = Intent(ExternalPluginContract.ACTION_MUSIC_PLUGIN_SERVICE)
        val resolveInfos = pm.queryIntentServices(queryIntent, PackageManager.GET_META_DATA)

        for (resolveInfo in resolveInfos) {
            val serviceInfo = resolveInfo.serviceInfo ?: continue
            val packageName = serviceInfo.packageName

            if (map.containsKey(packageName)) continue

            var displayName = serviceInfo.loadLabel(pm).toString()
            var description = "External Music Provider"
            var settingsActivity: String? = null

            val metaData = serviceInfo.metaData
            if (metaData != null && metaData.containsKey(ExternalPluginContract.META_DATA_PLUGIN_INFO)) {
                val resId = metaData.getInt(ExternalPluginContract.META_DATA_PLUGIN_INFO)
                try {
                    val pluginRes = pm.getResourcesForApplication(packageName)
                    val parser = pluginRes.getXml(resId)
                    var eventType = parser.eventType
                    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "music-plugin") {
                            val nameAttr = parser.getAttributeValue(null, "displayName")
                            val descAttr = parser.getAttributeValue(null, "description")
                            val settingsAttr = parser.getAttributeValue(null, "settingsActivity")

                            if (!nameAttr.isNullOrEmpty()) displayName = nameAttr
                            if (!descAttr.isNullOrEmpty()) description = descAttr
                            if (!settingsAttr.isNullOrEmpty()) settingsActivity = settingsAttr
                        }
                        eventType = parser.next()
                    }
                } catch (e: Exception) {
                    Logger.e("MusicSourcesFragment"){"Failed to parse plugin info for $packageName"}
                }
            }

            map[packageName] = PluginInfo(
                packageName,
                displayName,
                description,
                settingsActivityClassName = settingsActivity
            )
        }
        return map
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

    private fun getPrefKeyForPlugin(pluginId: String): String {
        return if (pluginId == "lastfm") "enable_lastfm" else "enable_$pluginId"
    }
}

data class PluginUiModel(
    val id: String,
    val name: String,
    val desc: String,
    val settingsActivityClassName: String? = null,
    var isEnabled: Boolean,
) {
    val hasSettings: Boolean
        get() = settingsActivityClassName != null || id == "lastfm" || id == "system_media"
}