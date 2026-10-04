package com.nxd1frnt.clockdesk2.music

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.nxd1frnt.clockdesk2.music.plugins.ExternalMusicPlugin
import com.nxd1frnt.clockdesk2.music.plugins.LastFmPlugin
import com.nxd1frnt.clockdesk2.music.plugins.SystemSessionPlugin
import com.nxd1frnt.clockdesk2.utils.Logger

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
        add(com.nxd1frnt.clockdesk2.music.plugins.KdeConnectMusicPlugin(context))
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
        val rawOrder = sharedPreferences.getString("music_provider_order", "system_media,deskconnect_mpris,lastfm")
            ?: "system_media,deskconnect_mpris,lastfm"
        priorityList = rawOrder.split(",").map { it.trim() }
        recalculateOutput()
    }

    private var primaryPluginId: String? = null
    private var lastActivePluginId: String? = null

    private fun handlePluginUpdate(pluginId: String, newState: PluginState) {
        pluginStates[pluginId] = newState
        recalculateOutput()
    }

    private fun recalculateOutput() {
        var activeState: PluginState = PluginState.Idle
        var selectedId: String? = null

        // 1. Look for actively playing
        for (id in priorityList) {
            val state = pluginStates[id]
            if (state is PluginState.Playing) {
                activeState = state
                selectedId = id
                break
            }
        }

        // 2. If nothing is actively playing, look for paused
        if (selectedId == null) {
            for (id in priorityList) {
                val state = pluginStates[id]
                if (state is PluginState.Paused) {
                    activeState = state
                    selectedId = id
                    break
                }
            }
        }

        primaryPluginId = selectedId
        if (selectedId != null) {
            lastActivePluginId = selectedId
        }

        if ((activeState is PluginState.Playing || activeState is PluginState.Paused) && selectedId != null) {
            val primaryTrack = when (activeState) {
                is PluginState.Playing -> activeState.track
                is PluginState.Paused -> activeState.track
                else -> null
            }

            if (primaryTrack != null) {
                val isArtMissing = (primaryTrack.artworkBitmap == null || primaryTrack.artworkBitmap.isRecycled) && primaryTrack.artworkUrl.isNullOrEmpty()

                if (isArtMissing) {
                    for ((id, state) in pluginStates) {
                        if (id == selectedId) continue

                        val candidateTrack = when (state) {
                            is PluginState.Playing -> state.track
                            is PluginState.Paused -> state.track
                            else -> null
                        }

                        if (candidateTrack != null && areTracksSame(primaryTrack, candidateTrack)) {
                            val candidateBitmapValid = candidateTrack.artworkBitmap != null && !candidateTrack.artworkBitmap.isRecycled
                            if (candidateTrack.artworkUrl != null || candidateBitmapValid) {
                                Logger.d("MusicManager"){"Merging art from $id into $selectedId for '${primaryTrack.title}'"}

                                val mergedTrack = primaryTrack.copy(
                                    artworkUrl = candidateTrack.artworkUrl,
                                    artworkBitmap = if (candidateBitmapValid) candidateTrack.artworkBitmap else null
                                )
                                activeState = if (activeState is PluginState.Playing) {
                                    PluginState.Playing(mergedTrack)
                                } else {
                                    PluginState.Paused(mergedTrack)
                                }
                                break
                            }
                        }
                    }
                }
            }
        }

        onUpdate(activeState)
    }

    fun play() {
        val id = primaryPluginId ?: lastActivePluginId ?: return
        plugins[id]?.play()
    }

    fun pause() {
        val id = primaryPluginId ?: lastActivePluginId ?: return
        plugins[id]?.pause()
    }

    fun togglePlayPause() {
        val id = primaryPluginId ?: lastActivePluginId ?: return
        plugins[id]?.togglePlayPause()
    }

    fun next() {
        val id = primaryPluginId ?: lastActivePluginId ?: return
        plugins[id]?.next()
    }

    fun previous() {
        val id = primaryPluginId ?: lastActivePluginId ?: return
        plugins[id]?.previous()
    }

    private fun areTracksSame(t1: MusicTrack, t2: MusicTrack): Boolean {
        return t1.title.equals(t2.title, ignoreCase = true) &&
                t1.artist.equals(t2.artist, ignoreCase = true)
    }

    private fun discoverAndRegisterExternalPlugins() {
        Thread {
            val pm = context.packageManager
            val queryIntent = Intent(ExternalPluginContract.ACTION_MUSIC_PLUGIN_SERVICE)
            Logger.d("MusicManager"){"Querying plugins with action: ${queryIntent.action}"}
            try {
                val resolveInfos = pm.queryIntentServices(queryIntent, android.content.pm.PackageManager.GET_META_DATA)

                class PluginData(val pkg: String, val name: String, val desc: String)
                val pendingPlugins = mutableListOf<PluginData>()

                for (resolveInfo in resolveInfos) {
                    val serviceInfo = resolveInfo.serviceInfo ?: continue
                    val packageName = serviceInfo.packageName
                    if (plugins.containsKey(packageName)) continue

                    var displayName = serviceInfo.loadLabel(pm).toString()
                    var description = "External Music Provider"

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
                            Logger.e("MusicManager"){"Failed to parse plugin info for $packageName: ${e.message}"}
                        }
                    }
                    pendingPlugins.add(PluginData(packageName, displayName, description))
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    pendingPlugins.forEach { data ->
                        if (!plugins.containsKey(data.pkg)) {
                            val externalPlugin = ExternalMusicPlugin(
                                context,
                                id = data.pkg,
                                displayName = data.name,
                                description = data.desc
                            )
                            registerPlugin(externalPlugin)
                        }
                    }
                    reloadPriorities()
                }
            } catch (e: Exception) {
                Logger.e("MusicManager"){"Error discovering external plugins: ${e.message}"}
            }
        }.start()
    }

    fun destroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        plugins.values.forEach { it.destroy() }
    }
}