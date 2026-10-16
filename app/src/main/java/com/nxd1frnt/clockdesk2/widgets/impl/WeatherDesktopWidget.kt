package com.nxd1frnt.clockdesk2.widgets.impl

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.weathergetter.celsiusToFahrenheit
import com.nxd1frnt.clockdesk2.weathergetter.getWeatherIconRes
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetDefinition
import com.nxd1frnt.clockdesk2.widgets.WidgetInstance
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetController
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetHost
import kotlin.math.roundToInt

class WeatherDesktopWidget(
    instance: WidgetInstance,
    definition: DesktopWidgetDefinition,
    host: DesktopWidgetHost
) : DesktopWidgetController(instance, definition, host) {

    override var rootView: View? = null
    var weatherTextView: TextView? = null
        private set
    var weatherIconView: ImageView? = null
        private set

    override fun bindView(view: View) {
        super.bindView(view)
        weatherTextView = view.findViewById(R.id.weather_text)
        weatherIconView = view.findViewById(R.id.weather_icon)
    }

    fun updateWeather(temperatureCelsius: Double?, weatherCode: Int, isDay: Boolean) {
        val textView = weatherTextView ?: return
        val iconView = weatherIconView ?: return

        if (temperatureCelsius != null) {
            val prefs = host.getPreferences()
            val tempUnit = prefs.getString("temp_unit", "C") ?: "C"
            val displayTemp = if (tempUnit == "F") {
                celsiusToFahrenheit(temperatureCelsius)
            } else {
                temperatureCelsius
            }
            textView.text = "${displayTemp.roundToInt()}°$tempUnit"
        }

        val iconRes = getWeatherIconRes(weatherCode, isDay)
        iconView.setImageResource(iconRes)
    }

    override fun onDestroy() {
        weatherTextView = null
        weatherIconView = null
        super.onDestroy()
    }
}
