package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip
import com.nxd1frnt.clockdesk2.smartchips.setTextWithFade
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter
import java.util.Calendar

import com.nxd1frnt.clockdesk2.smartchips.ui.WeatherDialog

class WeatherAlertPlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "show_weather_alert_chip"

    private var stateChangeListener: (() -> Unit)? = null
    private val weatherListener = {
        stateChangeListener?.invoke()
        Unit
    }

    override fun setOnStateChangeListener(listener: () -> Unit) {
        this.stateChangeListener = listener
    }

    override fun startListening() {
        WeatherGetter.registerListener(weatherListener)
    }

    override fun stopListening() {
        WeatherGetter.unregisterListener(weatherListener)
    }

    override fun createView(context: Context): View {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.smart_chip_layout, null, false)
        view.setOnClickListener {
            WeatherDialog.show(context)
        }
        return view
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        val currentCode = WeatherGetter.cachedWeatherCode
        val windSpeed = WeatherGetter.cachedWindSpeed
        val hourlyCodes = WeatherGetter.cachedHourlyCodes
        val uvIndex = WeatherGetter.cachedUvIndex

        val enableStorms = sharedPreferences.getBoolean("weather_alert_enable_storms", true)
        val enableWind = sharedPreferences.getBoolean("weather_alert_enable_wind", true)
        val enableWorsening = sharedPreferences.getBoolean("weather_alert_enable_worsening", true)
        val enableUv = sharedPreferences.getBoolean("weather_alert_enable_uv", true)

        val windThreshold = sharedPreferences.getInt("weather_alert_wind_threshold", 35)
        val uvThreshold = sharedPreferences.getInt("weather_alert_uv_threshold", 6)
        val forecastHours = sharedPreferences.getInt("weather_alert_forecast_hours", 3)

        val targetIcon: Int
        val targetText: String

        // 1. Current Storm Alert
        if (enableStorms && currentCode != null && (currentCode == 95 || currentCode == 96 || currentCode == 99)) {
            targetIcon = R.drawable.ic_weather_lightning_rainy
            targetText = context.getString(R.string.weather_alert_storm_active)
        } else if (enableUv && uvIndex != null && uvIndex >= uvThreshold) {
            // 2. High UV Alert
            targetIcon = R.drawable.ic_clear_day
            targetText = context.getString(R.string.weather_alert_high_uv, String.format(java.util.Locale.US, "%.1f", uvIndex))
        } else if (enableWind && windSpeed != null && windSpeed > windThreshold) {
            // 3. High Winds Alert
            targetIcon = R.drawable.ic_weather_windy
            targetText = context.getString(R.string.weather_alert_high_winds)
        } else if (enableWorsening && currentCode != null && hourlyCodes.isNotEmpty()) {
            // 4. Upcoming Worsening Weather
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val currentSeverity = getSeverity(currentCode)
            var foundText: String? = null
            var foundIcon = 0

            for (offset in 1..forecastHours) {
                val idx = currentHour + offset
                if (idx < hourlyCodes.size) {
                    val futureCode = hourlyCodes[idx]
                    val futureSeverity = getSeverity(futureCode)
                    if (futureSeverity > currentSeverity && futureSeverity >= 4) {
                        foundText = when (futureSeverity) {
                            4 -> context.getString(R.string.weather_alert_rain_in_format, offset)
                            5 -> context.getString(R.string.weather_alert_snow_in_format, offset)
                            6 -> context.getString(R.string.weather_alert_storm_in_format, offset)
                            else -> null
                        }
                        foundIcon = when (futureSeverity) {
                            4 -> R.drawable.ic_weather_rainy
                            5 -> R.drawable.ic_weather_snowy
                            6 -> R.drawable.ic_weather_lightning_rainy
                            else -> 0
                        }
                        break
                    }
                }
            }
            if (foundText != null && foundIcon != 0) {
                targetIcon = foundIcon
                targetText = foundText
            } else {
                return false
            }
        } else {
            return false
        }

        if (iconView != null && iconView.tag != targetIcon) {
            iconView.setImageResource(targetIcon)
            iconView.tag = targetIcon
        }
        if (textView != null) {
            textView.setTextWithFade(targetText)
        }
        return true
    }

    private fun getSeverity(code: Int): Int {
        return when (code) {
            0, 1, 2, 3 -> 1 // Clear / Cloudy
            45, 48 -> 2    // Fog
            51, 53, 55, 56, 57 -> 3 // Drizzle
            61, 63, 65, 66, 67, 80, 81, 82 -> 4 // Rain
            71, 73, 75, 77, 85, 86 -> 5 // Snow
            95, 96, 99 -> 6 // Storm
            else -> 0
        }
    }
}
