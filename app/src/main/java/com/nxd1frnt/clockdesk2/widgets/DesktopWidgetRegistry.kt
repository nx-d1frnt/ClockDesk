package com.nxd1frnt.clockdesk2.widgets

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.nxd1frnt.clockdesk2.R

object DesktopWidgetRegistry {

    private val registry = mutableMapOf<DesktopWidgetType, DesktopWidgetDefinition>()

    init {
        register(ClockWidgetDefinition)
        register(DateWidgetDefinition)
        register(MediaWidgetDefinition)
        register(SmartChipsWidgetDefinition)
        register(WeatherWidgetDefinition)
    }

    fun register(definition: DesktopWidgetDefinition) {
        registry[definition.type] = definition
    }

    fun get(type: DesktopWidgetType): DesktopWidgetDefinition? {
        return registry[type]
    }

    fun getAll(): List<DesktopWidgetDefinition> {
        return registry.values.toList()
    }
}

object ClockWidgetDefinition : DesktopWidgetDefinition {
    override val type = DesktopWidgetType.TIME
    override val titleRes = R.string.widget_clock_title
    override val descriptionRes = R.string.widget_clock_desc
    override val iconRes = R.drawable.ic_clock
    override val allowMultipleInstances = false
    override val supportedFeatures = setOf(
        WidgetFeature.FONT_FAMILY,
        WidgetFeature.FONT_SIZE,
        WidgetFeature.FONT_VARIATION,
        WidgetFeature.FONT_COLOR,
        WidgetFeature.FONT_ALPHA,
        WidgetFeature.TEXT_GRAVITY,
        WidgetFeature.HORIZONTAL_ALIGNMENT,
        WidgetFeature.VERTICAL_ALIGNMENT,
        WidgetFeature.FREE_MOVEMENT,
        WidgetFeature.TIME_FORMAT,
        WidgetFeature.CLOCK_STYLE,
        WidgetFeature.NIGHT_SHIFT
    )

    override fun createView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.widget_clock_template, parent, false)
    }
}

object DateWidgetDefinition : DesktopWidgetDefinition {
    override val type = DesktopWidgetType.DATE
    override val titleRes = R.string.widget_date_title
    override val descriptionRes = R.string.widget_date_desc
    override val iconRes = R.drawable.ic_calendar
    override val allowMultipleInstances = false
    override val supportedFeatures = setOf(
        WidgetFeature.FONT_FAMILY,
        WidgetFeature.FONT_SIZE,
        WidgetFeature.FONT_VARIATION,
        WidgetFeature.FONT_COLOR,
        WidgetFeature.FONT_ALPHA,
        WidgetFeature.TEXT_GRAVITY,
        WidgetFeature.HORIZONTAL_ALIGNMENT,
        WidgetFeature.VERTICAL_ALIGNMENT,
        WidgetFeature.FREE_MOVEMENT,
        WidgetFeature.DATE_FORMAT,
        WidgetFeature.NIGHT_SHIFT
    )

    override fun createView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.widget_date_template, parent, false)
    }
}

object MediaWidgetDefinition : DesktopWidgetDefinition {
    override val type = DesktopWidgetType.MEDIA
    override val titleRes = R.string.widget_media_title
    override val descriptionRes = R.string.widget_media_desc
    override val iconRes = R.drawable.music_note
    override val allowMultipleInstances = false
    override val supportedFeatures = setOf(
        WidgetFeature.FONT_FAMILY,
        WidgetFeature.FONT_SIZE,
        WidgetFeature.FONT_VARIATION,
        WidgetFeature.FONT_COLOR,
        WidgetFeature.FONT_ALPHA,
        WidgetFeature.TEXT_GRAVITY,
        WidgetFeature.HORIZONTAL_ALIGNMENT,
        WidgetFeature.VERTICAL_ALIGNMENT,
        WidgetFeature.FREE_MOVEMENT,
        WidgetFeature.MAX_WIDTH,
        WidgetFeature.MEDIA_ICON,
        WidgetFeature.NIGHT_SHIFT
    )

    override fun createView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.widget_media_template, parent, false)
    }
}

object SmartChipsWidgetDefinition : DesktopWidgetDefinition {
    override val type = DesktopWidgetType.SMART_CHIPS
    override val titleRes = R.string.widget_smart_chips_title
    override val descriptionRes = R.string.widget_smart_chips_desc
    override val iconRes = R.drawable.ic_auto_awesome
    override val allowMultipleInstances = false
    override val supportedFeatures = setOf(
        WidgetFeature.FONT_FAMILY,
        WidgetFeature.FONT_SIZE,
        WidgetFeature.FONT_VARIATION,
        WidgetFeature.FONT_COLOR,
        WidgetFeature.FONT_ALPHA,
        WidgetFeature.BACKGROUND_COLOR,
        WidgetFeature.NIGHT_SHIFT,
        WidgetFeature.CHIP_STACK_OVERFLOW
    )

    override fun createView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.widget_smart_chips_template, parent, false)
    }
}

object WeatherWidgetDefinition : DesktopWidgetDefinition {
    override val type = DesktopWidgetType.WEATHER
    override val titleRes = R.string.widget_weather_title
    override val descriptionRes = R.string.widget_weather_desc
    override val iconRes = R.drawable.ic_clear_day
    override val allowMultipleInstances = false
    override val supportedFeatures = setOf(
        WidgetFeature.FONT_FAMILY,
        WidgetFeature.FONT_SIZE,
        WidgetFeature.FONT_VARIATION,
        WidgetFeature.FONT_COLOR,
        WidgetFeature.FONT_ALPHA,
        WidgetFeature.TEXT_GRAVITY,
        WidgetFeature.HORIZONTAL_ALIGNMENT,
        WidgetFeature.VERTICAL_ALIGNMENT,
        WidgetFeature.FREE_MOVEMENT,
        WidgetFeature.NIGHT_SHIFT
    )

    override fun createView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.widget_weather_template, parent, false)
    }
}
