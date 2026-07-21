package com.nxd1frnt.clockdesk2.smartchips.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter
import com.nxd1frnt.clockdesk2.weathergetter.getUvIndexDescription
import com.nxd1frnt.clockdesk2.weathergetter.getWeatherConditionDescription
import com.nxd1frnt.clockdesk2.weathergetter.getWeatherIconRes
import java.util.Calendar
import java.util.Locale

import com.google.android.material.color.DynamicColors

object WeatherDialog {

    fun show(context: Context) {
        var activityContext: Context? = context
        while (activityContext is ContextWrapper && activityContext !is Activity) {
            activityContext = activityContext.baseContext
        }
        val targetContext = activityContext ?: context
        val dialogContext = DynamicColors.wrapContextIfAvailable(targetContext)

        val view = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_weather, null, false)

        val locationText = view.findViewById<TextView>(R.id.dialog_location_text)
        val modeBadge = view.findViewById<TextView>(R.id.dialog_location_mode_badge)
        val weatherIcon = view.findViewById<ImageView>(R.id.dialog_weather_icon)
        val tempText = view.findViewById<TextView>(R.id.dialog_temp_text)
        val conditionText = view.findViewById<TextView>(R.id.dialog_condition_text)

        val alertBanner = view.findViewById<LinearLayout>(R.id.dialog_alert_banner)
        val alertIcon = view.findViewById<ImageView>(R.id.dialog_alert_icon)
        val alertText = view.findViewById<TextView>(R.id.dialog_alert_text)

        val windValue = view.findViewById<TextView>(R.id.dialog_wind_value)
        val uvValue = view.findViewById<TextView>(R.id.dialog_uv_value)
        val precipValue = view.findViewById<TextView>(R.id.dialog_precip_value)
        val cloudsValue = view.findViewById<TextView>(R.id.dialog_clouds_value)
        val visibilityValue = view.findViewById<TextView>(R.id.dialog_visibility_value)
        val cycleValue = view.findViewById<TextView>(R.id.dialog_cycle_value)
        val cycleIcon = view.findViewById<ImageView>(R.id.dialog_cycle_icon)

        val forecastTitle = view.findViewById<TextView>(R.id.dialog_forecast_title)
        val forecastContainer = view.findViewById<LinearLayout>(R.id.dialog_forecast_container)

        // Read location prefs
        val prefs = targetContext.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val locationMode = prefs.getString("location_mode", "auto") ?: "auto"
        when (locationMode) {
            "city" -> {
                val cityName = prefs.getString("resolved_city_display_name", null)
                    ?: prefs.getString("location_city_name", targetContext.getString(R.string.weather_auto_current))
                locationText.text = cityName
                modeBadge.text = "City"
            }
            "coords" -> {
                val lat = prefs.getString("latitude", "0")
                val lon = prefs.getString("longitude", "0")
                locationText.text = "$lat, $lon"
                modeBadge.text = "Coords"
            }
            else -> {
                locationText.text = targetContext.getString(R.string.weather_auto_current)
                modeBadge.text = "GPS Auto"
            }
        }

        // Read weather cache
        val temp = WeatherGetter.cachedTemperature
        val code = WeatherGetter.cachedWeatherCode
        val isDay = WeatherGetter.cachedIsDay ?: true
        val wind = WeatherGetter.cachedWindSpeed
        val uv = WeatherGetter.cachedUvIndex
        val precip = WeatherGetter.cachedPrecipitation
        val clouds = WeatherGetter.cachedCloudCover
        val visibility = WeatherGetter.cachedVisibility
        val hourlyCodes = WeatherGetter.cachedHourlyCodes
        val hourlyTemps = WeatherGetter.cachedHourlyTemps

        val tempUnit = prefs.getString("temperature_unit", "celsius") ?: "celsius"
        val windUnit = prefs.getString("wind_speed_unit", "kmh") ?: "kmh"
        val precipUnit = prefs.getString("precipitation_unit", "mm") ?: "mm"

        val tempSuffix = if (tempUnit == "fahrenheit") "°F" else "°C"
        val windSuffix = when (windUnit) {
            "mph" -> "mph"
            "ms" -> "m/s"
            else -> "km/h"
        }
        val precipSuffix = if (precipUnit == "inch") "in" else "mm"

