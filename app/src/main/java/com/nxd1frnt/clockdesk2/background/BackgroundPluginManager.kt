package com.nxd1frnt.clockdesk2.background

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.nxd1frnt.clockdesk2.background.plugins.BingDailyWallpaperPlugin
import com.nxd1frnt.clockdesk2.utils.Logger

/**
 * BackgroundPluginManager manages background provider plugins.
 * Similar to MusicPluginManager, it supports both internal and external plugins.
 */
class BackgroundPluginManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val onUpdate: (BackgroundPluginState) -> Unit
) {
    private val plugins = mutableMapOf<String, IBackgroundPlugin>()
    private val pluginStates = mutableMapOf<String, BackgroundPluginState>()
    private var activePluginId: String? = null
    
    companion object {
        const val ACTION_BACKGROUND_PLUGIN_SERVICE = "com.nxd1frnt.clockdesk2.background.PLUGIN"
        const val META_DATA_PLUGIN_INFO = "com.nxd1frnt.clockdesk2.background.PLUGIN_INFO"
        const val ACTION_UPDATE_STATE = "com.nxd1frnt.clockdesk2.background.UPDATE_STATE"
        
        const val KEY_IS_READY = "is_ready"
        const val KEY_BACKGROUND_ID = "background_id"
        const val KEY_BACKGROUND_URI = "background_uri"
        const val KEY_PACKAGE_NAME = "source_package"
    }
    
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "background_provider") {
            val newProvider = prefs.getString("background_provider", null)
            setActiveProvider(newProvider)
        }
    }
    
    private val internalPlugins: List<IBackgroundPlugin> = listOf(
        BingDailyWallpaperPlugin(context)
    )
    
    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        
        // Register internal plugins
        internalPlugins.forEach { registerPlugin(it) }
        
        // Discover and register external plugins
        discoverAndRegisterExternalPlugins()
        
        // Load active provider from preferences
        loadActiveProvider()
    }
    
    fun registerPlugin(plugin: IBackgroundPlugin) {
        if (plugins.containsKey(plugin.id)) {
            Logger.d("BackgroundPluginManager") { "Plugin ${plugin.id} already registered, skipping" }
            return
        }
        
        plugins[plugin.id] = plugin
        plugin.setCallback { newState ->
            handlePluginUpdate(plugin.id, newState)
        }
        plugin.init()
        Logger.d("BackgroundPluginManager") { "Registered plugin: ${plugin.id}" }
    }
    
    private fun loadActiveProvider() {
        val savedProvider = sharedPreferences.getString("background_provider", null)
        if (savedProvider != null && plugins.containsKey(savedProvider)) {
            setActiveProvider(savedProvider)
        }
    }
    
    fun setActiveProvider(pluginId: String?) {
        activePluginId = pluginId
        
        if (pluginId != null) {
            // Request update from the active plugin
            plugins[pluginId]?.requestUpdate()
        } else {
            // No active plugin, reset to idle
            onUpdate(BackgroundPluginState.Idle)
        }
    }
    
    private fun handlePluginUpdate(pluginId: String, newState: BackgroundPluginState) {
        pluginStates[pluginId] = newState
        
        // Only propagate updates from the active plugin
        if (pluginId == activePluginId) {
            onUpdate(newState)
        }
    }
    
    fun getActivePluginState(): BackgroundPluginState? {
        return activePluginId?.let { pluginStates[it] }
    }
    
    fun getAllPlugins(): List<IBackgroundPlugin> {
        return plugins.values.toList()
    }
    
    fun getPluginById(id: String): IBackgroundPlugin? {
        return plugins[id]
    }
    
    private fun discoverAndRegisterExternalPlugins() {
        val pm = context.packageManager
        val queryIntent = Intent(ACTION_BACKGROUND_PLUGIN_SERVICE)
        Logger.d("BackgroundPluginManager") { "Querying external background plugins with action: ${queryIntent.action}" }
        
        try {
            val resolveInfos = pm.queryIntentServices(queryIntent, android.content.pm.PackageManager.GET_META_DATA)
            Logger.d("BackgroundPluginManager") { "Found ${resolveInfos.size} external background plugin services" }
            
            for (resolveInfo in resolveInfos) {
                val serviceInfo = resolveInfo.serviceInfo ?: continue
                val packageName = serviceInfo.packageName
                
                if (plugins.containsKey(packageName)) {
                    Logger.d("BackgroundPluginManager") { "Plugin $packageName already registered, skipping" }
                    continue
                }
                
                var displayName = serviceInfo.loadLabel(pm).toString()
                var description = "External Background Provider"
                
                // Try to read metadata for plugin info
                val metaData = serviceInfo.metaData
                if (metaData != null && metaData.containsKey(META_DATA_PLUGIN_INFO)) {
                    val resId = metaData.getInt(META_DATA_PLUGIN_INFO)
                    try {
                        val pluginRes = pm.getResourcesForApplication(packageName)
                        val parser = pluginRes.getXml(resId)
                        var eventType = parser.eventType
                        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "background-plugin") {
                                val nameAttr = parser.getAttributeValue(null, "displayName")
                                val descAttr = parser.getAttributeValue(null, "description")
                                if (!nameAttr.isNullOrEmpty()) displayName = nameAttr
                                if (!descAttr.isNullOrEmpty()) description = descAttr
                            }
                            eventType = parser.next()
                        }
                    } catch (e: Exception) {
                        Logger.e("BackgroundPluginManager") { "Failed to parse plugin info for $packageName: ${e.message}" }
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
            Logger.e("BackgroundPluginManager") { "Error discovering external plugins: ${e.message}" }
        }
    }
    
    fun destroy() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        plugins.values.forEach { it.destroy() }
        plugins.clear()
        pluginStates.clear()
    }
}
