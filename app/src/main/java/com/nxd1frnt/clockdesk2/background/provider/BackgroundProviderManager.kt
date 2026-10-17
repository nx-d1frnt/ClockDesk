package com.nxd1frnt.clockdesk2.background.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.nxd1frnt.clockdesk2.background.BackgroundManager
import com.nxd1frnt.clockdesk2.utils.Logger
import org.xmlpull.v1.XmlPullParser

class BackgroundProviderManager private constructor(private val context: Context) {

    companion object {
        const val KEY_ACTIVE_PROVIDER_ID = "active_background_provider_id"
        private const val KEY_CACHED_PLUGINS = "cached_external_background_plugins"
        private const val PREFS_NAME = "ClockDeskPrefs"

        @Volatile
        private var instance: BackgroundProviderManager? = null

        fun getInstance(context: Context): BackgroundProviderManager {
            return instance ?: synchronized(this) {
                instance ?: BackgroundProviderManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    val solidColorProvider = SolidColorBackgroundProvider()
    val gradientProvider = DynamicGradientBackgroundProvider()
    val imageProvider = ImageBackgroundProvider()

    private val internalProviders = listOf<IBackgroundProvider>(
        gradientProvider,
        solidColorProvider,
        imageProvider
    )

    private val externalPlugins = mutableListOf<ExternalBackgroundPlugin>()
    private val listeners = mutableListOf<(IBackgroundProvider, BackgroundPayload) -> Unit>()

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == BackgroundPluginContract.ACTION_UPDATE_BACKGROUND_DATA) {
                handlePluginUpdate(intent)
            }
        }
    }

    init {
        val filter = IntentFilter(BackgroundPluginContract.ACTION_UPDATE_BACKGROUND_DATA)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(updateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(updateReceiver, filter)
        }
        loadCachedPlugins()
        discoverExternalPlugins()
    }

    fun getAllProviders(): List<IBackgroundProvider> {
        val result = mutableListOf<IBackgroundProvider>()
        result.addAll(internalProviders)
        synchronized(externalPlugins) {
            result.addAll(externalPlugins)
        }
        return result
    }

    fun getExternalProviders(): List<ExternalBackgroundPlugin> = synchronized(externalPlugins) {
        externalPlugins.toList()
    }

    fun savePayloadForId(id: String, payload: BackgroundPayload) {
        val editor = prefs.edit()
        when (payload) {
            is BackgroundPayload.SolidColor -> {
                editor.putString("bg_plugin_payload_type_$id", "color")
                editor.putInt("bg_plugin_payload_color_$id", payload.color)
            }
            is BackgroundPayload.ImageUri -> {
                editor.putString("bg_plugin_payload_type_$id", "image_uri")
                editor.putString("bg_plugin_payload_uri_$id", payload.uriString)
            }
            is BackgroundPayload.Gradient -> {
                editor.putString("bg_plugin_payload_type_$id", "gradient")
            }
            is BackgroundPayload.ImageBitmap -> {
                // Bitmaps are not persisted via SharedPreferences
            }
        }
        editor.apply()
    }

    fun getSavedPayloadForId(id: String): BackgroundPayload {
        val type = prefs.getString("bg_plugin_payload_type_$id", null)
        return when (type) {
            "color" -> {
                val color = prefs.getInt("bg_plugin_payload_color_$id", Color.BLACK)
                BackgroundPayload.SolidColor(color)
            }
            "image_uri" -> {
                val uriStr = prefs.getString("bg_plugin_payload_uri_$id", null)
                if (uriStr != null) BackgroundPayload.ImageUri(uriStr) else BackgroundPayload.Gradient
            }
            else -> BackgroundPayload.Gradient
        }
    }