        if (temp != null && code != null) {
            weatherIcon.setImageResource(getWeatherIconRes(code, isDay))
            tempText.text = "${temp.toInt()}$tempSuffix"
            conditionText.text = getWeatherConditionDescription(targetContext, code)

            windValue.text = if (wind != null) String.format(Locale.US, "%.1f %s", wind, windSuffix) else "--"
            uvValue.text = if (uv != null) String.format(Locale.US, "%.1f (%s)", uv, getUvIndexDescription(uv)) else "--"
            precipValue.text = if (precip != null) String.format(Locale.US, "%.1f %s", precip, precipSuffix) else "0.0 $precipSuffix"
            cloudsValue.text = if (clouds != null) "$clouds%" else "--"
            visibilityValue.text = if (visibility != null) {
                if (visibility >= 1000.0) String.format(Locale.US, "%.1f km", visibility / 1000.0)
                else "${visibility.toInt()} m"
            } else "--"
            cycleValue.text = if (isDay) "Day" else "Night"
            cycleIcon.setImageResource(if (isDay) R.drawable.ic_clear_day else R.drawable.ic_clear_night)

            // Active Alert check
            var alertMessage: String? = null
            var alertIconRes = 0
            val windKmh = when (windUnit) {
                "mph" -> (wind ?: 0.0) * 1.60934
                "ms" -> (wind ?: 0.0) * 3.6
                else -> wind ?: 0.0
            }
            if (code in listOf(95, 96, 99)) {
                alertMessage = targetContext.getString(R.string.weather_alert_storm_active)
                alertIconRes = R.drawable.ic_weather_lightning_rainy
            } else if (windKmh > 35) {
                alertMessage = targetContext.getString(R.string.weather_alert_high_winds)
                alertIconRes = R.drawable.ic_weather_windy
            } else if (uv != null && uv >= 6.0) {
                alertMessage = targetContext.getString(R.string.weather_alert_high_uv, String.format(Locale.US, "%.1f", uv))
                alertIconRes = R.drawable.ic_clear_day
            }

            if (alertMessage != null) {
                alertBanner.visibility = View.VISIBLE
                alertText.text = alertMessage
                if (alertIconRes != 0) alertIcon.setImageResource(alertIconRes)
            } else {
                alertBanner.visibility = View.GONE
            }

            // Hourly Forecast list
            if (hourlyCodes.isNotEmpty()) {
                forecastTitle.visibility = View.VISIBLE
                forecastContainer.removeAllViews()
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val itemsToShow = minOf(12, hourlyCodes.size - currentHour - 1)

                for (offset in 1..itemsToShow) {
                    val idx = currentHour + offset
                    if (idx in hourlyCodes.indices) {
                        val hourlyCode = hourlyCodes[idx]
                        val hourlyTemp = if (idx in hourlyTemps.indices) hourlyTemps[idx] else null

                        val itemView = LayoutInflater.from(targetContext).inflate(R.layout.item_weather_forecast, forecastContainer, false)
                        val itemTime = itemView.findViewById<TextView>(R.id.forecast_item_time)
                        val itemIcon = itemView.findViewById<ImageView>(R.id.forecast_item_icon)
                        val itemTemp = itemView.findViewById<TextView>(R.id.forecast_item_temp)
                        val itemCond = itemView.findViewById<TextView>(R.id.forecast_item_condition)

                        itemTime.text = "+${offset}h"
                        itemIcon.setImageResource(getWeatherIconRes(hourlyCode, isDay = true))
                        itemTemp.text = if (hourlyTemp != null) "${hourlyTemp.toInt()}$tempSuffix" else "--"
                        itemCond.text = getWeatherConditionDescription(targetContext, hourlyCode)
                        forecastContainer.addView(itemView)
                    }
                }
            } else {
                forecastTitle.visibility = View.GONE
            }
        } else {
            weatherIcon.setImageResource(R.drawable.ic_weather_unknown)
            tempText.text = "--"
            conditionText.text = targetContext.getString(R.string.weather_auto_no_data)
            windValue.text = "--"
            uvValue.text = "--"
            precipValue.text = "--"
            cloudsValue.text = "--"
            visibilityValue.text = "--"
            cycleValue.text = "--"
            forecastTitle.visibility = View.GONE
        }

        MaterialAlertDialogBuilder(dialogContext)
            .setTitle(targetContext.getString(R.string.weather_dialog_title))
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(targetContext.getString(R.string.weather_alert_settings_title)) { _, _ ->
                try {
                    val intent = Intent(targetContext, WeatherAlertSettingsActivity::class.java)
                    targetContext.startActivity(intent)
                } catch (e: Exception) {}
            }
            .show()
    }
}
