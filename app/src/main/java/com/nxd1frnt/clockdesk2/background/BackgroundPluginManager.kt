package com.nxd1frnt.clockdesk2.background

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import com.nxd1frnt.clockdesk2.background.plugins.ExternalBackgroundPlugin
import com.nxd1frnt.clockdesk2.utils.Logger
import org.xmlpull.v1.XmlPullParser

class BackgroundPluginManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val onUpdate: (BackgroundState) -> Unit
) {
    private val plugins = mutableMapOf<String, IBackgroundPlugin>()
    private val pluginStates = mutableMapOf<String, BackgroundState>()
    private var priorityList: List<String> = emptyList()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "background_provider_order") {
            reloadPriorities()
        }
    }

    private val internalPlugins: List<IBackgroundPlugin> = listOf(
        com.nxd1frnt.clockdesk2.background.plugins.BingDailyWallpaperPlugin(context)
    )

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        internalPlugins.forEach { registerPlugin(it) }
        discoverAndRegisterExternalPlugins()
        reloadPriorities()
    }

    fun registerPlugin(plugin: IBackgroundPlugin) {
        if (plugins.containsKey(plugin.id)) return

        plugins[plugin.id] = plugin
        plugin.setCallback { newState ->
            handlePluginUpdate(plugin.id, newState)
        }
        plugin.init()
    }

    private fun reloadPriorities() {
        val rawOrder = sharedPreferences.getString("background_provider_order", "") ?: ""
        priorityList = rawOrder.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        recalculateOutput()
    }

    private fun handlePluginUpdate(pluginId: String, newState: BackgroundState) {
        pluginStates[pluginId] = newState
        recalculateOutput()
    }

    private fun recalculateOutput() {
        var activeState: BackgroundState = BackgroundState.Unavailable

        for (id in priorityList) {
            val state = pluginStates[id]
            if (state is BackgroundState.Available) {
                activeState = state
                break
            }
        }

        onUpdate(activeState)
    }

    private fun discoverAndRegisterExternalPlugins() {
        val pm = context.packageManager
        val queryIntent = Intent(BackgroundPluginContract.ACTION_BACKGROUND_PLUGIN_SERVICE)
        Logger.d("BackgroundManager") { "Querying background plugins with action: ${queryIntent.action}" }
        try {
            val resolveInfos = pm.queryIntentServices(queryIntent, PackageManager.GET_META_DATA)
            Logger.d("BackgroundManager") { "Found ${resolveInfos.size} background plugin services" }
            for (resolveInfo in resolveInfos) {
                val serviceInfo = resolveInfo.serviceInfo ?: continue
                val packageName = serviceInfo.packageName
                if (plugins.containsKey(packageName)) continue

                var displayName = serviceInfo.loadLabel(pm).toString()
                var description = "External Background Provider"
                Logger.d("BackgroundManager") { "Found background plugin: $packageName" }
                val metaData = serviceInfo.metaData
                if (metaData != null && metaData.containsKey(BackgroundPluginContract.META_DATA_PLUGIN_INFO)) {
                    val resId = metaData.getInt(BackgroundPluginContract.META_DATA_PLUGIN_INFO)
                    try {
                        val pluginRes = pm.getResourcesForApplication(packageName)
                        val parser = pluginRes.getXml(resId)
                        var eventType = parser.eventType
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG && parser.name == "background-plugin") {
                                val nameAttr = parser.getAttributeValue(null, "displayName")
                                val descAttr = parser.getAttributeValue(null, "description")
                                if (!nameAttr.isNullOrEmpty()) displayName = nameAttr
                                if (!descAttr.isNullOrEmpty()) description = descAttr
                            }
                            eventType = parser.next()
                        }
                    } catch (e: Exception) {
                        Logger.e("BackgroundManager") { "Failed to parse plugin info for $packageName: ${e.message}" }
                    }
                }

                val externalPlugin = ExternalBackgroundPlugin(
                    context,
                    id = packageName,
                    displayName = displayName,
                    description = description
                )
                registerPlugin(externalPlugin)
            }
        } catch (e: Exception) {
            Logger.e("BackgroundManager") { "Error discovering external background plugins: ${e.message}" }
        }
    }

    fun destroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        plugins.values.forEach { it.destroy() }
    }

    fun requestBackgroundFromAll() {
        // This could be used to manually trigger updates from all plugins
        // For now, plugins manage their own update schedules
    }
}
