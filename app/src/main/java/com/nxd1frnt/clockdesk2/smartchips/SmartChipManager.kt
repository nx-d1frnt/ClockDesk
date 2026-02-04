package com.nxd1frnt.clockdesk2.smartchips

import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.nxd1frnt.clockdesk2.FontManager
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.plugins.BatteryAlertPlugin
import com.nxd1frnt.clockdesk2.smartchips.plugins.UpdatePlugin
import com.nxd1frnt.clockdesk2.smartchips.plugins.BackgroundProgressPlugin
import com.nxd1frnt.clockdesk2.utils.Logger
import org.xmlpull.v1.XmlPullParser
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionSet
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.app.Activity
import android.app.ActivityOptions
import android.os.Bundle
import android.util.Pair
import android.view.Window

class SmartChipManager(
    private val context: Context,
    private val chipContainer: ViewGroup,
    private val sharedPreferences: SharedPreferences,
    private val fontManager: FontManager
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
        BatteryAlertPlugin(context),
        UpdatePlugin(context),
        BackgroundProgressPlugin(context)
    )
    var externalPlugins: List<ExternalChipPlugin> = emptyList()
    private val allChips = mutableListOf<ChipInfo>()

    private var isEditMode = false
    private var onEditClickListener: ((View) -> Unit)? = null

    fun setEditMode(enabled: Boolean, listener: (View) -> Unit) {
        isEditMode = enabled
        onEditClickListener = listener
    }

    private val dataUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != ChipPluginContract.ACTION_UPDATE_DATA) return
            val packageName = intent.getStringExtra(ChipPluginContract.KEY_PLUGIN_PACKAGE) ?: return
            val chipInfo = allChips.find { it.id == packageName } ?: return

            val isVisible = intent.getBooleanExtra(ChipPluginContract.KEY_CHIP_VISIBLE, true)
            val text = intent.getStringExtra(ChipPluginContract.KEY_CHIP_TEXT)
            val iconName = intent.getStringExtra(ChipPluginContract.KEY_CHIP_ICON_NAME)
            val clickActivity = intent.getStringExtra(ChipPluginContract.KEY_CHIP_CLICK_ACTIVITY)

            var contentChanged = false

            if (!isVisible) {
                if (chipInfo.isVisible) contentChanged = true
                chipInfo.isVisible = false
                chipInfo.clickActivityClassName = null
            } else if (text != null && iconName != null) {
                val textView = chipInfo.view.findViewById<TextView>(R.id.chip_text)
                val oldText = textView.text.toString()

                val success = updateExternalChipView(chipInfo.view, packageName, text, iconName)
                
                if (chipInfo.isVisible != success) contentChanged = true
                if (success && oldText != text) contentChanged = true 
                chipInfo.isVisible = success
                if (success) {
                    chipInfo.currentText = text
                    chipInfo.clickActivityClassName = clickActivity?.takeIf { it.isNotBlank() }
                }
            } else {
                if (chipInfo.isVisible) contentChanged = true
                chipInfo.isVisible = false
                chipInfo.clickActivityClassName = null
            }
            sortAndRedrawChips(contentChanged)
        }
    }

    init {
        internalPlugins.forEach { plugin ->
            val view = plugin.createView(context).apply {
                visibility = View.GONE
                tag = plugin.preferenceKey
            }
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
        handler.removeCallbacks(periodicUpdateRunnable)
        handler.post(periodicUpdateRunnable)
    }

    fun stopUpdates() {
        handler.removeCallbacks(periodicUpdateRunnable)
    }

    fun destroy() {
        stopUpdates()
        try {
            context.unregisterReceiver(dataUpdateReceiver)
        } catch (e: Exception) { }
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
                            .apply {
                                visibility = View.GONE
                                isClickable = true
                                isFocusable = true
                                tag = packageName
                            }

                        view.setOnClickListener {
                            if (isEditMode) {
                                onEditClickListener?.invoke(chipContainer)
                                return@setOnClickListener
                            }
                            val chipInfo = allChips.find { it.view == view } ?: return@setOnClickListener
                            chipInfo.clickActivityClassName?.let { cls ->
                                try {
                                    val fullClassName = if (cls.startsWith(".")) chipInfo.id + cls else cls
                                    val intent = Intent().setClassName(chipInfo.id, fullClassName)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    var options: Bundle? = null

                                    if (context is Activity) {
                                        val transitionName = "shared_chip_container"
                                        view.transitionName = transitionName

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                            options = ActivityOptions.makeSceneTransitionAnimation(
                                                context,
                                                Pair.create(view, transitionName)
                                            ).toBundle()
                                        }
                                        else {
                                            options = ActivityOptions.makeScaleUpAnimation(
                                                view, 0, 0, view.width, view.height
                                            ).toBundle()
                                        }
                                    } else {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                    context.startActivity(intent, options)
                                } catch (e: Exception) { }
                            }
                        }
                        allChips.add(ChipInfo(packageName, view, priority))
                    }
                } catch (e: Exception) {
                    Logger.w("SmartChipManager"){"Failed to parse plugin metadata from $packageName"}
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

                if (textView.text.toString() != text) {
                    textView.text = text
                }

                textView.isSelected = true
                return true
            }
        } catch (e: Exception) {
            Logger.e("SmartChipManager"){"Failed to update external chip view for $pkg"}
        }
        return false
    }

