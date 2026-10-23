package com.nxd1frnt.clockdesk2.desks.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.UiMonetPalette
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter
import com.nxd1frnt.clockdesk2.weathergetter.getUvIndexDescription
import com.nxd1frnt.clockdesk2.weathergetter.getWeatherConditionDescription
import com.nxd1frnt.clockdesk2.weathergetter.getWeatherIconRes
import java.util.Calendar
import java.util.Locale

class WeatherDeskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val txtLocation: TextView
    private val txtLocationMode: TextView
    private val btnRefresh: MaterialButton
    private val imgHeroIcon: ImageView
    private val txtHeroTemp: TextView
    private val txtHeroCondition: TextView

    private val cardAlert: MaterialCardView
    private val imgAlertIcon: ImageView
    private val txtAlert: TextView

    private val txtHourlyTitle: TextView
    private val layoutHourly: LinearLayout

    private val txtWind: TextView
    private val txtUv: TextView
    private val txtPrecip: TextView
    private val txtCycle: TextView

    private val weatherListener: () -> Unit = {
        post { updateWeatherData() }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.desk_weather_layout, this, true)

        txtLocation = findViewById(R.id.weather_location_text)
        txtLocationMode = findViewById(R.id.weather_location_mode)
        btnRefresh = findViewById(R.id.btn_refresh_weather)
        imgHeroIcon = findViewById(R.id.weather_hero_icon)
        txtHeroTemp = findViewById(R.id.weather_hero_temp)
        txtHeroCondition = findViewById(R.id.weather_hero_condition)

        cardAlert = findViewById(R.id.weather_alert_banner)
        imgAlertIcon = findViewById(R.id.weather_alert_icon)
        txtAlert = findViewById(R.id.weather_alert_text)

        txtHourlyTitle = findViewById(R.id.txt_hourly_title)
        layoutHourly = findViewById(R.id.weather_hourly_container)

        txtWind = findViewById(R.id.txt_wind_value)
        txtUv = findViewById(R.id.txt_uv_value)
        txtPrecip = findViewById(R.id.txt_precip_value)
        txtCycle = findViewById(R.id.txt_cycle_value)

        btnRefresh.setOnClickListener {
            // Trigger weather update
            updateWeatherData()
        }
    }

    fun refreshMonetTheme(palette: UiMonetPalette = UiMonetThemeManager.getPalette(context)) {
        UiMonetThemeManager.applyLiveToViewHierarchy(this, palette)
        DeskForegroundStyle.apply(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshMonetTheme()
        WeatherGetter.registerListener(weatherListener)
        updateWeatherData()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        WeatherGetter.unregisterListener(weatherListener)
    }

    fun updateWeatherData() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

        // Location text
        val locationMode = prefs.getString("location_mode", "auto") ?: "auto"
        when (locationMode) {
            "city" -> {
                val cityName = prefs.getString("resolved_city_display_name", null)
                    ?: prefs.getString("location_city_name", context.getString(R.string.weather_auto_current))
                txtLocation.text = cityName
                txtLocationMode.text = context.getString(R.string.weather_mode_badge_city)
            }
            "coords" -> {
                val lat = prefs.getString("latitude", "0")
                val lon = prefs.getString("longitude", "0")
                txtLocation.text = "$lat, $lon"
                txtLocationMode.text = context.getString(R.string.weather_mode_badge_coords)
            }
            else -> {
                txtLocation.text = context.getString(R.string.weather_auto_current)
                txtLocationMode.text = context.getString(R.string.weather_mode_badge_auto)
            }
        }

        val temp = WeatherGetter.cachedTemperature
        val code = WeatherGetter.cachedWeatherCode
        val isDay = WeatherGetter.cachedIsDay ?: true
        val wind = WeatherGetter.cachedWindSpeed
        val uv = WeatherGetter.cachedUvIndex
        val precip = WeatherGetter.cachedPrecipitation
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
            imgHeroIcon.setImageResource(getWeatherIconRes(code, isDay))
            txtHeroTemp.text = "${temp.toInt()}$tempSuffix"
            txtHeroCondition.text = getWeatherConditionDescription(context, code)

            txtWind.text = if (wind != null) String.format(Locale.US, "%.1f %s", wind, windSuffix) else "--"
            txtUv.text = if (uv != null) String.format(Locale.US, "%.1f (%s)", uv, getUvIndexDescription(uv)) else "--"
            txtPrecip.text = if (precip != null) String.format(Locale.US, "%.1f %s", precip, precipSuffix) else "0.0 $precipSuffix"
            txtCycle.text = if (isDay) context.getString(R.string.weather_cycle_day) else context.getString(R.string.weather_cycle_night)

            // Alert banner
            var alertMessage: String? = null
            var alertIconRes = 0
            val windKmh = when (windUnit) {
                "mph" -> (wind ?: 0.0) * 1.60934
                "ms" -> (wind ?: 0.0) * 3.6
                else -> wind ?: 0.0
            }
            if (code in listOf(95, 96, 99)) {
                alertMessage = context.getString(R.string.weather_alert_storm_active)
                alertIconRes = R.drawable.ic_weather_lightning_rainy
            } else if (windKmh > 35) {
                alertMessage = context.getString(R.string.weather_alert_high_winds)
                alertIconRes = R.drawable.ic_weather_windy
            } else if (uv != null && uv >= 6.0) {
                alertMessage = context.getString(R.string.weather_alert_high_uv, String.format(Locale.US, "%.1f", uv))
                alertIconRes = R.drawable.ic_clear_day
            }

            if (alertMessage != null) {
                cardAlert.visibility = View.VISIBLE
                txtAlert.text = alertMessage
                if (alertIconRes != 0) imgAlertIcon.setImageResource(alertIconRes)
            } else {
                cardAlert.visibility = View.GONE
            }

            // Hourly forecast
            if (hourlyCodes.isNotEmpty()) {
                txtHourlyTitle.visibility = View.VISIBLE
                layoutHourly.visibility = View.VISIBLE
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val itemsToShow = minOf(12, hourlyCodes.size - currentHour - 1).coerceAtLeast(0)
                while (layoutHourly.childCount > itemsToShow) {
                    layoutHourly.removeViewAt(layoutHourly.childCount - 1)
                }
                val palette = UiMonetThemeManager.getPalette(context)
                val inflater = LayoutInflater.from(context)

                for (offset in 1..itemsToShow) {
                    val idx = currentHour + offset
                    if (idx in hourlyCodes.indices) {
                        val hourlyCode = hourlyCodes[idx]
                        val hourlyTemp = if (idx in hourlyTemps.indices) hourlyTemps[idx] else null

                        // Cached pages detach as the pager moves. Keep their forecast cards
                        // instead of inflating twelve Material hierarchies on every return.
                        val itemView = layoutHourly.getChildAt(offset - 1)
                            ?: inflater.inflate(R.layout.item_weather_forecast, layoutHourly, false).also {
                                UiMonetThemeManager.applyLiveToViewHierarchy(it, palette)
                                layoutHourly.addView(it)
                            }
                        val itemTime = itemView.findViewById<TextView>(R.id.forecast_item_time)
                        val itemIcon = itemView.findViewById<ImageView>(R.id.forecast_item_icon)
                        val itemTemp = itemView.findViewById<TextView>(R.id.forecast_item_temp)
                        val itemCond = itemView.findViewById<TextView>(R.id.forecast_item_condition)

                        itemTime.text = "+${offset}h"
                        itemIcon.setImageResource(getWeatherIconRes(hourlyCode, isDay = true))
                        itemTemp.text = if (hourlyTemp != null) "${hourlyTemp.toInt()}$tempSuffix" else "--"
                        itemCond.text = getWeatherConditionDescription(context, hourlyCode)
                    }
                }
            } else {
                txtHourlyTitle.visibility = View.GONE
                layoutHourly.visibility = View.GONE
            }
        } else {
            imgHeroIcon.setImageResource(R.drawable.ic_weather_unknown)
            txtHeroTemp.text = "--"
            txtHeroCondition.text = context.getString(R.string.weather_auto_no_data)
            cardAlert.visibility = View.GONE
            txtWind.text = "--"
            txtUv.text = "--"
            txtPrecip.text = "--"
            txtCycle.text = "--"
            txtHourlyTitle.visibility = View.GONE
            layoutHourly.visibility = View.GONE
        }
    }
}
