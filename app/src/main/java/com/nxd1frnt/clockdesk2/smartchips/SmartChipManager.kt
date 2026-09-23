package com.nxd1frnt.clockdesk2.smartchips

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Pair
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import androidx.transition.TransitionValues
import androidx.transition.Visibility
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.plugins.AlarmChipPlugin
import com.nxd1frnt.clockdesk2.smartchips.plugins.BackgroundProgressPlugin
import com.nxd1frnt.clockdesk2.smartchips.plugins.BatteryAlertPlugin
import com.nxd1frnt.clockdesk2.smartchips.plugins.UpdatePlugin
import com.nxd1frnt.clockdesk2.smartchips.plugins.WeatherAlertPlugin
import com.nxd1frnt.clockdesk2.smartchips.plugins.WeatherChipPlugin
import com.nxd1frnt.clockdesk2.utils.FontManager
import com.nxd1frnt.clockdesk2.utils.Logger
import org.xmlpull.v1.XmlPullParser

class SmartChipManager(
    private val context: Context,
    private val chipContainer: ViewGroup,
    private val sharedPreferences: SharedPreferences,
    private val fontManager: FontManager
) : DefaultLifecycleObserver {
    /**
     * Internal representation of a smart chip instance.
     *
     * @property id Unique identifier for this specific chip instance (e.g. "download_task_1").
     * @property channelId The Notification Channel / Category ID (e.g. "downloads_channel") used to group
     *                     and control user preferences (enable/disable and layout ordering).
     * @property view Inflated UI View for displaying the chip.
     */
    private data class ChipInfo(
        val id: String,
        val channelId: String,
        val view: View,
       //val priority: Int,
        var isVisible: Boolean = false,
        var currentText: String? = null,
        var clickActivityClassName: String? = null
    )

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 5000L

    private val pluginTimers = mutableMapOf<String, Runnable>()
    private val timeoutRunnables = mutableMapOf<String, Runnable>()
    private val pluginTimeoutCounts = mutableMapOf<String, Int>()

    private var isFirstLoad = true
    private val springInterpolator = PathInterpolator(0.2f, 1.0f, 0.3f, 1.0f)

    var isStackExpanded: Boolean = false
        private set

    private val autoCollapseHandler = Handler(Looper.getMainLooper())
    private val autoCollapseRunnable = Runnable {
        if (isStackExpanded) {
            collapseStack()
        }
    }

    fun expandStack() {
        if (isStackExpanded) return
        isStackExpanded = true
        autoCollapseHandler.removeCallbacks(autoCollapseRunnable)
        autoCollapseHandler.postDelayed(autoCollapseRunnable, 8000L)
        sortAndRedrawChips(contentChanged = true)
    }

    fun collapseStack() {
        if (!isStackExpanded) return
        isStackExpanded = false
        autoCollapseHandler.removeCallbacks(autoCollapseRunnable)
        (chipContainer.parent as? android.widget.ScrollView)?.smoothScrollTo(0, 0)
        sortAndRedrawChips(contentChanged = true)
    }

    fun resetAutoCollapseTimer() {
        if (isStackExpanded) {
            autoCollapseHandler.removeCallbacks(autoCollapseRunnable)
            autoCollapseHandler.postDelayed(autoCollapseRunnable, 8000L)
        }
    }

    fun setStackOverflowEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("smart_chips_stack_overflow", enabled).apply()
        if (!enabled && isStackExpanded) {
            isStackExpanded = false
            autoCollapseHandler.removeCallbacks(autoCollapseRunnable)
        }
        onPreferencesChanged()
    }

