package com.nxd1frnt.clockdesk2.weathergetter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nxd1frnt.clockdesk2.LocationManager
import com.nxd1frnt.clockdesk2.network.NetworkManager
import com.nxd1frnt.clockdesk2.utils.Logger
import com.nxd1frnt.clockdesk2.utils.PowerSaveObserver

open class WeatherGetter(
    private val context: Context,
    private val locationManager: LocationManager,
    private val callback: () -> Unit
) : PowerSaveObserver {
    private var interval = 30 * 60 * 1000L

    override fun onPowerSaveModeChanged(isEnabled: Boolean) {
        if (isEnabled) {
            interval = 60*60*1000L
            Logger.d("WeatherGetter"){"Power saving mode enabled. Setting interval to 3600000 ms"}
        } else {
            interval = 30*60*1000L
            Logger.d("WeatherGetter"){"Power saving mode disabled. Setting interval to 1800000 ms"}
        }
    }
    val requestQueue = NetworkManager.getRequestQueue(context)

    var temperature: Double? = null
    var weatherCode: Int? = null
    var isDay: Boolean? = null

    var windSpeed: Double? = null
    var precipitation: Double? = null
    var cloudCover: Int? = null
    var visibility: Double? = null
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
        handler.post(updateRunnable)
    }

    fun stopUpdates() {
        handler.removeCallbacks(updateRunnable)
    }

    open fun fetch(latitude: Double, longitude: Double) {

    }
}