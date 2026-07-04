package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter
import com.nxd1frnt.clockdesk2.weathergetter.getWeatherIconRes

class WeatherChipPlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "show_weather_chip"

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
        return LayoutInflater.from(context)
            .inflate(R.layout.smart_chip_layout, null, false)
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        val temp = WeatherGetter.cachedTemperature
        val code = WeatherGetter.cachedWeatherCode
        val isDay = WeatherGetter.cachedIsDay ?: true

        if (temp != null && code != null) {
            val iconRes = getWeatherIconRes(code, isDay)
            iconView.setImageResource(iconRes)
            textView.text = "${temp.toInt()}°"
            return true
        }

        return false
    }
}
