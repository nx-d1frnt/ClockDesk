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
        discoverExternalPlugins()
    }

    fun getAllProviders(): List<IBackgroundProvider> {
        val result = mutableListOf<IBackgroundProvider>()
        result.addAll(internalProviders)
        result.addAll(externalPlugins)
        return result
    }

    fun getExternalProviders(): List<ExternalBackgroundPlugin> = externalPlugins.toList()

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

    fun setActiveProviderId(id: String) {
        val previous = getActiveProvider()
        previous.onDeselected(context)

        prefs.edit().putString(KEY_ACTIVE_PROVIDER_ID, id).apply()

        val next = getActiveProvider()
        next.onSelected(context)

        val payload = next.getCurrentPayload(context)
        notifyListeners(next, payload)

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
                                displayName = parser.getAttributeValue(null, "displayName")
                                description = parser.getAttributeValue(null, "description")
                                settingsActivity = parser.getAttributeValue(null, "settingsActivity")
                                supportsBlur = parser.getAttributeBooleanValue(null, "supportsBlur", true)
                                supportsCrop = parser.getAttributeBooleanValue(null, "supportsCrop", true)
                                supportsWeather = parser.getAttributeBooleanValue(null, "supportsWeather", true)
                                supportsDim = parser.getAttributeBooleanValue(null, "supportsDimming", true)
                            }
                        }

                        if (pluginId != null && displayName != null) {
                            discovered.add(
                                ExternalBackgroundPlugin(
                                    packageName = packageName,
                                    receiverClassName = className,
                                    id = "external:$packageName/$pluginId",
                                    displayName = displayName,
                                    description = description ?: "",
                                    settingsActivity = settingsActivity,
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
                externalPlugins.clear()
                externalPlugins.addAll(discovered)
                onComplete?.invoke()
            }
        }.start()
    }

    private fun requestDataFromPlugin(plugin: ExternalBackgroundPlugin) {
        val intent = Intent(BackgroundPluginContract.ACTION_REQUEST_BACKGROUND_DATA).apply {
            setClassName(plugin.packageName, plugin.receiverClassName)
            putExtra(BackgroundPluginContract.KEY_PLUGIN_ID, plugin.id)
        }
        context.sendBroadcast(intent)
    }

    private fun handlePluginUpdate(intent: Intent) {
        val pluginId = intent.getStringExtra(BackgroundPluginContract.KEY_PLUGIN_ID) ?: return
        val plugin = externalPlugins.firstOrNull { it.id == pluginId } ?: return

        val color = if (intent.hasExtra(BackgroundPluginContract.KEY_COLOR)) {
            intent.getIntExtra(BackgroundPluginContract.KEY_COLOR, Color.TRANSPARENT)
        } else null

        val imageUri = intent.getStringExtra(BackgroundPluginContract.KEY_IMAGE_URI)

        val payload = when {
            color != null && color != Color.TRANSPARENT -> BackgroundPayload.SolidColor(color)
            !imageUri.isNullOrEmpty() -> BackgroundPayload.ImageUri(imageUri)
            else -> BackgroundPayload.Gradient
        }

        plugin.updatePayload(payload)

        if (getActiveProviderId() == plugin.id) {
            notifyListeners(plugin, payload)
        }
    }

    fun onDestroy() {
        try {
            context.unregisterReceiver(updateReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }
}