//    private val periodicUpdateRunnable = object : Runnable {
//        override fun run() {
//            updateAllChips()
//            handler.postDelayed(this, updateInterval)
//        }
//    }

    private val internalPlugins: List<ISmartChip> = listOf(
        BatteryAlertPlugin(context),
        com.nxd1frnt.clockdesk2.smartchips.plugins.CompanionBatteryChipPlugin(context),
        com.nxd1frnt.clockdesk2.smartchips.plugins.DeviceConnectionNoticeChipPlugin(context),
        com.nxd1frnt.clockdesk2.smartchips.plugins.NotificationSmartChipPlugin(context),
        UpdatePlugin(context),
        BackgroundProgressPlugin(context),
        AlarmChipPlugin(context),
        WeatherChipPlugin(context),
        WeatherAlertPlugin(context)
    )
    var externalPlugins: List<ExternalChipPlugin> = emptyList()
    private val allChips = mutableListOf<ChipInfo>()

    private var isEditMode = false
    private var onEditClickListener: ((View) -> Unit)? = null

    private var isReceiverRegistered = false

    private fun isReceiverAvailable(packageName: String, className: String): Boolean {
        return try {
            val componentName = ComponentName(packageName, className)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getReceiverInfo(componentName, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getReceiverInfo(componentName, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun startTimeoutCheck(packageName: String) {
        timeoutRunnables[packageName]?.let { handler.removeCallbacks(it) }

        val timeoutRunnable = Runnable {
            Logger.w("SmartChipManager") { "Plugin $packageName response timed out. Hiding chip." }
            timeoutRunnables.remove(packageName)
            val matchingChannels = externalPlugins.filter { it.packageName == packageName }.map { it.preferenceKey }
            if (matchingChannels.isEmpty()) return@Runnable

            val count = (pluginTimeoutCounts[packageName] ?: 0) + 1
            pluginTimeoutCounts[packageName] = count
            if (count >= 3) {
                pluginTimers[packageName]?.let { handler.removeCallbacks(it) }
                pluginTimers.remove(packageName)
                Logger.w("SmartChipManager") { "Plugin $packageName timed out 3 times consecutively. Stopped timer." }
            }

            var contentChanged = false
            allChips.filter { it.channelId in matchingChannels }.forEach { chipInfo ->
                if (chipInfo.isVisible) {
                    chipInfo.isVisible = false
                    chipInfo.clickActivityClassName = null
                    contentChanged = true
                }
            }
            if (contentChanged) {
                sortAndRedrawChips(contentChanged = true)
            }
        }
        timeoutRunnables[packageName] = timeoutRunnable
        handler.postDelayed(timeoutRunnable, 15000L)
    }

    private fun clearPluginTimeout(packageName: String) {
        timeoutRunnables[packageName]?.let {
            handler.removeCallbacks(it)
            timeoutRunnables.remove(packageName)
        }
        pluginTimeoutCounts[packageName] = 0
    }

    fun setEditMode(enabled: Boolean, listener: (View) -> Unit) {
        isEditMode = enabled
        onEditClickListener = listener
        updateChipsClickability()
    }


    private fun updateChipsClickability() {
        for (i in 0 until chipContainer.childCount) {
            val child = chipContainer.getChildAt(i)
            if (isEditMode) {
                child.isClickable = false
                child.isFocusable = false
            } else {
                val chipInfo = allChips.find { it.view == child }
                val hasClick = chipInfo?.clickActivityClassName != null || child.hasOnClickListeners()
                child.isClickable = hasClick
                child.isFocusable = hasClick
            }
        }
    }

    /**
     * Receiver handling data updates from external smart chip plugins.
     * Supports both single-chip broadcast payloads and multi-chip channel payloads ([ChipPluginContract.KEY_CHIPS_ARRAY]).
     */
    private val dataUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != ChipPluginContract.ACTION_UPDATE_DATA) return
            val packageName = intent.getStringExtra(ChipPluginContract.KEY_PLUGIN_PACKAGE) ?: return

            val matchingPlugins = externalPlugins.filter { it.packageName == packageName }
            if (matchingPlugins.isEmpty()) return

            clearPluginTimeout(packageName)

            val updateIntervalSec = intent.getIntExtra("update_interval_seconds", -1)
            var contentChanged = false

            /**
             * Processes an update for a single chip instance under a specified Notification Channel.
             *
             * @param chipId Unique identifier for this chip instance.
             * @param channelId Category/Channel ID controlling user preference toggles & layout order.
             * @param isVisible Desired visibility state requested by the plugin.
             * @param text Display text.
             * @param iconName Drawable resource name in plugin package.
             * @param clickActivity Optional Activity class to launch on chip click.
             */
            fun processSingleChipUpdate(
                chipId: String,
                channelId: String,
                isVisible: Boolean,
                text: String?,
                iconName: String?,
                clickActivity: String?
            ) {
                // Enforce Channel-level user preference toggle
                val isChannelEnabled = sharedPreferences.getBoolean(channelId, true)
                val effectiveVisible = isVisible && isChannelEnabled

                var chipInfo = allChips.find { it.id == chipId }
                // Dynamically instantiate chip View if a new chipId arrives under an enabled channel
                if (chipInfo == null && effectiveVisible && text != null && iconName != null) {
                    val view = LayoutInflater.from(context)
                        .inflate(R.layout.smart_chip_layout, chipContainer, false)
                        .apply {
                            visibility = View.GONE
                            isClickable = true
                            isFocusable = true
                            tag = chipId
                        }
                    setupExternalChipClickListener(view, chipId, packageName)
                    chipInfo = ChipInfo(chipId, channelId, view)
                    allChips.add(chipInfo)
                }

                val targetChipInfo = chipInfo ?: return

                if (!effectiveVisible) {
                    if (targetChipInfo.isVisible) contentChanged = true
                    targetChipInfo.isVisible = false
                    targetChipInfo.clickActivityClassName = null
                } else if (text != null && iconName != null) {
                    val textView = targetChipInfo.view.findViewById<TextView>(R.id.chip_text)
                    val oldText = textView.text.toString()

                    val success = updateExternalChipView(targetChipInfo.view, packageName, text, iconName)

                    if (targetChipInfo.isVisible != success) contentChanged = true
                    if (success && oldText != text) contentChanged = true
                    targetChipInfo.isVisible = success
                    if (success) {
                        targetChipInfo.currentText = text
                        targetChipInfo.clickActivityClassName = clickActivity?.takeIf { it.isNotBlank() }
                    }
                } else {
                    if (targetChipInfo.isVisible) contentChanged = true
                    targetChipInfo.isVisible = false
                    targetChipInfo.clickActivityClassName = null
                }
            }

            // Multi-chip broadcast payload (KEY_CHIPS_ARRAY contains ArrayList<Bundle>)
            if (intent.hasExtra(ChipPluginContract.KEY_CHIPS_ARRAY)) {
                val chipsList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(ChipPluginContract.KEY_CHIPS_ARRAY, Bundle::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Bundle>(ChipPluginContract.KEY_CHIPS_ARRAY)
                }

                chipsList?.forEach { chipBundle ->
                    val defaultChannel = matchingPlugins.firstOrNull()?.preferenceKey ?: packageName
                    val channelId = chipBundle.getString(ChipPluginContract.KEY_CHANNEL_ID) ?: defaultChannel
                    val chipId = chipBundle.getString(ChipPluginContract.KEY_CHIP_ID) ?: channelId
                    val isVisible = chipBundle.getBoolean(ChipPluginContract.KEY_CHIP_VISIBLE, true)
                    val text = chipBundle.getString(ChipPluginContract.KEY_CHIP_TEXT)
                    val iconName = chipBundle.getString(ChipPluginContract.KEY_CHIP_ICON_NAME)
                    val clickActivity = chipBundle.getString(ChipPluginContract.KEY_CHIP_CLICK_ACTIVITY)
                    processSingleChipUpdate(chipId, channelId, isVisible, text, iconName, clickActivity)
                }
            } else {
                // Legacy single-chip broadcast payload
                val defaultChannel = matchingPlugins.firstOrNull()?.preferenceKey ?: packageName
                val channelId = intent.getStringExtra(ChipPluginContract.KEY_CHANNEL_ID) ?: defaultChannel
                val chipId = intent.getStringExtra(ChipPluginContract.KEY_CHIP_ID) ?: channelId

                val isVisible = intent.getBooleanExtra(ChipPluginContract.KEY_CHIP_VISIBLE, true)
                val text = intent.getStringExtra(ChipPluginContract.KEY_CHIP_TEXT)
                val iconName = intent.getStringExtra(ChipPluginContract.KEY_CHIP_ICON_NAME)
                val clickActivity = intent.getStringExtra(ChipPluginContract.KEY_CHIP_CLICK_ACTIVITY)

                processSingleChipUpdate(chipId, channelId, isVisible, text, iconName, clickActivity)
            }

            managePluginTimer(packageName, updateIntervalSec, true)
            sortAndRedrawChips(contentChanged)
        }
    }

    init {
        chipContainer.viewTreeObserver.addOnScrollChangedListener {
            resetAllChipsScale()
        }
        internalPlugins.forEach { plugin ->
            if (plugin !is IMultiSmartChip) {
                val view = plugin.createView(context).apply {
                    visibility = View.GONE
                    tag = plugin.preferenceKey
                }
                allChips.add(ChipInfo(plugin.preferenceKey, plugin.preferenceKey, view))
            }
            plugin.setOnStateChangeListener {
                updateAllChips()
            }
        }
        discoverExternalPlugins()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        internalPlugins.forEach { it.startListening() } // Будим плагины
        registerReceiver()
        requestInitialState()
        Logger.d("SmartChipManager") { "Started listening for chip updates." }
    }

    // Вызывается автоматически, когда экран скрывается
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        internalPlugins.forEach { it.stopListening() }
        pluginTimers.values.forEach { handler.removeCallbacks(it) }
        pluginTimers.clear()

        timeoutRunnables.values.forEach { handler.removeCallbacks(it) }
        timeoutRunnables.clear()
        pluginTimeoutCounts.clear()

        autoCollapseHandler.removeCallbacks(autoCollapseRunnable)
        if (isStackExpanded) {
            isStackExpanded = false
        }

        unregisterReceiver()
        Logger.d("SmartChipManager") { "Stopped listening. App is sleeping." }
    }

    // Вызывается при полном уничтожении экрана (защита от утечек)
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        unregisterReceiver() // На всякий случай
    }

    private fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter(ChipPluginContract.ACTION_UPDATE_DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_EXPORTED
            } else 0
            context.registerReceiver(dataUpdateReceiver, filter, flags)
        } else {
            ContextCompat.registerReceiver(context, dataUpdateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        isReceiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(dataUpdateReceiver)
            isReceiverRegistered = false
        } catch (e: IllegalArgumentException) {
            // Игнорируем, если ресивер уже был отписан
        }
    }

    private fun requestInitialState() {
        var isContentChanged = false

        // Обновляем внутренние плагины (они читают статус мгновенно)
        internalPlugins.forEach { plugin ->
            if (plugin is IMultiSmartChip) {
                if (updateMultiChipPlugin(plugin)) isContentChanged = true
            } else {
                val chipInfo = allChips.find { it.id == plugin.preferenceKey } ?: return@forEach
                val isEnabled = sharedPreferences.getBoolean(plugin.preferenceKey, true)

                if (!isEnabled) {
                    if (chipInfo.isVisible) isContentChanged = true
                    chipInfo.isVisible = false
                } else {
                    val newIsVisible = plugin.update(chipInfo.view, sharedPreferences)
                    if (chipInfo.isVisible != newIsVisible) isContentChanged = true
                    chipInfo.isVisible = newIsVisible
                }
            }
        }

        // Запрашиваем данные у внешних плагинов
        val distinctReceivers = externalPlugins.distinctBy { Pair(it.packageName, it.receiverClassName) }
        distinctReceivers.forEach { plugin ->
            val receiverChips = externalPlugins.filter {
                it.packageName == plugin.packageName && it.receiverClassName == plugin.receiverClassName
            }
            val anyEnabled = receiverChips.any { sharedPreferences.getBoolean(it.preferenceKey, false) }

            if (anyEnabled) {
                if (!isReceiverAvailable(plugin.packageName, plugin.receiverClassName)) {
                    receiverChips.forEach { chipDef ->
                        val chipInfo = allChips.find { it.id == chipDef.preferenceKey }
                        if (chipInfo?.isVisible == true) {
                            chipInfo.isVisible = false
                            isContentChanged = true
                        }
                    }
                    return@forEach
                }

                val requestIntent = Intent().apply {
                    action = ChipPluginContract.ACTION_REQUEST_DATA
                    component = ComponentName(plugin.packageName, plugin.receiverClassName)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES) // Forces system to deliver the broadcast even if the receiver is not active
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                context.sendBroadcast(requestIntent)
                startTimeoutCheck(plugin.packageName)
            } else {
                receiverChips.forEach { chipDef ->
                    val chipInfo = allChips.find { it.id == chipDef.preferenceKey }
                    if (chipInfo?.isVisible == true) {
                        chipInfo.isVisible = false
                        isContentChanged = true
                    }
                }
            }
        }

        if (isContentChanged) {
            sortAndRedrawChips(true)
        }
    }

    private fun managePluginTimer(packageName: String, intervalSec: Int, isVisible: Boolean) {
        pluginTimers[packageName]?.let { handler.removeCallbacks(it) }
        pluginTimers.remove(packageName)

        if (intervalSec > 0 && isReceiverRegistered) {
            val runnable = object : Runnable {
                override fun run() {
                    val plugin = externalPlugins.find { it.packageName == packageName } ?: return
                    if (!isReceiverAvailable(plugin.packageName, plugin.receiverClassName)) {
                        Logger.w("SmartChipManager") { "Plugin receiver not available: $packageName. Stopping timer." }
                        pluginTimers[packageName]?.let { handler.removeCallbacks(it) }
                        pluginTimers.remove(packageName)

                        val chipInfo = allChips.find { it.id == plugin.preferenceKey }
                        if (chipInfo?.isVisible == true) {
                            chipInfo.isVisible = false
                            sortAndRedrawChips(contentChanged = true)
                        }
                        return
                    }

                    val requestIntent = Intent(ChipPluginContract.ACTION_REQUEST_DATA).apply {
                        component = ComponentName(plugin.packageName, plugin.receiverClassName)
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES) // Пробиваем сон
                    }
                    context.sendBroadcast(requestIntent)
                    Logger.d("SmartChipManager"){"Plugin timer triggered: $packageName"}
                    startTimeoutCheck(packageName)
                    handler.postDelayed(this, intervalSec * 1000L)
                }
            }
            pluginTimers[packageName] = runnable
            Logger.d("SmartChipManager"){"Plugin timer set: $packageName"}
            handler.postDelayed(runnable, intervalSec * 1000L)
        }
    }

    private fun setupExternalChipClickListener(view: View, chipId: String, packageName: String) {
        view.setOnClickListener {
            if (isEditMode) {
                onEditClickListener?.invoke(chipContainer)
                return@setOnClickListener
            }
            val chipInfo = allChips.find { it.view == view } ?: return@setOnClickListener
            chipInfo.clickActivityClassName?.let { cls ->
                try {
                    val pluginPkg = externalPlugins.find { it.preferenceKey == chipInfo.id }?.packageName ?: packageName
                    val fullClassName = if (cls.startsWith(".")) pluginPkg + cls else cls
                    val intent = Intent().setClassName(pluginPkg, fullClassName)
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
                        } else {
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
    }

    private fun discoverExternalPlugins() {
        Thread {
            val pm = context.packageManager
            val queryIntent = Intent(ChipPluginContract.ACTION_QUERY_PLUGINS)
            val receivers = pm.queryBroadcastReceivers(queryIntent, PackageManager.GET_META_DATA)

            class DiscoveredPlugin(val pkg: String, val cls: String, val key: String, val name: String)
            val foundData = mutableListOf<DiscoveredPlugin>()

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

                        while (parser.next() != XmlPullParser.END_DOCUMENT) {
                            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "smart-chip-plugin") {
                                val prefKey = parser.getAttributeValue(null, "preferenceKey")
                                val dispName = parser.getAttributeValue(null, "displayName")
                                if (prefKey != null && dispName != null) {
                                    foundData.add(DiscoveredPlugin(packageName, className, prefKey, dispName))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.w("SmartChipManager"){"Failed to parse plugin metadata from $packageName"}
                    }
                }
            }

            handler.post {
                val foundPlugins = mutableListOf<ExternalChipPlugin>()
                foundData.forEach { data ->
                    foundPlugins.add(ExternalChipPlugin(data.pkg, data.cls, data.key, data.name))
                    val view = LayoutInflater.from(context)
                        .inflate(R.layout.smart_chip_layout, chipContainer, false)
                        .apply {
                            visibility = View.GONE
                            isClickable = true
                            isFocusable = true
                            tag = data.key
                        }
                    Logger.d("SmartChipManager"){"Plugin ${data.pkg} loaded chip: ${data.key}"}

                    setupExternalChipClickListener(view, data.key, data.pkg)
                    allChips.add(ChipInfo(data.key, data.key, view))
                }
                this.externalPlugins = foundPlugins
                requestInitialState()
            }
        }.start()
    }

    private fun animateTextChange(textView: TextView, newText: String) {
        if (textView.text.toString() == newText) return

        textView.animate().cancel()

        if (textView.text.isNullOrEmpty() || textView.visibility != View.VISIBLE) {
            textView.text = newText
            textView.isSelected = true
            return
        }

        val container = chipContainer as? ViewGroup

        textView.animate()
            .alpha(0f)
            .setDuration(100)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                if (container != null) {
                    val boundsTransition = TransitionSet().apply {
                        ordering = TransitionSet.ORDERING_TOGETHER
                        duration = 350L
                        interpolator = springInterpolator
                        addTransition(ChangeBounds().apply {
                            resizeClip = false
                        })
                    }
                    TransitionManager.beginDelayedTransition(container, boundsTransition)
                }

                textView.text = newText
                textView.isSelected = true

                textView.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            }
            .start()
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
                    animateTextChange(textView, text)
                } else {
                    textView.isSelected = true
                }

                return true
            }
        } catch (e: Exception) {
            Logger.e("SmartChipManager"){"Failed to update external chip view for $pkg"}
        }
        return false
    }

    private fun updateMultiChipPlugin(plugin: IMultiSmartChip): Boolean {
        var isContentChanged = false
        val isEnabled = sharedPreferences.getBoolean(plugin.preferenceKey, true)

        if (!isEnabled) {
            allChips.filter { it.channelId == plugin.preferenceKey }.forEach { chipInfo ->
                if (chipInfo.isVisible) isContentChanged = true
                chipInfo.isVisible = false
            }
            return isContentChanged
        }

        val items = plugin.getChips(sharedPreferences)
        val activeIds = items.filter { it.isVisible }.map { it.chipId }.toSet()

        // Hide any chips that are no longer active
        allChips.filter { it.channelId == plugin.preferenceKey }.forEach { chipInfo ->
            if (chipInfo.id !in activeIds) {
                if (chipInfo.isVisible) isContentChanged = true
                chipInfo.isVisible = false
            }
        }

        // Add or update active chips
        items.forEach { item ->
            var chipInfo = allChips.find { it.id == item.chipId }
            if (chipInfo == null && item.isVisible) {
                val view = LayoutInflater.from(context)
                    .inflate(R.layout.smart_chip_layout, chipContainer, false)
                    .apply {
                        visibility = View.GONE
                        tag = item.chipId
                    }
                chipInfo = ChipInfo(item.chipId, plugin.preferenceKey, view)
                allChips.add(chipInfo)
                isContentChanged = true
            }

            if (chipInfo != null) {
                val textView = chipInfo.view.findViewById<TextView>(R.id.chip_text)
                val iconView = chipInfo.view.findViewById<ImageView>(R.id.chip_icon)
                val oldText = textView?.text?.toString()

                if (item.iconRes != null && iconView != null && iconView.tag != item.iconRes) {
                    iconView.setImageResource(item.iconRes)
                    iconView.tag = item.iconRes
                }
                if (textView != null && oldText != item.text) {
                    textView.text = item.text
                }
                if (textView != null && !textView.isSelected) {
                    textView.isSelected = true
                }

                if (chipInfo.isVisible != item.isVisible) isContentChanged = true
                if (item.isVisible && oldText != item.text) isContentChanged = true
                chipInfo.isVisible = item.isVisible
                chipInfo.currentText = item.text
            }
        }

        val allPluginChipIds = items.map { it.chipId }.toSet()
        val toRemove = allChips.filter { it.channelId == plugin.preferenceKey && it.id !in allPluginChipIds && !it.isVisible }
        toRemove.forEach { deadChip ->
            (deadChip.view.parent as? ViewGroup)?.removeView(deadChip.view)
        }
        allChips.removeAll(toRemove.toSet())

        return isContentChanged
    }

    fun updateAllChips() {
        var isContentChanged = false

        // Internal chips
        internalPlugins.forEach { plugin ->
            if (plugin is IMultiSmartChip) {
                if (updateMultiChipPlugin(plugin)) isContentChanged = true
            } else {
                val chipInfo = allChips.find { it.id == plugin.preferenceKey } ?: return@forEach
                val isSystemChip = plugin.preferenceKey == "system_bg_progress"
                val isEnabled = sharedPreferences.getBoolean(plugin.preferenceKey, true)
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
        }

        externalPlugins.forEach { plugin ->
            val isEnabled = sharedPreferences.getBoolean(plugin.preferenceKey, false)

            allChips.filter { it.channelId == plugin.preferenceKey }.forEach { chipInfo ->
                if (!isEnabled && chipInfo.isVisible) {
                    chipInfo.isVisible = false
                    isContentChanged = true
                }

                if (isEnabled && !isReceiverAvailable(plugin.packageName, plugin.receiverClassName)) {
                    if (chipInfo.isVisible) {
                        chipInfo.isVisible = false
                        isContentChanged = true
                    }
                }
            }
        }

        sortAndRedrawChips(isContentChanged)
    }

    private var isRedrawPending = false
    private var pendingContentChanged = false

    private val redrawRunnable = Runnable {
        isRedrawPending = false
        val changed = pendingContentChanged
        pendingContentChanged = false
        executeSortAndRedrawChips(changed)
    }

    private fun sortAndRedrawChips(contentChanged: Boolean = false) {
        if (contentChanged) pendingContentChanged = true
        if (isRedrawPending) return
        isRedrawPending = true
        handler.removeCallbacks(redrawRunnable)
        handler.post(redrawRunnable)
    }

    private fun getOrderedVisibleChips(): List<ChipInfo> {
        val orderString = sharedPreferences.getString("smart_chip_order", "system_bg_progress,show_notifications_chip,show_battery_alert,show_companion_battery,show_device_connection_chip,show_updates,show_alarm_chip,show_weather_chip,show_weather_alert_chip") ?: ""
        val orderList = orderString.split(",").map { it.trim() }

        return allChips
            .filter { chipInfo ->
                val isChannelEnabled = sharedPreferences.getBoolean(chipInfo.channelId, true)
                chipInfo.isVisible && isChannelEnabled
            }
            .sortedBy { chipInfo ->
                val index = orderList.indexOf(chipInfo.channelId)
                if (index != -1) index else Int.MAX_VALUE
            }
    }

    private fun getUsableHeight(): Int {
        val parentView = chipContainer.parent as? View
        val availableHeight = if (parentView != null && parentView.height > 0) {
            parentView.height - parentView.paddingTop - parentView.paddingBottom
        } else {
            (165 * context.resources.displayMetrics.density).toInt()
        }
        return (availableHeight - chipContainer.paddingTop - chipContainer.paddingBottom).coerceAtLeast(0)
    }

    private fun getChipHeight(view: View): Int {
        if (view.height > 0) return view.height
        if (view.measuredHeight > 0) return view.measuredHeight
        val widthSpec = View.MeasureSpec.makeMeasureSpec(
            if (chipContainer.width > 0) chipContainer.width else (165 * context.resources.displayMetrics.density).toInt(),
            View.MeasureSpec.AT_MOST
        )
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return if (view.measuredHeight > 0) view.measuredHeight else (36 * context.resources.displayMetrics.density).toInt()
    }

    private fun calculateStackAnchor(visibleChips: List<ChipInfo>, usableHeight: Int): Int {
        if (visibleChips.size <= 1) return -1
        val chipMargin = (8 * context.resources.displayMetrics.density).toInt()
        val peekReserve = (16 * context.resources.displayMetrics.density).toInt()

        var totalHeight = 0
        visibleChips.forEachIndexed { index, chipInfo ->
            val h = getChipHeight(chipInfo.view)
            totalHeight += h
            if (index < visibleChips.size - 1) totalHeight += chipMargin
        }

        if (totalHeight <= usableHeight) {
            return -1
        }

        var accumulatedHeight = 0
        var anchorIndex = 0
        for (i in visibleChips.indices) {
            val h = getChipHeight(visibleChips[i].view)
            val nextTotal = accumulatedHeight + h + peekReserve
            if (nextTotal <= usableHeight || i == 0) {
                anchorIndex = i
                accumulatedHeight += h + chipMargin
            } else {
                break
            }
        }
        return anchorIndex.coerceAtMost(visibleChips.size - 2)
    }

    private fun applyLayoutAndTransforms(container: ConstraintLayout, visibleChips: List<ChipInfo>) {
        val isStackOverflowEnabled = sharedPreferences.getBoolean("smart_chips_stack_overflow", false)
        val usableHeight = getUsableHeight()
        val anchorIndex = if (isStackOverflowEnabled) calculateStackAnchor(visibleChips, usableHeight) else -1
        val hasOverflow = anchorIndex != -1

        if (!hasOverflow && isStackExpanded) {
            isStackExpanded = false
            autoCollapseHandler.removeCallbacks(autoCollapseRunnable)
        }

        val constraintSet = ConstraintSet().apply {
            clone(container)
            if (hasOverflow && !isStackExpanded) {
                for (i in 0 until anchorIndex) {
                    val id = visibleChips[i].view.id
                    constrainWidth(id, ConstraintSet.WRAP_CONTENT)
                    constrainHeight(id, ConstraintSet.WRAP_CONTENT)
                    connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    if (i == 0) {
                        connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    } else {
                        val prevId = visibleChips[i - 1].view.id
                        connect(id, ConstraintSet.TOP, prevId, ConstraintSet.BOTTOM, 8)
                    }
                }

                val anchorId = visibleChips[anchorIndex].view.id
                constrainWidth(anchorId, ConstraintSet.WRAP_CONTENT)
                constrainHeight(anchorId, ConstraintSet.WRAP_CONTENT)
                connect(anchorId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                if (anchorIndex == 0) {
                    connect(anchorId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                } else {
                    val prevId = visibleChips[anchorIndex - 1].view.id
                    connect(anchorId, ConstraintSet.TOP, prevId, ConstraintSet.BOTTOM, 8)
                }

                for (i in (anchorIndex + 1) until visibleChips.size) {
                    val id = visibleChips[i].view.id
                    constrainWidth(id, ConstraintSet.WRAP_CONTENT)
                    constrainHeight(id, ConstraintSet.WRAP_CONTENT)
                    connect(id, ConstraintSet.END, anchorId, ConstraintSet.END)
                    connect(id, ConstraintSet.TOP, anchorId, ConstraintSet.TOP)
                }
            } else {
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
        }
        constraintSet.applyTo(container)

        val density = context.resources.displayMetrics.density
        if (hasOverflow && !isStackExpanded) {
            for (i in 0 until anchorIndex) {
                val v = visibleChips[i].view
                val targetAlpha = (v.getTag(R.id.tag_target_alpha) as? Float) ?: 1.0f
                v.visibility = View.VISIBLE
                v.findViewById<TextView>(R.id.chip_stack_badge)?.visibility = View.GONE
                ViewCompat.setTranslationZ(v, 0f)
                v.animate()
                    .translationY(0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(targetAlpha)
                    .setDuration(350L)
                    .setInterpolator(springInterpolator)
                    .start()
            }

            val anchorView = visibleChips[anchorIndex].view
            val anchorAlpha = (anchorView.getTag(R.id.tag_target_alpha) as? Float) ?: 1.0f
            anchorView.visibility = View.VISIBLE
            ViewCompat.setTranslationZ(anchorView, 12f)
            anchorView.animate()
                .translationY(0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(anchorAlpha)
                .setDuration(350L)
                .setInterpolator(springInterpolator)
                .start()

            val overflowCount = visibleChips.size - 1 - anchorIndex
            val anchorBadge = anchorView.findViewById<TextView>(R.id.chip_stack_badge)
            if (anchorBadge != null && overflowCount > 0) {
                fontManager.applyStyleToSmartChip(anchorView)
                anchorBadge.text = String.format(context.getString(R.string.chip_stack_badge_format), overflowCount)
                anchorBadge.visibility = View.VISIBLE
            }

            val overflowChips = visibleChips.subList(anchorIndex + 1, visibleChips.size)
            overflowChips.forEachIndexed { i, chipInfo ->
                val v = chipInfo.view
                val targetAlpha = (v.getTag(R.id.tag_target_alpha) as? Float) ?: 1.0f
                v.findViewById<TextView>(R.id.chip_stack_badge)?.visibility = View.GONE
                when (i) {
                    0 -> {
                        v.visibility = View.VISIBLE
                        ViewCompat.setTranslationZ(v, 8f)
                        v.animate()
                            .translationY(8f * density)
                            .scaleX(0.94f)
                            .scaleY(0.94f)
                            .alpha(targetAlpha * 0.85f)
                            .setDuration(350L)
                            .setInterpolator(springInterpolator)
                            .start()
                    }
                    1 -> {
                        v.visibility = View.VISIBLE
                        ViewCompat.setTranslationZ(v, 4f)
                        v.animate()
                            .translationY(16f * density)
                            .scaleX(0.88f)
                            .scaleY(0.88f)
                            .alpha(targetAlpha * 0.65f)
                            .setDuration(350L)
                            .setInterpolator(springInterpolator)
                            .start()
                    }
                    else -> {
                        ViewCompat.setTranslationZ(v, 0f)
                        v.animate()
                            .translationY(16f * density)
                            .scaleX(0.82f)
                            .scaleY(0.82f)
                            .alpha(0f)
                            .setDuration(350L)
                            .setInterpolator(springInterpolator)
                            .withEndAction {
                                if (!isStackExpanded) v.visibility = View.INVISIBLE
                            }
                            .start()
                    }
                }
            }
        } else {
            visibleChips.forEach { chipInfo ->
                val v = chipInfo.view
                val targetAlpha = (v.getTag(R.id.tag_target_alpha) as? Float) ?: 1.0f
                v.visibility = View.VISIBLE
                ViewCompat.setTranslationZ(v, 0f)
                v.findViewById<TextView>(R.id.chip_stack_badge)?.visibility = View.GONE
                v.animate()
                    .translationY(0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(targetAlpha)
                    .setDuration(350L)
                    .setInterpolator(springInterpolator)
                    .start()
            }
        }
    }

    private fun executeSortAndRedrawChips(contentChanged: Boolean = false) {
        val visibleChips = getOrderedVisibleChips()

        val container = chipContainer as? ConstraintLayout
            ?: throw IllegalStateException("chipContainer must be ConstraintLayout")

        val currentTags = (0 until container.childCount).map { container.getChildAt(it).tag }
        val newTags = visibleChips.map { it.id }

        val transition = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            duration = 350L
            interpolator = springInterpolator

            addTransition(ChangeBounds().apply {
                resizeClip = false
            })
            addTransition(ScaleAndFade())
        }

        if (currentTags == newTags) {
            visibleChips.forEach { chipInfo ->
                val textView = chipInfo.view.findViewById<TextView>(R.id.chip_text)
                if (textView != null && !textView.isSelected) textView.isSelected = true
            }

            if (contentChanged) {
                TransitionManager.beginDelayedTransition(container, transition)
                applyLayoutAndTransforms(container, visibleChips)
            }
            return
        }

        TransitionManager.beginDelayedTransition(container, transition)

        val visibleViews = visibleChips.map { it.view }.toSet()
        val childrenToRemove = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child !in visibleViews) {
                childrenToRemove.add(child)
            }
        }
        childrenToRemove.forEach { container.removeView(it) }

        if (visibleChips.isEmpty()) return

        visibleChips.forEach { chipInfo ->
            val v = chipInfo.view
            if (v.id == View.NO_ID) {
                v.id = ViewCompat.generateViewId()
            }
            if (v.parent != container) {
                (v.parent as? ViewGroup)?.removeView(v)
                v.visibility = View.VISIBLE
                v.tag = chipInfo.id
                container.addView(v)
            } else {
                v.visibility = View.VISIBLE
            }

            setupChipTouchFeedback(v)

            val textView = v.findViewById<TextView>(R.id.chip_text)
            textView?.isSelected = true

            fontManager.applyStyleToSmartChip(v)
        }
        Logger.d("SmartChipManager") { "Chips updated" }

        applyLayoutAndTransforms(container, visibleChips)
        updateChipsClickability()

        if (isFirstLoad && visibleChips.isNotEmpty()) {
            isFirstLoad = false
            animateStaggeredEntrance(visibleChips)
        }
    }

    private fun resetAllChipsScale() {
        for (i in 0 until chipContainer.childCount) {
            val child = chipContainer.getChildAt(i)
            if (child.scaleX != 1.0f || child.scaleY != 1.0f) {
                child.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .setInterpolator(OvershootInterpolator(1.4f))
                    .start()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupChipTouchFeedback(view: View) {
        if (view.getTag(R.id.tag_touch_listener_set) == true) return
        view.setTag(R.id.tag_touch_listener_set, true)

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val isStackOverflowEnabled = sharedPreferences.getBoolean("smart_chips_stack_overflow", false)
                if (!isStackOverflowEnabled && (v.scaleX != 1.0f || v.scaleY != 1.0f)) {
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                v.animate().cancel()
                v.scaleX = 1.0f
                v.scaleY = 1.0f
                v.translationY = 0f
            }
        })

        view.setOnTouchListener { v, event ->
            if (!v.isClickable && !isEditMode) {
                return@setOnTouchListener false
            }

            val isStackOverflowEnabled = sharedPreferences.getBoolean("smart_chips_stack_overflow", false)
            val usableHeight = getUsableHeight()
            val currentVisibleChips = getOrderedVisibleChips()
            val anchorIndex = if (isStackOverflowEnabled) calculateStackAnchor(currentVisibleChips, usableHeight) else -1
            val hasOverflow = anchorIndex != -1
            val chipIndex = currentVisibleChips.indexOfFirst { it.view == v }
            val isStackedChild = hasOverflow && chipIndex >= anchorIndex

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isStackExpanded) {
                        resetAutoCollapseTimer()
                    }
                    val targetPressScale = if (isStackedChild && !isStackExpanded) 0.90f else 0.95f
                    v.animate()
                        .scaleX(targetPressScale)
                        .scaleY(targetPressScale)
                        .setDuration(120)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val x = event.x
                    val y = event.y
                    val isInside = x >= 0 && x <= v.width && y >= 0 && y <= v.height
                    if (!isInside) {
                        val baseScale = if (isStackedChild && !isStackExpanded) {
                            when (chipIndex - anchorIndex) {
                                1 -> 0.94f
                                2 -> 0.88f
                                else -> 1.0f
                            }
                        } else 1.0f
                        v.animate()
                            .scaleX(baseScale)
                            .scaleY(baseScale)
                            .setDuration(150)
                            .setInterpolator(OvershootInterpolator(1.4f))
                            .start()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val x = event.x
                    val y = event.y
                    val isInside = x >= 0 && x <= v.width && y >= 0 && y <= v.height
                    val baseScale = if (isStackedChild && !isStackExpanded) {
                        when (chipIndex - anchorIndex) {
                            1 -> 0.94f
                            2 -> 0.88f
                            else -> 1.0f
                        }
                    } else 1.0f
                    v.animate()
                        .scaleX(baseScale)
                        .scaleY(baseScale)
                        .setDuration(220)
                        .setInterpolator(OvershootInterpolator(1.4f))
                        .start()

                    if (isInside) {
                        if (!isEditMode && isStackedChild && !isStackExpanded) {
                            expandStack()
                            return@setOnTouchListener true
                        } else if (isStackExpanded) {
                            handler.postDelayed({ collapseStack() }, 200L)
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    val baseScale = if (isStackedChild && !isStackExpanded) {
                        when (chipIndex - anchorIndex) {
                            1 -> 0.94f
                            2 -> 0.88f
                            else -> 1.0f
                        }
                    } else 1.0f
                    v.animate()
                        .scaleX(baseScale)
                        .scaleY(baseScale)
                        .setDuration(220)
                        .setInterpolator(OvershootInterpolator(1.4f))
                        .start()
                }
            }
            false
        }
    }

    private fun animateStaggeredEntrance(visibleChips: List<ChipInfo>) {
        visibleChips.forEachIndexed { index, chipInfo ->
            val view = chipInfo.view
            val targetAlpha = (view.getTag(R.id.tag_target_alpha) as? Float) ?: 1.0f
            view.alpha = 0f
            view.translationY = 24f
            view.scaleX = 0.9f
            view.scaleY = 0.9f

            view.animate()
                .alpha(targetAlpha)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 50L)
                .setDuration(350L)
                .setInterpolator(springInterpolator)
                .withEndAction {
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.translationY = 0f
                    view.alpha = targetAlpha
                }
                .start()
        }
    }

    fun onPreferencesChanged() {
        updateAllChips()
    }
}

private class ScaleAndFade : Visibility() {
    override fun onAppear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        val targetAlpha = (view.getTag(R.id.tag_target_alpha) as? Float) ?: 1.0f
        view.alpha = 0f
        view.scaleX = 0.85f
        view.scaleY = 0.85f

        val alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, targetAlpha)
        val scaleXAnim = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.85f, 1f)
        val scaleYAnim = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.85f, 1f)

        val set = AnimatorSet().apply {
            playTogether(alphaAnim, scaleXAnim, scaleYAnim)
        }
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.scaleX = 1.0f
                view.scaleY = 1.0f
                view.alpha = targetAlpha
            }
            override fun onAnimationCancel(animation: Animator) {
                view.scaleX = 1.0f
                view.scaleY = 1.0f
                view.alpha = targetAlpha
            }
        })
        return set
    }

    override fun onDisappear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        val targetAlpha = (view.getTag(R.id.tag_target_alpha) as? Float) ?: 1.0f
        val startAlpha = if (view.alpha > 0f) view.alpha else targetAlpha
        val alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, startAlpha, 0f)
        val scaleXAnim = ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, 0.85f)
        val scaleYAnim = ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, 0.85f)

        return AnimatorSet().apply {
            playTogether(alphaAnim, scaleXAnim, scaleYAnim)
        }
    }
}