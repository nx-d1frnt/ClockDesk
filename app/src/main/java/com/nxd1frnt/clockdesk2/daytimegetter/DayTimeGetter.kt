package com.nxd1frnt.clockdesk2.daytimegetter

import android.content.Context
import android.util.Log
import com.nxd1frnt.clockdesk2.utils.LocationManager
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
    fun isDay(): Boolean {
        val now = Date()
        return if (sunriseTime != null && sunsetTime != null) {
            now.after(sunriseTime) && now.before(sunsetTime)
        } else {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            hour in 6..18
        }
    }

    fun getDayFactor(targetTime: Date = Date()): Float {
        val sunrise = sunriseTime ?: run { setDefault(); sunriseTime!! }
        val sunset = sunsetTime ?: run { setDefault(); sunsetTime!! }
        val dawn = dawnTime ?: run { setDefault(); dawnTime!! }
        val solarNoon = solarNoonTime ?: run { setDefault(); solarNoonTime!! }
        val dusk = duskTime ?: run { setDefault(); duskTime!! }

        fun timeToMsOfDay(date: Date): Long {
            val cal = Calendar.getInstance().apply { time = date }
            return ((cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)) * 1000L + cal.get(Calendar.MILLISECOND))
        }

        val nowMs = timeToMsOfDay(targetTime)
        val dawnMs = timeToMsOfDay(dawn)
        val sunriseMs = timeToMsOfDay(sunrise)
        val noonMs = timeToMsOfDay(solarNoon)
        val sunsetMs = timeToMsOfDay(sunset)
        val duskMs = timeToMsOfDay(dusk)

        return when {
            nowMs < dawnMs -> 0f
            nowMs < sunriseMs -> {
                val progress = (nowMs - dawnMs).toFloat() / (sunriseMs - dawnMs).coerceAtLeast(1L)
                0.4f * progress
            }
            nowMs < noonMs -> {
                val progress = (nowMs - sunriseMs).toFloat() / (noonMs - sunriseMs).coerceAtLeast(1L)
                0.4f + 0.6f * progress
            }
            nowMs < sunsetMs -> {
                val progress = (nowMs - noonMs).toFloat() / (sunsetMs - noonMs).coerceAtLeast(1L)
                1.0f - 0.6f * progress
            }
            nowMs < duskMs -> {
                val progress = (nowMs - sunsetMs).toFloat() / (duskMs - sunsetMs).coerceAtLeast(1L)
                0.4f * (1.0f - progress)
            }
            else -> 0f
        }.coerceIn(0f, 1f)
    }
}

