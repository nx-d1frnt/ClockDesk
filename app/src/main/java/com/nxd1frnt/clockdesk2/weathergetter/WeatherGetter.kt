package com.nxd1frnt.clockdesk2.weathergetter

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nxd1frnt.clockdesk2.utils.PowerSavePolicy
import com.nxd1frnt.clockdesk2.utils.LocationManager
import com.nxd1frnt.clockdesk2.network.NetworkManager
import com.nxd1frnt.clockdesk2.utils.Logger
import com.nxd1frnt.clockdesk2.utils.PowerSaveObserver

open class WeatherGetter(
    protected val context: Context,
    private val locationManager: LocationManager,
    private val callback: () -> Unit
) : PowerSaveObserver, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        var cachedTemperature: Double? = null
        var cachedWeatherCode: Int? = null
        var cachedIsDay: Boolean? = null
        var cachedWindSpeed: Double? = null
        var cachedHourlyCodes: List<Int> = emptyList()
        var cachedHourlyTemps: List<Double> = emptyList()
        var cachedUvIndex: Double? = null
        var cachedPrecipitation: Double? = null
        var cachedCloudCover: Int? = null
        var cachedVisibility: Double? = null

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
            hourlyTemps: List<Double> = emptyList(),
            uvIndex: Double? = null,
            precipitation: Double? = null,
            cloudCover: Int? = null,
            visibility: Double? = null
        ) {
            cachedTemperature = temp
            cachedWeatherCode = code
            cachedIsDay = isDay
            cachedWindSpeed = wind
            cachedHourlyCodes = hourlyCodes
            cachedHourlyTemps = hourlyTemps
            cachedUvIndex = uvIndex
            cachedPrecipitation = precipitation
            cachedCloudCover = cloudCover
            cachedVisibility = visibility
            listeners.forEach {
                try { it() } catch (e: Exception) {}
            }
        }
    }
    private var isPowerSaveActive = false
    private var interval = 30 * 60 * 1000L
    private val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

    private var updatesRequested = false
    private var lastRequestAt: Long? = null
    private var lastRequestKey: String? = null

    override fun onPowerSaveModeChanged(isEnabled: Boolean) {
        isPowerSaveActive = isEnabled
        updateInterval()
        scheduleNextUpdate()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "weather_refresh_interval" || key == "temperature_unit" ||
            key == "wind_speed_unit" || key == "precipitation_unit") {
            updateInterval()
            scheduleNextUpdate()
        }
    }

    private fun requestKey(): String = listOf(
        lastLatitude, lastLongitude,
        prefs.getString("temperature_unit", "celsius"),
        prefs.getString("wind_speed_unit", "kmh"),
        prefs.getString("precipitation_unit", "mm")
    ).joinToString("|")

    private fun scheduleNextUpdate() {
        handler.removeCallbacks(updateRunnable)
        if (!updatesRequested || PowerSavePolicy.read(prefs, isPowerSaveActive).suspendWeather) return
        val last = lastRequestAt
        val delay = if (last == null || requestKey() != lastRequestKey) 0L
            else (interval - (SystemClock.elapsedRealtime() - last)).coerceAtLeast(0L)
        handler.postDelayed(updateRunnable, delay)
    }

    private fun updateInterval() {
        val baseMinutesStr = prefs.getString("weather_refresh_interval", "30") ?: "30"
        val baseMinutes = (baseMinutesStr.toLongOrNull() ?: 30L).coerceIn(1L, 1440L)
        val baseIntervalMs = baseMinutes * 60 * 1000L
        
        interval = if (isPowerSaveActive) {
            baseIntervalMs * 2
        } else {
            baseIntervalMs
        }
        Logger.d("WeatherGetter") { "Weather interval updated: $interval ms (powerSave=$isPowerSaveActive)" }
    }

    val requestQueue by lazy { NetworkManager.getRequestQueue(context.applicationContext) }

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
            if (!updatesRequested || PowerSavePolicy.read(prefs, isPowerSaveActive).suspendWeather) return
            val latitude = lastLatitude ?: return
            val longitude = lastLongitude ?: return
            // Preserve the schedule across pauses and policy changes, including in-flight requests.
            lastRequestAt = SystemClock.elapsedRealtime()
            lastRequestKey = requestKey()
            fetch(latitude, longitude)
            scheduleNextUpdate()
        }
    }

    fun startUpdates(latitude: Double, longitude: Double) {
        lastLatitude = latitude
        lastLongitude = longitude
        if (!updatesRequested) prefs.registerOnSharedPreferenceChangeListener(this)
        updatesRequested = true
        updateInterval()
        scheduleNextUpdate()
    }

    fun stopUpdates() {
        updatesRequested = false
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        handler.removeCallbacks(updateRunnable)
    }

    open fun fetch(latitude: Double, longitude: Double) {

    }
}