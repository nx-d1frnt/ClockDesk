package com.nxd1frnt.clockdesk2.music

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.nxd1frnt.clockdesk2.music.plugins.ExternalMusicPlugin
import com.nxd1frnt.clockdesk2.music.plugins.LastFmPlugin
import com.nxd1frnt.clockdesk2.music.plugins.SystemSessionPlugin

class MusicPluginManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val onUpdate: (PluginState) -> Unit
) {
    private val plugins = mutableMapOf<String, IMusicPlugin>()
    private val pluginStates = mutableMapOf<String, PluginState>()
    private var priorityList: List<String> = emptyList()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "music_provider_order") {
            reloadPriorities()
        }
    }

    private val internalPlugins: List<IMusicPlugin> = mutableListOf<IMusicPlugin>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            add(SystemSessionPlugin(context))
        }
        add(LastFmPlugin(context))
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        internalPlugins.forEach { registerPlugin(it) }
        discoverAndRegisterExternalPlugins()
        reloadPriorities()
    }

    fun registerPlugin(plugin: IMusicPlugin) {
        if (plugins.containsKey(plugin.id)) return

        plugins[plugin.id] = plugin
        plugin.setCallback { newState ->
            handlePluginUpdate(plugin.id, newState)
        }
        plugin.init()
    }

    private fun reloadPriorities() {
        val rawOrder = sharedPreferences.getString("music_provider_order", "system_media,lastfm")
            ?: "system_media,lastfm"
        priorityList = rawOrder.split(",").map { it.trim() }
        recalculateOutput()
    }

    private fun handlePluginUpdate(pluginId: String, newState: PluginState) {
        pluginStates[pluginId] = newState
        recalculateOutput()
    }

    private fun recalculateOutput() {
        var activeState: PluginState = PluginState.Idle
        var primaryPluginId: String? = null

        for (id in priorityList) {
            val state = pluginStates[id]
            if (state is PluginState.Playing) {
                activeState = state
                primaryPluginId = id
                break
            }
        }

        if (activeState is PluginState.Playing && primaryPluginId != null) {
            val primaryTrack = activeState.track

            val isArtMissing = primaryTrack.artworkBitmap == null && primaryTrack.artworkUrl.isNullOrEmpty()

            if (isArtMissing) {

                for ((id, state) in pluginStates) {
                    if (id == primaryPluginId) continue

                    if (state is PluginState.Playing) {
                        val candidateTrack = state.track

                        if (areTracksSame(primaryTrack, candidateTrack)) {
                            if (candidateTrack.artworkUrl != null || candidateTrack.artworkBitmap != null) {
                                Log.d("MusicManager", "Merging art from $id into $primaryPluginId for '${primaryTrack.title}'")

                                val mergedTrack = primaryTrack.copy(
                                    artworkUrl = candidateTrack.artworkUrl,
                                    artworkBitmap = candidateTrack.artworkBitmap
                                )
                                activeState = PluginState.Playing(mergedTrack)
                                break
                            }
                        }
                    }
                }
            }
        }

        onUpdate(activeState)
    }

    private fun areTracksSame(t1: MusicTrack, t2: MusicTrack): Boolean {
        return t1.title.equals(t2.title, ignoreCase = true) &&
                t1.artist.equals(t2.artist, ignoreCase = true)
    }

    private fun discoverAndRegisterExternalPlugins() {
        val pm = context.packageManager
        val queryIntent = Intent(ExternalPluginContract.ACTION_MUSIC_PLUGIN_SERVICE)
        Log.d("MusicManager", "Querying plugins with action: ${queryIntent.action}")
        try {
            val resolveInfos = pm.queryIntentServices(queryIntent, android.content.pm.PackageManager.GET_META_DATA)
            Log.d("MusicManager", "Found ${resolveInfos.size} services")
            for (resolveInfo in resolveInfos) {
                val serviceInfo = resolveInfo.serviceInfo ?: continue
                val packageName = serviceInfo.packageName
                if (plugins.containsKey(packageName)) continue

                var displayName = serviceInfo.loadLabel(pm).toString()
                var description = "External Music Provider"
                Log.d("MusicManager", "Found plugin: ${resolveInfo.serviceInfo.packageName}")
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
                                if (!nameAttr.isNullOrEmpty()) displayName = nameAttr
                                if (!descAttr.isNullOrEmpty()) description = descAttr
                            }
                            eventType = parser.next()
                        }
                    } catch (e: Exception) {
                        Log.e("MusicManager", "Failed to parse plugin info for $packageName", e)
                    }
                }

                val externalPlugin = ExternalMusicPlugin(
                    context,
                    id = packageName,
                    displayName = displayName,
                    description = description
                )
                registerPlugin(externalPlugin)
            }
        } catch (e: Exception) {
            Log.e("MusicManager", "Error discovering external plugins", e)
        }
    }

    fun destroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        plugins.values.forEach { it.destroy() }
    }
}