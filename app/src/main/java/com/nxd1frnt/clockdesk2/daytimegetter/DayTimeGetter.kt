package com.nxd1frnt.clockdesk2.daytimegetter

import android.content.Context
import android.util.Log
import com.nxd1frnt.clockdesk2.LocationManager
import com.nxd1frnt.clockdesk2.network.NetworkManager
import java.util.Calendar
import java.util.Date

open class DayTimeGetter(private val context: Context, private val locationManager: LocationManager) {
    val requestQueue = NetworkManager.getRequestQueue(context)
    var sunriseTime: Date? = null
    var sunsetTime: Date? = null
    var dawnTime: Date? = null
    var duskTime: Date? = null
    var solarNoonTime: Date? = null

    open fun fetch(latitude: Double, longitude: Double, callback: () -> Unit) {
        setDefault()
        callback()
    }

    fun setDefault() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        sunriseTime = Calendar.getInstance().apply {
            time = today.time
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
        }.time
        sunsetTime = Calendar.getInstance().apply {
            time = today.time
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
        }.time
        dawnTime = Calendar.getInstance().apply {
            time = today.time
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 30)
        }.time
        duskTime = Calendar.getInstance().apply {
            time = today.time
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 30)
        }.time
        solarNoonTime = Calendar.getInstance().apply {
            time = today.time
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }.time
        Log.d("SunTimes", "Set fallback times: sunrise=$sunriseTime, sunset=$sunsetTime")
    }
}
