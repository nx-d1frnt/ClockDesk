package com.nxd1frnt.clockdesk2.weathergetter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nxd1frnt.clockdesk2.LocationManager
import com.nxd1frnt.clockdesk2.network.NetworkManager

// Добавляем callback в конструктор, как в MusicGetter
open class WeatherGetter(
    private val context: Context,
    private val locationManager: LocationManager,
    private val callback: () -> Unit
) {
    val requestQueue = NetworkManager.getRequestQueue(context)

    var temperature: Double? = null
    var weatherCode: Int? = null
    var isDay: Boolean? = null

    var windSpeed: Double? = null
    var precipitation: Double? = null
    var cloudCover: Int? = null
    var visibility: Double? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isPowerSavingMode = false
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    private val intervalNormal = 30 * 60 * 1000L
    private val intervalPowerSave = 60 * 60 * 1000L

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (lastLatitude != null && lastLongitude != null) {
                Log.d("WeatherGetter", "Auto-refreshing weather data...")
                fetch(lastLatitude!!, lastLongitude!!)
            }

            val nextInterval = if (isPowerSavingMode) intervalPowerSave else intervalNormal
            handler.postDelayed(this, nextInterval)
        }
    }

    fun startUpdates(latitude: Double, longitude: Double) {
        lastLatitude = latitude
        lastLongitude = longitude

        stopUpdates()
        handler.post(updateRunnable)
    }

    fun stopUpdates() {
        handler.removeCallbacks(updateRunnable)
    }

    fun setPowerSavingMode(enabled: Boolean) {
        isPowerSavingMode = enabled
    }

    open fun fetch(latitude: Double, longitude: Double) {

    }
}