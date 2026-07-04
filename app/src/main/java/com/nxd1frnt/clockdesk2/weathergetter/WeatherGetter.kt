package com.nxd1frnt.clockdesk2.weathergetter

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.nxd1frnt.clockdesk2.utils.LocationManager
import com.nxd1frnt.clockdesk2.network.NetworkManager
import com.nxd1frnt.clockdesk2.utils.Logger
import com.nxd1frnt.clockdesk2.utils.PowerSaveObserver

open class WeatherGetter(
    private val context: Context,
    private val locationManager: LocationManager,
    private val callback: () -> Unit
) : PowerSaveObserver, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        var cachedTemperature: Double? = null
        var cachedWeatherCode: Int? = null
        var cachedIsDay: Boolean? = null
        var cachedWindSpeed: Double? = null
        var cachedHourlyCodes: List<Int> = emptyList()
        var cachedUvIndex: Double? = null
        var cachedDailyCodes: List<Int> = emptyList()
        var cachedDailyMaxTemps: List<Double> = emptyList()

        private val listeners = mutableSetOf<() -> Unit>()

        fun registerListener(listener: () -> Unit) {
            listeners.add(listener)
        }

        fun unregisterListener(listener: () -> Unit) {
            listeners.remove(listener)
        }

        fun updateCache(
            temp: Double?,
            code: Int?,
            isDay: Boolean?,
            wind: Double?,
            hourlyCodes: List<Int>,
            uvIndex: Double? = null,
            dailyCodes: List<Int> = emptyList(),
            dailyMaxTemps: List<Double> = emptyList()
        ) {
            cachedTemperature = temp
            cachedWeatherCode = code
            cachedIsDay = isDay
            cachedWindSpeed = wind
            cachedHourlyCodes = hourlyCodes
            cachedUvIndex = uvIndex
            cachedDailyCodes = dailyCodes
            cachedDailyMaxTemps = dailyMaxTemps
            listeners.forEach {
                try { it() } catch (e: Exception) {}
            }
        }
    }
    private var isPowerSaveActive = false
    private var interval = 30 * 60 * 1000L
    private val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

    override fun onPowerSaveModeChanged(isEnabled: Boolean) {
        isPowerSaveActive = isEnabled
        updateInterval()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "weather_refresh_interval") {
            updateInterval()
            if (lastLatitude != null && lastLongitude != null) {
                Logger.d("WeatherGetter") { "Weather refresh rate preference changed. Rescheduling updates." }
                startUpdates(lastLatitude!!, lastLongitude!!)
            }
        }
    }

    private fun updateInterval() {
        val baseMinutesStr = prefs.getString("weather_refresh_interval", "30") ?: "30"
        val baseMinutes = baseMinutesStr.toLongOrNull() ?: 30L
        val baseIntervalMs = baseMinutes * 60 * 1000L
        
        interval = if (isPowerSaveActive) {
            baseIntervalMs * 2
        } else {
            baseIntervalMs
        }
        Logger.d("WeatherGetter") { "Weather interval updated: $interval ms (powerSave=$isPowerSaveActive)" }
    }

    val requestQueue = NetworkManager.getRequestQueue(context)

    var temperature: Double? = null
    var weatherCode: Int? = null
    var isDay: Boolean? = null

    var windSpeed: Double? = null
    var precipitation: Double? = null
    var cloudCover: Int? = null
    var visibility: Double? = null
    var uvIndex: Double? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (lastLatitude != null && lastLongitude != null) {
                Logger.d("WeatherGetter"){"Auto-refreshing weather data..."}
                fetch(lastLatitude!!, lastLongitude!!)
            }
            handler.postDelayed(this, interval)
        }
    }

    fun startUpdates(latitude: Double, longitude: Double) {
        lastLatitude = latitude
        lastLongitude = longitude

        stopUpdates()
        prefs.registerOnSharedPreferenceChangeListener(this)
        updateInterval()
        handler.post(updateRunnable)
    }

    fun stopUpdates() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        handler.removeCallbacks(updateRunnable)
    }

    open fun fetch(latitude: Double, longitude: Double) {

    }
}