package com.nxd1frnt.clockdesk2.smartchips.ui

import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem

class WeatherAlertSettingsFragment : BaseM3SettingsFragment() {

    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.weather_alert_types_category)) {
            switch(
                key = "weather_alert_enable_storms",
                title = getString(R.string.weather_alert_enable_storms_title),
                summary = getString(R.string.weather_alert_enable_storms_summary),
                iconRes = R.drawable.ic_thunderstorm,
                defaultValue = true
            )

            switch(
                key = "weather_alert_enable_wind",
                title = getString(R.string.weather_alert_enable_wind_title),
                summary = getString(R.string.weather_alert_enable_wind_summary),
                defaultValue = true
            )

            switch(
                key = "weather_alert_enable_worsening",
                title = getString(R.string.weather_alert_enable_worsening_title),
                summary = getString(R.string.weather_alert_enable_worsening_summary),
                defaultValue = true
            )

            switch(
                key = "weather_alert_enable_uv",
                title = getString(R.string.weather_alert_enable_uv_title),
                summary = getString(R.string.weather_alert_enable_uv_summary),
                defaultValue = true
            )
        }

        category(getString(R.string.weather_alert_thresholds_category)) {
            val windUnit = prefs.getString("wind_speed_unit", "kmh") ?: "kmh"
            val windUnitLabel = when (windUnit) {
                "ms" -> "m/s"
                "mph" -> "mph"
                else -> "km/h"
            }

            slider(
                key = "weather_alert_wind_threshold",
                title = getString(R.string.weather_alert_wind_threshold_title),
                summary = getString(R.string.weather_alert_wind_threshold_summary),
                min = 15f,
                max = 80f,
                step = 1f,
                defaultValue = 35f,
                valueFormatter = { "${it.toInt()} $windUnitLabel" },
                isEnabled = { prefs.getBoolean("weather_alert_enable_wind", true) }
            )

            slider(
                key = "weather_alert_uv_threshold",
                title = getString(R.string.weather_alert_uv_threshold_title),
                summary = getString(R.string.weather_alert_uv_threshold_summary),
                min = 3f,
                max = 11f,
                step = 1f,
                defaultValue = 6f,
                valueFormatter = { "${it.toInt()}" },
                isEnabled = { prefs.getBoolean("weather_alert_enable_uv", true) }
            )

            slider(
                key = "weather_alert_forecast_hours",
                title = getString(R.string.weather_alert_forecast_hours_title),
                summary = getString(R.string.weather_alert_forecast_hours_summary),
                min = 1f,
                max = 8f,
                step = 1f,
                defaultValue = 3f,
                valueFormatter = { "${it.toInt()} h" },
                isEnabled = { prefs.getBoolean("weather_alert_enable_worsening", true) }
            )
        }
    }
}
