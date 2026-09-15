package com.nxd1frnt.clockdesk2.widgets

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

enum class DesktopWidgetType(val typeId: String) {
    TIME("widget_time"),
    DATE("widget_date"),
    MEDIA("widget_media"),
    SMART_CHIPS("widget_smart_chips"),
    WEATHER("widget_weather");

    companion object {
        fun fromTypeId(typeId: String): DesktopWidgetType? {
            return values().firstOrNull { it.typeId == typeId }
        }
    }
}

enum class WidgetFeature {
    FONT_FAMILY,
    FONT_SIZE,
    FONT_VARIATION,
    FONT_COLOR,
    FONT_ALPHA,
    TEXT_GRAVITY,
    HORIZONTAL_ALIGNMENT,
    VERTICAL_ALIGNMENT,
    FREE_MOVEMENT,
    TIME_FORMAT,
    DATE_FORMAT,
    CLOCK_STYLE,
    NIGHT_SHIFT,
    MAX_WIDTH,
    MEDIA_ICON,
    BACKGROUND_COLOR
}

data class WidgetInstance(
    val instanceId: String,
    val type: DesktopWidgetType,
    var orderIndex: Int = 0,
    var isFreeMode: Boolean = false,
    var posX: Float = 0f,
    var posY: Float = 0f,
    var alignH: Int = 1, // ALIGN_H_CENTER = 1
    var alignV: Int = 2, // ALIGN_V_BOTTOM = 2
    var internalGravity: Int = 1, // GRAVITY_CENTER = 1
    var isVisible: Boolean = true,
    val customSettings: MutableMap<String, String> = mutableMapOf()
)

interface DesktopWidgetDefinition {
    val type: DesktopWidgetType
    @get:StringRes
    val titleRes: Int
    @get:StringRes
    val descriptionRes: Int
    @get:DrawableRes
    val iconRes: Int
    val allowMultipleInstances: Boolean
    val supportedFeatures: Set<WidgetFeature>

    fun createView(context: Context, parent: ViewGroup): View
}
