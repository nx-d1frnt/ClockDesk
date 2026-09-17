package com.nxd1frnt.clockdesk2.widgets

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.Logger
import org.json.JSONArray
import org.json.JSONObject

class DesktopWidgetManager(
    private val context: Context,
    private val mainLayout: ConstraintLayout
) {
    private val TAG = "DesktopWidgetManager"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("DesktopLayout", Context.MODE_PRIVATE)
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences("WidgetPositions", Context.MODE_PRIVATE)

    private val activeInstances = mutableListOf<WidgetInstance>()
    private val instanceViewMap = mutableMapOf<String, View>()

    var onWidgetsChanged: (() -> Unit)? = null

    init {
        loadLayout()
    }

    fun getActiveInstances(): List<WidgetInstance> = activeInstances.toList()

    fun getViewForInstance(instanceId: String): View? = instanceViewMap[instanceId]

    fun getViewForInstance(instance: WidgetInstance): View? = instanceViewMap[instance.instanceId]

    fun getInstanceForView(view: View): WidgetInstance? {
        val entry = instanceViewMap.entries.firstOrNull { it.value === view } ?: return null
        return activeInstances.firstOrNull { it.instanceId == entry.key }
    }

    fun getViews(): List<View> {
        return activeInstances.mapNotNull { instanceViewMap[it.instanceId] }
    }

    fun isWidgetTypeActive(type: DesktopWidgetType): Boolean {
        return activeInstances.any { it.type == type }
    }

    private fun loadLayout() {
        activeInstances.clear()
        val jsonString = prefs.getString("active_widgets_json", null)
        if (jsonString != null) {
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val instanceId = obj.getString("instanceId")
                    val typeId = obj.getString("typeId")
                    val type = DesktopWidgetType.fromTypeId(typeId) ?: continue
                    val orderIndex = obj.optInt("orderIndex", i)
                    val isFreeMode = obj.optBoolean("isFreeMode", false)
                    val posX = obj.optDouble("posX", 0.0).toFloat()
                    val posY = obj.optDouble("posY", 0.0).toFloat()
                    val alignH = obj.optInt("alignH", 1)
                    val alignV = obj.optInt("alignV", 2)
                    val internalGravity = obj.optInt("internalGravity", 1)
                    val isVisible = obj.optBoolean("isVisible", true)

                    val instance = WidgetInstance(
                        instanceId = instanceId,
                        type = type,
                        orderIndex = orderIndex,
                        isFreeMode = isFreeMode,
                        posX = posX,
                        posY = posY,
                        alignH = alignH,
                        alignV = alignV,
                        internalGravity = internalGravity,
                        isVisible = isVisible
                    )
                    activeInstances.add(instance)
                }
            } catch (e: Exception) {
                Logger.e(TAG) { "Failed to parse active_widgets_json: ${e.message}" }
            }
        }

        if (activeInstances.isEmpty()) {
            val hasConfigured = prefs.getBoolean("has_configured_desktop", false)
            if (!hasConfigured) {
                migrateOrInitDefaults()
                prefs.edit().putBoolean("has_configured_desktop", true).apply()
            }
        }
    }

    private fun migrateOrInitDefaults() {
        Logger.d(TAG) { "Initializing default desktop widgets..." }
        activeInstances.clear()

        // 1. Time
        activeInstances.add(
            WidgetInstance(
                instanceId = "widget_time_main",
                type = DesktopWidgetType.TIME,
                orderIndex = legacyPrefs.getInt("time_text_order_index", 2),
                isFreeMode = legacyPrefs.getBoolean("time_text_individual_free_mode", false),
                posX = legacyPrefs.getFloat("time_text_x", 0f),
                posY = legacyPrefs.getFloat("time_text_y", 0f),
                alignH = legacyPrefs.getInt("time_text_align_h", 1),
                alignV = legacyPrefs.getInt("time_text_align_v", 2),
                internalGravity = legacyPrefs.getInt("time_text_internal_gravity", 1)
            )
        )

        // 2. Date
        activeInstances.add(
            WidgetInstance(
                instanceId = "widget_date_main",
                type = DesktopWidgetType.DATE,
                orderIndex = legacyPrefs.getInt("date_text_order_index", 1),
                isFreeMode = legacyPrefs.getBoolean("date_text_individual_free_mode", false),
                posX = legacyPrefs.getFloat("date_text_x", 0f),
                posY = legacyPrefs.getFloat("date_text_y", 0f),
                alignH = legacyPrefs.getInt("date_text_align_h", 1),
                alignV = legacyPrefs.getInt("date_text_align_v", 2),
                internalGravity = legacyPrefs.getInt("date_text_internal_gravity", 1)
            )
        )

        // 3. Media
        activeInstances.add(
            WidgetInstance(
                instanceId = "widget_media_main",
                type = DesktopWidgetType.MEDIA,
                orderIndex = legacyPrefs.getInt("lastfm_layout_order_index", 0),
                isFreeMode = legacyPrefs.getBoolean("lastfm_layout_individual_free_mode", false),
                posX = legacyPrefs.getFloat("lastfm_layout_x", 0f),
                posY = legacyPrefs.getFloat("lastfm_layout_y", 0f),
                alignH = legacyPrefs.getInt("lastfm_layout_align_h", 1),
                alignV = legacyPrefs.getInt("lastfm_layout_align_v", 2),
                internalGravity = legacyPrefs.getInt("lastfm_layout_internal_gravity", 1)
            )
        )

        // 4. Smart Chips
        activeInstances.add(
            WidgetInstance(
                instanceId = "widget_chips_main",
                type = DesktopWidgetType.SMART_CHIPS,
                orderIndex = 3,
                isFreeMode = false
            )
        )

        saveLayout()
    }

    fun saveLayout() {
        val array = JSONArray()
        for (instance in activeInstances) {
            val obj = JSONObject().apply {
                put("instanceId", instance.instanceId)
                put("typeId", instance.type.typeId)
                put("orderIndex", instance.orderIndex)
                put("isFreeMode", instance.isFreeMode)
                put("posX", instance.posX.toDouble())
                put("posY", instance.posY.toDouble())
                put("alignH", instance.alignH)
                put("alignV", instance.alignV)
                put("internalGravity", instance.internalGravity)
                put("isVisible", instance.isVisible)
            }
            array.put(obj)
        }
        prefs.edit()
            .putString("active_widgets_json", array.toString())
            .putBoolean("has_configured_desktop", true)
            .apply()
        Logger.d(TAG) { "Desktop layout saved with ${activeInstances.size} widgets" }
    }

    fun bindExistingViews(
        timeView: View?,
        dateView: View?,
        mediaView: View?,
        chipsView: View?,
        weatherView: View?
    ) {
        instanceViewMap.clear()
        val allTypesWithViews = listOf(
            DesktopWidgetType.TIME to timeView,
            DesktopWidgetType.DATE to dateView,
            DesktopWidgetType.MEDIA to mediaView,
            DesktopWidgetType.SMART_CHIPS to chipsView,
            DesktopWidgetType.WEATHER to weatherView
        )

        for ((type, view) in allTypesWithViews) {
            if (view == null) continue
            val instance = activeInstances.firstOrNull { it.type == type }
            if (instance != null) {
                instanceViewMap[instance.instanceId] = view
                view.visibility = if (instance.isVisible) View.VISIBLE else View.GONE
            } else {
                view.visibility = View.GONE
            }
        }
    }

    fun addWidget(type: DesktopWidgetType): WidgetInstance? {
        val definition = DesktopWidgetRegistry.get(type) ?: return null
        if (!definition.allowMultipleInstances && isWidgetTypeActive(type)) {
            Logger.w(TAG) { "Widget type ${type.name} does not allow multiple instances and already exists" }
            return null
        }

        val instanceId = "widget_${type.typeId}_${System.currentTimeMillis()}"
        val maxOrder = activeInstances.maxOfOrNull { it.orderIndex } ?: -1
        val newInstance = WidgetInstance(
            instanceId = instanceId,
            type = type,
            orderIndex = maxOrder + 1,
            isFreeMode = false,
            alignH = 1,
            alignV = 2,
            internalGravity = 1,
            isVisible = true
        )

        activeInstances.add(newInstance)
        saveLayout()
        onWidgetsChanged?.invoke()
        return newInstance
    }

    fun removeWidget(instanceId: String): Boolean {
        val instance = activeInstances.firstOrNull { it.instanceId == instanceId } ?: return false
        return removeWidget(instance)
    }

    fun removeWidget(instance: WidgetInstance): Boolean {
        val removed = activeInstances.remove(instance)
        if (removed) {
            val view = instanceViewMap.remove(instance.instanceId)
            view?.let {
                it.visibility = View.GONE
            }
            saveLayout()
            onWidgetsChanged?.invoke()
        }
        return removed
    }

    fun resetToDefaults() {
        migrateOrInitDefaults()
        onWidgetsChanged?.invoke()
    }
}
