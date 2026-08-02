package com.nxd1frnt.clockdesk2.smartchips

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
import android.view.View
import android.view.ViewGroup
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
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import androidx.transition.TransitionValues
import androidx.transition.Visibility
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
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
    private data class ChipInfo(
        val id: String,
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

//    private val periodicUpdateRunnable = object : Runnable {
//        override fun run() {
//            updateAllChips()
//            handler.postDelayed(this, updateInterval)
//        }
//    }

    private val internalPlugins: List<ISmartChip> = listOf(
        BatteryAlertPlugin(context),
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
            val plugin = externalPlugins.find { it.packageName == packageName } ?: return@Runnable
            val chipInfo = allChips.find { it.id == plugin.preferenceKey } ?: return@Runnable

            val count = (pluginTimeoutCounts[packageName] ?: 0) + 1
            pluginTimeoutCounts[packageName] = count
            if (count >= 3) {
                pluginTimers[packageName]?.let { handler.removeCallbacks(it) }
                pluginTimers.remove(packageName)
                Logger.w("SmartChipManager") { "Plugin $packageName timed out 3 times consecutively. Stopped timer." }
            }

            if (chipInfo.isVisible) {
                chipInfo.isVisible = false
                chipInfo.clickActivityClassName = null
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

    private val dataUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != ChipPluginContract.ACTION_UPDATE_DATA) return
            val packageName = intent.getStringExtra(ChipPluginContract.KEY_PLUGIN_PACKAGE) ?: return

            val plugin = externalPlugins.find { it.packageName == packageName } ?: return

            val chipInfo = allChips.find { it.id == plugin.preferenceKey } ?: return

            clearPluginTimeout(packageName)

            val isVisible = intent.getBooleanExtra(ChipPluginContract.KEY_CHIP_VISIBLE, true)
            val updateIntervalSec = intent.getIntExtra("update_interval_seconds", -1)
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
            managePluginTimer(packageName, updateIntervalSec, isVisible)
            sortAndRedrawChips(contentChanged)
        }
    }

    init {
        internalPlugins.forEach { plugin ->
            val view = plugin.createView(context).apply {
                visibility = View.GONE
                tag = plugin.preferenceKey
            }
            allChips.add(ChipInfo(plugin.preferenceKey, view))
            plugin.setOnStateChangeListener {
                requestInitialState()
            }
        }
        discoverExternalPlugins()

        // We don't register receivers anymore in init
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

        // Запрашиваем данные у внешних плагинов
        externalPlugins.forEach { plugin ->
            val isEnabled = sharedPreferences.getBoolean(plugin.preferenceKey, false)
            if (isEnabled) {
                if (!isReceiverAvailable(plugin.packageName, plugin.receiverClassName)) {
                    val chipInfo = allChips.find { it.id == plugin.preferenceKey }
                    if (chipInfo?.isVisible == true) {
                        chipInfo.isVisible = false
                        isContentChanged = true
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
                val chipInfo = allChips.find { it.id == plugin.preferenceKey }
                if (chipInfo?.isVisible == true) {
                    chipInfo.isVisible = false
                    isContentChanged = true
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
                        var prefKey: String? = null
                        var dispName: String? = null

                        while (parser.next() != XmlPullParser.END_DOCUMENT) {
                            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "smart-chip-plugin") {
                                prefKey = parser.getAttributeValue(null, "preferenceKey")
                                dispName = parser.getAttributeValue(null, "displayName")
                            }
                        }

                        if (prefKey != null && dispName != null) {
                            foundData.add(DiscoveredPlugin(packageName, className, prefKey, dispName))
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
                    Logger.d("SmartChipManager"){"Plugin ${data.pkg} loaded"}

                    view.setOnClickListener {
                        if (isEditMode) {
                            onEditClickListener?.invoke(chipContainer)
                            return@setOnClickListener
                        }
                        val chipInfo = allChips.find { it.view == view } ?: return@setOnClickListener
                        chipInfo.clickActivityClassName?.let { cls ->
                            try {
                                val pluginPkg = externalPlugins.find { it.preferenceKey == chipInfo.id }?.packageName ?: return@let
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
                    allChips.add(ChipInfo(data.key, view))
                }
                this.externalPlugins = foundPlugins
                requestInitialState()
            }
        }.start()
    }

    private fun animateTextChange(textView: TextView, newText: String) {
        if (textView.text.toString() == newText) return
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

    fun updateAllChips() {
        var isContentChanged = false

        val container = chipContainer as? ViewGroup
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

        // Internal chips
        internalPlugins.forEach { plugin ->
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

        externalPlugins.forEach { plugin ->
            val chipInfo = allChips.find { it.id == plugin.preferenceKey } ?: return@forEach
            val isEnabled = sharedPreferences.getBoolean(plugin.preferenceKey, false)
            
            if (!isEnabled && chipInfo.isVisible) {
                chipInfo.isVisible = false
                isContentChanged = true
            }
            
            if (isEnabled) {
                if (!isReceiverAvailable(plugin.packageName, plugin.receiverClassName)) {
                    if (chipInfo.isVisible) {
                        chipInfo.isVisible = false
                        isContentChanged = true
                    }
                    return@forEach
                }

                val requestIntent = Intent().apply {
                    action = ChipPluginContract.ACTION_REQUEST_DATA
                    component = ComponentName(plugin.packageName, plugin.receiverClassName)
                }
                context.sendBroadcast(requestIntent)
                startTimeoutCheck(plugin.packageName)
            }
        }

        sortAndRedrawChips(isContentChanged)
    }

    private fun sortAndRedrawChips(contentChanged: Boolean = false) {
        // Читаем порядок, заданный пользователем в настройках
        val orderString = sharedPreferences.getString("smart_chip_order", "system_bg_progress,show_battery_alert,show_updates,show_alarm_chip,show_weather_chip,show_weather_alert_chip") ?: ""
        val orderList = orderString.split(",").map { it.trim() }

        // Фильтруем видимые чипы и сортируем их по индексу в orderList
        val visibleChips = allChips
            .filter { it.isVisible }
            .sortedBy { chipInfo ->
                val index = orderList.indexOf(chipInfo.id)
                if (index != -1) index else Int.MAX_VALUE
            }

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
                if (!textView.isSelected) textView.isSelected = true
            }
            
            if (contentChanged) {
                TransitionManager.beginDelayedTransition(container, transition)
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
        Logger.d("SmartChipManager"){"Chips updated"}

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
        updateChipsClickability()

        if (isFirstLoad && visibleChips.isNotEmpty()) {
            isFirstLoad = false
            animateStaggeredEntrance(visibleChips)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupChipTouchFeedback(view: View) {
        if (view.getTag(R.id.tag_touch_listener_set) == true) return
        view.setTag(R.id.tag_touch_listener_set, true)

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(120)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
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
            val targetAlpha = if (view.alpha > 0f) view.alpha else 1f
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
        val targetAlpha = if (view.alpha > 0f) view.alpha else 1f
        view.alpha = 0f
        view.scaleX = 0.85f
        view.scaleY = 0.85f

        val alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, targetAlpha)
        val scaleXAnim = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.85f, 1f)
        val scaleYAnim = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.85f, 1f)

        return AnimatorSet().apply {
            playTogether(alphaAnim, scaleXAnim, scaleYAnim)
        }
    }

    override fun onDisappear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        val alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f)
        val scaleXAnim = ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, 0.85f)
        val scaleYAnim = ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, 0.85f)

        return AnimatorSet().apply {
            playTogether(alphaAnim, scaleXAnim, scaleYAnim)
        }
    }
}