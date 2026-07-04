package com.nxd1frnt.clockdesk2.ui.dashboard

import android.view.View

data class DashboardTile(
    val id: String,
    val type: String, // INFO, TOGGLE, SLIDER, BUTTON, MEDIA_PLAYER, WEATHER_FORECAST, NATIVE_APK
    val pluginId: String,
    var title: String? = null,
    var icon: String? = null,
    var iconUrl: String? = null,
    var localIcon: String? = null,
    var span: Int = 2,
    var state: Boolean = false,
    var value: Float = 0f,
    var min: Float = 0f,
    var max: Float = 100f,
    var info: String? = null,
    var action: String? = null,
    var deviceCategory: String? = null,
    var deviceAction: String? = null,
    var nativeView: View? = null,
    var extraData: Map<String, Any?>? = null
) {
    fun updateWith(updates: Map<String, Any?>) {
        updates["title"]?.let { title = it as? String }
        updates["icon"]?.let { icon = it as? String }
        updates["iconUrl"]?.let { iconUrl = it as? String }
        updates["localIcon"]?.let { localIcon = it as? String }
        updates["span"]?.let { span = (it as? Number)?.toInt() ?: 2 }
        updates["state"]?.let { state = it as? Boolean ?: false }
        updates["value"]?.let { value = (it as? Number)?.toFloat() ?: 0f }
        updates["min"]?.let { min = (it as? Number)?.toFloat() ?: 0f }
        updates["max"]?.let { max = (it as? Number)?.toFloat() ?: 100f }
        updates["info"]?.let { info = it as? String }
        updates["action"]?.let { action = it as? String }
        updates["deviceCategory"]?.let { deviceCategory = it as? String }
        updates["deviceAction"]?.let { deviceAction = it as? String }
        
        // Store any other fields in extraData
        val currentExtra = extraData?.toMutableMap() ?: mutableMapOf()
        updates.forEach { (key, value) ->
            if (key !in setOf("id", "type", "pluginId", "title", "icon", "iconUrl", "localIcon", "span", "state", "value", "min", "max", "info", "action", "deviceCategory", "deviceAction")) {
                currentExtra[key] = value
            }
        }
        extraData = currentExtra
    }
}
