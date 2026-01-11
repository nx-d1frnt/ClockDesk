package com.nxd1frnt.clockdesk2.smartchips

import android.content.*
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.plugins.BatteryAlertPlugin
import org.xmlpull.v1.XmlPullParser
import kotlin.text.compareTo

class SmartChipManager(
    private val context: Context,
    private val chipContainer: ViewGroup,
    private val sharedPreferences: SharedPreferences
) {
    private data class ChipInfo(
        val id: String,
        val view: View,
        val priority: Int,
        var isVisible: Boolean = false,
        var currentText: String? = null,
        var clickActivityClassName: String? = null
    )

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 5000L
    private val periodicUpdateRunnable = object : Runnable {
        override fun run() {
            updateAllChips()
            handler.postDelayed(this, updateInterval)
        }
    }

    private val internalPlugins: List<ISmartChip> = listOf(
        BatteryAlertPlugin(context)
    )
    var externalPlugins: List<ExternalChipPlugin> = emptyList()
    private val allChips = mutableListOf<ChipInfo>()

    private val dataUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != ChipPluginContract.ACTION_UPDATE_DATA) return
            val packageName = intent.getStringExtra(ChipPluginContract.KEY_PLUGIN_PACKAGE) ?: return
            val chipInfo = allChips.find { it.id == packageName } ?: return

            val isVisible = intent.getBooleanExtra(ChipPluginContract.KEY_CHIP_VISIBLE, true)
            val text = intent.getStringExtra(ChipPluginContract.KEY_CHIP_TEXT)
            val iconName = intent.getStringExtra(ChipPluginContract.KEY_CHIP_ICON_NAME)
            val clickActivity = intent.getStringExtra(ChipPluginContract.KEY_CHIP_CLICK_ACTIVITY)

            if (!isVisible) {
                chipInfo.isVisible = false
                chipInfo.clickActivityClassName = null
            } else if (text != null && iconName != null) {
                // Only update if text has changed
                if (text != chipInfo.currentText) {
                    val success = updateExternalChipView(chipInfo.view, packageName, text, iconName)
                    chipInfo.isVisible = success
                    if (success)
                    {
                        chipInfo.currentText = text
                        chipInfo.clickActivityClassName = clickActivity?.takeIf { it.isNotBlank() }
                    }
                } else {

                }
            } else {
                chipInfo.isVisible = false
                chipInfo.clickActivityClassName = null
            }
            sortAndRedrawChips()
        }
    }

    init {
        internalPlugins.forEach { plugin ->
            val view = plugin.createView(context).apply { visibility = View.GONE }
            allChips.add(ChipInfo(plugin.preferenceKey, view, plugin.priority))
        }
        discoverExternalPlugins()

        val filter = IntentFilter(ChipPluginContract.ACTION_UPDATE_DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_EXPORTED
            } else {
                0
            }
            context.registerReceiver(dataUpdateReceiver, filter, flags)
        } else {
            ContextCompat.registerReceiver(context, dataUpdateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
    }

    fun startUpdates() {
        handler.post(periodicUpdateRunnable)
    }

    fun stopUpdates() {
        handler.removeCallbacks(periodicUpdateRunnable)
    }

    fun destroy() {
        stopUpdates()
        context.unregisterReceiver(dataUpdateReceiver)
    }

    private fun discoverExternalPlugins() {
        val pm = context.packageManager
        val queryIntent = Intent(ChipPluginContract.ACTION_QUERY_PLUGINS)
        val receivers = pm.queryBroadcastReceivers(queryIntent, PackageManager.GET_META_DATA)
        val foundPlugins = mutableListOf<ExternalChipPlugin>()

        for (resolveInfo in receivers) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val metaData = activityInfo.metaData ?: continue
            val packageName = activityInfo.packageName
            val className = activityInfo.name

            if (metaData.containsKey(ChipPluginContract.META_DATA_PLUGIN_INFO)) {
                val resId = metaData.getInt(ChipPluginContract.META_DATA_PLUGIN_INFO)
                try {
                    val pluginRes = pm.getResourcesForApplication(packageName)
                    val parser = pluginRes.getXml(resId)
                    var prefKey: String? = null
                    var dispName: String? = null
                    var priority = 0

                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "smart-chip-plugin") {
                            prefKey = parser.getAttributeValue(null, "preferenceKey")
                            dispName = parser.getAttributeValue(null, "displayName")
                            priority = parser.getAttributeValue(null, "priority")?.toIntOrNull() ?: 0
                        }
                    }

                    if (prefKey != null && dispName != null) {
                        foundPlugins.add(ExternalChipPlugin(packageName, className, prefKey, dispName, priority))
                        val view = LayoutInflater.from(context)
                            .inflate(R.layout.smart_chip_layout, chipContainer, false)
                            .apply { visibility = View.GONE
                                isClickable = true
                                isFocusable = true
                            }
                        view.setOnClickListener {
                            val chipInfo = allChips.find { it.view == view } ?: return@setOnClickListener
                            chipInfo.clickActivityClassName?.let { className ->
                                try {
                                    // Resolve full class name
                                    val fullClassName = if (className.startsWith(".")) {
                                        chipInfo.id + className // packageName + .Activity
                                    } else {
                                        className // assume fully qualified
                                    }
                                    val intent = Intent().setClassName(chipInfo.id, fullClassName)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                }
                            }
                        }
                        allChips.add(ChipInfo(packageName, view, priority))
                    }
                } catch (e: Exception) {
                    Log.w("SmartChipManager", "Failed to parse plugin metadata from $packageName", e)
                }
            }
        }
        this.externalPlugins = foundPlugins
    }

    private fun updateExternalChipView(view: View, pkg: String, text: String, iconName: String): Boolean {
        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)
        try {
            val pluginRes = context.packageManager.getResourcesForApplication(pkg)
            val iconId = pluginRes.getIdentifier(iconName, "drawable", pkg)
            if (iconId != 0) {
                iconView.setImageDrawable(ResourcesCompat.getDrawable(pluginRes, iconId, null))
                textView.text = text
                textView.isSelected = true // Required for marquee
                Log.d("SmartChipManager", "Updated external chip from $pkg: $text")
                return true
            } else {
                Log.w("SmartChipManager", "Icon not found: $iconName in $pkg")
            }
        } catch (e: Exception) {
            Log.e("SmartChipManager", "Failed to update external chip view for $pkg", e)
        }
        return false
    }

    fun updateAllChips() {
        // Internal chips
        internalPlugins.forEach { plugin ->
            val chipInfo = allChips.find { it.id == plugin.preferenceKey } ?: return@forEach
            val isEnabled = sharedPreferences.getBoolean(plugin.preferenceKey, false)
            if (!isEnabled) {
                chipInfo.isVisible = false
            } else {
                val newIsVisible = plugin.update(chipInfo.view, sharedPreferences)
                // For internal plugins, we assume update() handles text; we don't track text here
                // If you want marquee preservation for internal too, add currentText tracking
                chipInfo.isVisible = newIsVisible
            }
        }

        // External chips
        externalPlugins.forEach { plugin ->
            val chipInfo = allChips.find { it.id == plugin.packageName } ?: return@forEach
            val isEnabled = sharedPreferences.getBoolean(plugin.preferenceKey, false)
            if (isEnabled) {
                val requestIntent = Intent().apply {
                    action = ChipPluginContract.ACTION_REQUEST_DATA
                    component = ComponentName(plugin.packageName, plugin.receiverClassName)
                }
                context.sendBroadcast(requestIntent)
            } else {
                chipInfo.isVisible = false
            }
        }

        sortAndRedrawChips()
    }

    private fun sortAndRedrawChips() {
        val visibleChips = allChips
            .filter { it.isVisible }
            .sortedByDescending { it.priority }

        // Ensure container is ConstraintLayout
        val container = chipContainer as? androidx.constraintlayout.widget.ConstraintLayout
            ?: throw IllegalStateException("chipContainer must be ConstraintLayout")

        // Use AutoTransition for smooth changes
        val transition = AutoTransition().apply {
            duration = 250
            addTarget(container)
        }
        TransitionManager.beginDelayedTransition(container, transition)

        container.removeAllViews()

        if (visibleChips.isEmpty()) return

        visibleChips.forEach { chipInfo ->
            chipInfo.view.id = ViewCompat.generateViewId()
            chipInfo.view.visibility = View.VISIBLE
            container.addView(chipInfo.view)
        }

        val constraintSet = ConstraintSet().apply {
            clone(container)
            visibleChips.forEachIndexed { index, chipInfo ->
                val id = chipInfo.view.id
                constrainWidth(id, ConstraintSet.WRAP_CONTENT)
                constrainHeight(id, ConstraintSet.WRAP_CONTENT)
                connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                if (index == 0) {
                    connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                } else {
                    val prevId = visibleChips[index - 1].view.id
                    connect(id, ConstraintSet.TOP, prevId, ConstraintSet.BOTTOM, 8)
                }
            }
        }
        constraintSet.applyTo(container)
    }

    fun onPreferencesChanged() {
        updateAllChips()
    }
}