fun updateAllChips() {
        var isContentChanged = false

        // Internal chips
        internalPlugins.forEach { plugin ->
            val chipInfo = allChips.find { it.id == plugin.preferenceKey } ?: return@forEach
            val isSystemChip = plugin.preferenceKey == "system_bg_progress"
            val isEnabled = if (isSystemChip) true else sharedPreferences.getBoolean(plugin.preferenceKey, false)

            if (!isEnabled) {
                if (chipInfo.isVisible) isContentChanged = true // Если чип исчез, структура меняется
                chipInfo.isVisible = false
            } else {
                val textView = chipInfo.view.findViewById<TextView>(R.id.chip_text)
                val oldText = textView.text.toString()

                val newIsVisible = plugin.update(chipInfo.view, sharedPreferences)
                
                val newText = textView.text.toString()

                if (chipInfo.isVisible != newIsVisible) {
                    isContentChanged = true
                } else if (newIsVisible && oldText != newText) {
                    isContentChanged = true
                }
                
                chipInfo.isVisible = newIsVisible
            }
        }

        externalPlugins.forEach { plugin ->
            val chipInfo = allChips.find { it.id == plugin.packageName } ?: return@forEach
            val isEnabled = sharedPreferences.getBoolean(plugin.preferenceKey, false)
            
            if (!isEnabled && chipInfo.isVisible) {
                chipInfo.isVisible = false
                isContentChanged = true
            }
            
            if (isEnabled) {
                val requestIntent = Intent().apply {
                    action = ChipPluginContract.ACTION_REQUEST_DATA
                    component = ComponentName(plugin.packageName, plugin.receiverClassName)
                }
                context.sendBroadcast(requestIntent)
            }
        }

        sortAndRedrawChips(isContentChanged)
    }

    private fun sortAndRedrawChips(contentChanged: Boolean = false) {
        val visibleChips = allChips
            .filter { it.isVisible }
            .sortedByDescending { it.priority }

        val container = chipContainer as? ConstraintLayout
            ?: throw IllegalStateException("chipContainer must be ConstraintLayout")

        val currentTags = (0 until container.childCount).map { container.getChildAt(it).tag }
        val newTags = visibleChips.map { it.id }

        val transition = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            duration = 300 
            interpolator = FastOutSlowInInterpolator()

            addTransition(ChangeBounds().apply {
                resizeClip = false
            })
            addTransition(Fade(Fade.IN))
            addTransition(Fade(Fade.OUT))
        }

        if (currentTags == newTags) {
            visibleChips.forEach { chipInfo ->
                val textView = chipInfo.view.findViewById<TextView>(R.id.chip_text)
                if (!textView.isSelected) textView.isSelected = true
            }
            
            if (contentChanged) {
                TransitionManager.beginDelayedTransition(container, transition)
            }
            return
        }

        TransitionManager.beginDelayedTransition(container, transition)

        container.removeAllViews()
        if (visibleChips.isEmpty()) return

        visibleChips.forEach { chipInfo ->
            if (chipInfo.view.id == View.NO_ID) {
                chipInfo.view.id = ViewCompat.generateViewId()
            }
            (chipInfo.view.parent as? ViewGroup)?.removeView(chipInfo.view)

            chipInfo.view.visibility = View.VISIBLE
            chipInfo.view.tag = chipInfo.id

            container.addView(chipInfo.view)

            val textView = chipInfo.view.findViewById<TextView>(R.id.chip_text)
            textView?.isSelected = true

            fontManager.applyStyleToSmartChip(chipInfo.view)
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