    private fun loadCachedPlugins() {
        val jsonStr = prefs.getString(KEY_CACHED_PLUGINS, null) ?: return
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            val cached = mutableListOf<ExternalBackgroundPlugin>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val pkg = obj.getString("packageName")
                val rawName = obj.getString("displayName")
                val rawDesc = obj.optString("description", "")
                cached.add(
                    ExternalBackgroundPlugin(
                        packageName = pkg,
                        receiverClassName = obj.getString("receiverClassName"),
                        id = obj.getString("id"),
                        displayName = resolvePotentiallyRawString(pkg, rawName),
                        description = resolvePotentiallyRawString(pkg, rawDesc),
                        settingsActivity = if (obj.isNull("settingsActivity")) null else obj.getString("settingsActivity"),
                        capabilities = ProviderCapabilities(
                            supportsBlur = obj.optBoolean("supportsBlur", true),
                            supportsCrop = obj.optBoolean("supportsCrop", obj.optBoolean("supportsCropping", true)),
                            supportsDimming = obj.optBoolean("supportsDimming", obj.optBoolean("supportsDim", true)),
                            supportsWeather = obj.optBoolean("supportsWeather", true)
                        )
                    )
                )
            }
            synchronized(externalPlugins) {
                externalPlugins.clear()
                externalPlugins.addAll(cached)
            }
            Logger.i("BackgroundProvider") { "Loaded ${cached.size} external background plugins from cache" }
        } catch (e: Exception) {
            Logger.w("BackgroundProvider") { "Failed to load cached background plugins: ${e.message}" }
        }
    }

    private fun savePluginsToCache(plugins: List<ExternalBackgroundPlugin>) {
        try {
            val jsonArray = org.json.JSONArray()
            for (p in plugins) {
                val obj = org.json.JSONObject().apply {
                    put("packageName", p.packageName)
                    put("receiverClassName", p.receiverClassName)
                    put("id", p.id)
                    put("displayName", p.displayName)
                    put("description", p.description)
                    if (p.settingsActivity != null) put("settingsActivity", p.settingsActivity)
                    put("supportsBlur", p.capabilities.supportsBlur)
                    put("supportsCrop", p.capabilities.supportsCrop)
                    put("supportsCropping", p.capabilities.supportsCrop)
                    put("supportsDimming", p.capabilities.supportsDimming)
                    put("supportsWeather", p.capabilities.supportsWeather)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_CACHED_PLUGINS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Logger.w("BackgroundProvider") { "Failed to save plugins to cache: ${e.message}" }
        }
    }

    fun getActiveProviderId(): String {
        val saved = prefs.getString(KEY_ACTIVE_PROVIDER_ID, null)
        if (saved != null) return saved

        // Graceful migration from legacy saved background URI
        val legacyUri = BackgroundManager(context).getSavedBackgroundUri()
        val migratedId = when {
            legacyUri == null || legacyUri == "__DEFAULT_GRADIENT__" -> DynamicGradientBackgroundProvider.ID
            else -> ImageBackgroundProvider.ID
        }
        prefs.edit().putString(KEY_ACTIVE_PROVIDER_ID, migratedId).apply()
        return migratedId
    }

    fun getActiveProvider(): IBackgroundProvider {
        val id = getActiveProviderId()
        return getAllProviders().firstOrNull { it.id == id } ?: gradientProvider
    }

    fun setActiveProviderId(id: String, notify: Boolean = true) {
        val previous = getActiveProvider()
        previous.onDeselected(context)

        prefs.edit().putString(KEY_ACTIVE_PROVIDER_ID, id).apply()

        val next = getActiveProvider()
        next.onSelected(context)

        if (notify) {
            val payload = next.getCurrentPayload(context)
            notifyListeners(next, payload)
        }

        if (next is ExternalBackgroundPlugin) {
            requestDataFromPlugin(next)
        }
    }

    fun addListener(listener: (IBackgroundProvider, BackgroundPayload) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (IBackgroundProvider, BackgroundPayload) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners(provider: IBackgroundProvider, payload: BackgroundPayload) {
        mainHandler.post {
            listeners.forEach { it(provider, payload) }
        }
    }

    fun discoverExternalPlugins(onComplete: (() -> Unit)? = null) {
        Thread {
            val pm = context.packageManager
            val queryIntent = Intent(BackgroundPluginContract.ACTION_QUERY_BACKGROUND_PLUGINS)
            val receivers = pm.queryBroadcastReceivers(queryIntent, PackageManager.GET_META_DATA)

            val discovered = mutableListOf<ExternalBackgroundPlugin>()

            for (resolveInfo in receivers) {
                val activityInfo = resolveInfo.activityInfo ?: continue
                val metaData = activityInfo.metaData ?: continue
                val packageName = activityInfo.packageName
                val className = activityInfo.name

                if (metaData.containsKey(BackgroundPluginContract.META_DATA_PLUGIN_INFO)) {
                    val resId = metaData.getInt(BackgroundPluginContract.META_DATA_PLUGIN_INFO)
                    try {
                        val pluginRes = pm.getResourcesForApplication(packageName)
                        val parser = pluginRes.getXml(resId)

                        var pluginId: String? = null
                        var displayName: String? = null
                        var description: String? = null
                        var settingsActivity: String? = null
                        var supportsBlur = true
                        var supportsCrop = true
                        var supportsWeather = true
                        var supportsDim = true

                        while (parser.next() != XmlPullParser.END_DOCUMENT) {
                            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "background-plugin") {
                                pluginId = parser.getAttributeValue(null, "id")
                                displayName = resolveXmlString(parser, pluginRes, "displayName")
                                description = resolveXmlString(parser, pluginRes, "description")
                                settingsActivity = parser.getAttributeValue(null, "settingsActivity")
                                supportsBlur = parser.getAttributeBooleanValue(null, "supportsBlur", true)
                                val hasCropAttr = parser.getAttributeValue(null, "supportsCrop") != null
                                val hasCroppingAttr = parser.getAttributeValue(null, "supportsCropping") != null
                                supportsCrop = when {
                                    hasCropAttr -> parser.getAttributeBooleanValue(null, "supportsCrop", true)
                                    hasCroppingAttr -> parser.getAttributeBooleanValue(null, "supportsCropping", true)
                                    else -> true
                                }
                                supportsWeather = parser.getAttributeBooleanValue(null, "supportsWeather", true)
                                val hasDimmingAttr = parser.getAttributeValue(null, "supportsDimming") != null
                                val hasDimAttr = parser.getAttributeValue(null, "supportsDim") != null
                                supportsDim = when {
                                    hasDimmingAttr -> parser.getAttributeBooleanValue(null, "supportsDimming", true)
                                    hasDimAttr -> parser.getAttributeBooleanValue(null, "supportsDim", true)
                                    else -> true
                                }
                            }
                        }

                        if (pluginId != null && displayName != null) {
                            val resolvedSettingsActivity = when {
                                settingsActivity.isNullOrEmpty() -> null
                                settingsActivity.startsWith(".") -> "$packageName$settingsActivity"
                                !settingsActivity.contains(".") -> "$packageName.$settingsActivity"
                                else -> settingsActivity
                            }
                            discovered.add(
                                ExternalBackgroundPlugin(
                                    packageName = packageName,
                                    receiverClassName = className,
                                    id = "external:$packageName/$pluginId",
                                    displayName = displayName,
                                    description = description ?: "",
                                    settingsActivity = resolvedSettingsActivity,
                                    capabilities = ProviderCapabilities(
                                        supportsBlur = supportsBlur,
                                        supportsCrop = supportsCrop,
                                        supportsDimming = supportsDim,
                                        supportsWeather = supportsWeather
                                    )
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Logger.w("BackgroundProviderManager") { "Failed to parse background plugin metadata: $packageName - ${e.message}" }
                    }
                }
            }

            mainHandler.post {
                synchronized(externalPlugins) {
                    externalPlugins.clear()
                    externalPlugins.addAll(discovered)
                }
                savePluginsToCache(discovered)
                onComplete?.invoke()

                val activeId = getActiveProviderId()
                if (activeId.startsWith("external:")) {
                    val activePlugin = externalPlugins.firstOrNull { it.id == activeId }
                    if (activePlugin != null) {
                        val payload = activePlugin.getCurrentPayload(context)
                        if (payload !is BackgroundPayload.Gradient) {
                            notifyListeners(activePlugin, payload)
                        }
                        requestDataFromPlugin(activePlugin)
                    } else if (discovered.isNotEmpty()) {
                        val pkg = activeId.removePrefix("external:").substringBefore("/")
                        val isInstalled = try {
                            pm.getPackageInfo(pkg, 0)
                            true
                        } catch (e: Exception) {
                            false
                        }
                        if (!isInstalled) {
                            Logger.w("BackgroundProvider") { "Active external plugin $activeId is no longer installed, reverting to gradient" }
                            setActiveProviderId(DynamicGradientBackgroundProvider.ID)
                        }
                    }
                }
            }
        }.start()
    }

    fun requestDataFromPlugin(plugin: ExternalBackgroundPlugin) {
        Logger.i("BackgroundProvider") { "requestDataFromPlugin: sending broadcast to ${plugin.packageName}/${plugin.receiverClassName} for id=${plugin.id}" }
        val intent = Intent(BackgroundPluginContract.ACTION_REQUEST_BACKGROUND_DATA).apply {
            setClassName(plugin.packageName, plugin.receiverClassName)
            putExtra(BackgroundPluginContract.KEY_PLUGIN_ID, plugin.id)
        }
        context.sendBroadcast(intent)
    }

    fun requestDataFromPluginId(pluginId: String) {
        val plugin = synchronized(externalPlugins) {
            externalPlugins.firstOrNull { it.id == pluginId || it.id.endsWith("/$pluginId") || it.id == "external:$pluginId" }
        }
        if (plugin != null) {
            requestDataFromPlugin(plugin)
            return
        }
        if (pluginId.startsWith("external:")) {
            val stripped = pluginId.removePrefix("external:")
            val parts = stripped.split("/", limit = 2)
            if (parts.isNotEmpty()) {
                val pkg = parts[0]
                val id = if (parts.size > 1) parts[1] else pluginId
                val intent = Intent(BackgroundPluginContract.ACTION_REQUEST_BACKGROUND_DATA).apply {
                    setPackage(pkg)
                    putExtra(BackgroundPluginContract.KEY_PLUGIN_ID, id)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    private fun handlePluginUpdate(intent: Intent) {
        val pluginId = intent.getStringExtra(BackgroundPluginContract.KEY_PLUGIN_ID) ?: return
        val packageName = intent.getStringExtra(BackgroundPluginContract.KEY_PLUGIN_PACKAGE)
        val plugin = synchronized(externalPlugins) {
            externalPlugins.firstOrNull {
                it.id == pluginId ||
                it.id.endsWith("/$pluginId") ||
                it.id == "external:$pluginId" ||
                (packageName != null && it.id == "external:$packageName/$pluginId")
            }
        }

        val color = if (intent.hasExtra(BackgroundPluginContract.KEY_COLOR)) {
            intent.getIntExtra(BackgroundPluginContract.KEY_COLOR, Color.TRANSPARENT)
        } else null

        val imageUri = intent.getStringExtra(BackgroundPluginContract.KEY_IMAGE_URI)

        val payload = when {
            color != null && color != Color.TRANSPARENT -> BackgroundPayload.SolidColor(color)
            !imageUri.isNullOrEmpty() -> BackgroundPayload.ImageUri(imageUri)
            else -> BackgroundPayload.Gradient
        }

        val effectiveId = plugin?.id ?: if (packageName != null) "external:$packageName/$pluginId" else "external:$pluginId"
        Logger.i("BackgroundProvider") { "handlePluginUpdate: plugin=$effectiveId, payload=$payload" }
        savePayloadForId(effectiveId, payload)
        plugin?.updatePayload(context, payload)

        val activeId = getActiveProviderId()
        if (activeId == effectiveId || (plugin != null && activeId == plugin.id)) {
            val target = plugin ?: ExternalBackgroundPlugin(
                packageName = packageName ?: "",
                receiverClassName = "",
                id = effectiveId,
                displayName = "",
                description = ""
            )
            notifyListeners(target, payload)
        }
    }

    private fun resolvePotentiallyRawString(packageName: String, raw: String): String {
        if (raw.startsWith("@")) {
            val resId = raw.substring(1).toIntOrNull()
            if (resId != null && resId != 0) {
                try {
                    return context.packageManager.getResourcesForApplication(packageName).getString(resId)
                } catch (_: Exception) {}
            }
        }
        return raw
    }

    private fun resolveXmlString(
        parser: android.content.res.XmlResourceParser,
        pluginRes: android.content.res.Resources,
        attrName: String
    ): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == attrName) {
                val resId = parser.getAttributeResourceValue(i, 0)
                if (resId != 0) {
                    try {
                        return pluginRes.getString(resId)
                    } catch (_: Exception) {}
                }
                val rawValue = parser.getAttributeValue(i) ?: return null
                if (rawValue.startsWith("@")) {
                    val parsedId = rawValue.substring(1).toIntOrNull()
                    if (parsedId != null && parsedId != 0) {
                        try {
                            return pluginRes.getString(parsedId)
                        } catch (_: Exception) {}
                    }
                }
                return rawValue
            }
        }
        return null
    }

    fun onDestroy() {
        try {
            context.unregisterReceiver(updateReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }
}
