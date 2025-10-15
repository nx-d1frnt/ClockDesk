package com.nxd1frnt.clockdesk2

import android.os.Handler
import android.util.Log
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class ClockManager(
    private val timeText: TextView,
    private val dateText: TextView,
    private val handler: Handler,
    private val fontManager: FontManager,
    private val sunTimeApi: SunTimeApi,
    private val locationManager: LocationManager,
    private val debugCallback: (String, String, String) -> Unit,
    private val onTimeChanged: (Date) -> Unit

) {
    private var isDebugMode = false
    private val debugCycleInterval = 5L // milliseconds
    private var simulatedTime: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    private var lastRealTimeDay: Int? = null

    private val clockUpdateRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, if (isDebugMode) debugCycleInterval else 1000)
            Log.d("ClockUpdate", "Clock updated at ${System.currentTimeMillis()}")
        }
    }

    fun getCurrentTime(): Date {
        return if (isDebugMode) simulatedTime.time else Calendar.getInstance().time
    }

    fun updateTimeText() {
        val currentTime = getCurrentTime()
        val timePattern = fontManager.getTimeFormatPattern().ifBlank { "HH:mm" }
        timeText.text = SimpleDateFormat(timePattern, Locale.getDefault()).format(currentTime)
    }

    fun updateDateText() {
        val currentTime = getCurrentTime()
        val datePattern = fontManager.getDateFormatPattern().ifBlank { "EEE, MMM dd" }
        dateText.text = SimpleDateFormat(datePattern, Locale.getDefault()).format(currentTime)
    }

    fun startUpdates() {
        handler.removeCallbacks(clockUpdateRunnable)
        handler.post(clockUpdateRunnable)
    }

    fun stopUpdates() {
        handler.removeCallbacks(clockUpdateRunnable)
    }

    fun toggleDebugMode(enabled: Boolean) {
        isDebugMode = enabled
        if (isDebugMode) {
            simulatedTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            updateSunTimesForSimulatedDay()
        } else {
            lastRealTimeDay = null
        }
        startUpdates()
    }

    private fun updateSunTimesForSimulatedDay() {
        val today = simulatedTime.clone() as Calendar
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        fun normalizeTime(original: Date?): Date? {
            if (original == null) return null
            val cal = Calendar.getInstance().apply { time = original }
            cal.set(Calendar.YEAR, today.get(Calendar.YEAR))
            cal.set(Calendar.MONTH, today.get(Calendar.MONTH))
            cal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))
            return cal.time
        }

        sunTimeApi.sunriseTime = normalizeTime(sunTimeApi.sunriseTime)
        sunTimeApi.sunsetTime = normalizeTime(sunTimeApi.sunsetTime)
        sunTimeApi.dawnTime = normalizeTime(sunTimeApi.dawnTime)
        sunTimeApi.duskTime = normalizeTime(sunTimeApi.duskTime)
        sunTimeApi.solarNoonTime = normalizeTime(sunTimeApi.solarNoonTime)
        Log.d(
            "ClockManager",
            "Re-normalized sun times for ${today.time}: sunrise=${sunTimeApi.sunriseTime}, sunset=${sunTimeApi.sunsetTime}"
        )
    }

    private fun checkAndUpdateSunTimesForRealTime(currentTime: Date) {
        val currentCal = Calendar.getInstance().apply { time = currentTime }
        val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
        if (lastRealTimeDay != null && lastRealTimeDay != currentDay) {
            Log.d("ClockManager", "Day changed to ${currentCal.time}, refreshing sun times")
            locationManager.loadCoordinates { lat, lon ->
                sunTimeApi.fetchSunTimes(lat, lon) {
                    Log.d(
                        "ClockManager",
                        "Sun times updated for new day: sunrise=${sunTimeApi.sunriseTime}, sunset=${sunTimeApi.sunsetTime}"
                    )
                }
            }
        }
        lastRealTimeDay = currentDay
    }

    private fun updateClock() {
        val currentTime = if (isDebugMode) {
            simulatedTime.add(Calendar.MINUTE, 1)
            if (simulatedTime.get(Calendar.HOUR_OF_DAY) >= 24) {
                simulatedTime.set(Calendar.HOUR_OF_DAY, 0)
                simulatedTime.set(Calendar.MINUTE, 0)
                simulatedTime.add(Calendar.DAY_OF_MONTH, 1)
                updateSunTimesForSimulatedDay()
            }

            val timePattern = fontManager.getTimeFormatPattern().ifBlank { "HH:mm" }
            val datePattern = fontManager.getDateFormatPattern().ifBlank { "yyyy-MM-dd" }

            val timeStr = SimpleDateFormat(timePattern, Locale.getDefault()).format(simulatedTime.time)
            val sunriseStr = sunTimeApi.sunriseTime?.let {
                SimpleDateFormat(timePattern, Locale.getDefault()).format(it)
            } ?: "06:00"
            val sunsetStr = sunTimeApi.sunsetTime?.let {
                SimpleDateFormat(timePattern, Locale.getDefault()).format(it)
            } ?: "20:05"
            debugCallback(timeStr, sunriseStr, sunsetStr)
            Log.d(
                "DemoMode",
                "Simulated time: $timeStr, date: ${
                    SimpleDateFormat(
                        datePattern,
                        Locale.getDefault()
                    ).format(simulatedTime.time)
                }"
            )
            simulatedTime.time
        } else {
            val realTime = Calendar.getInstance().time
            checkAndUpdateSunTimesForRealTime(realTime)
            realTime
        }
        // Always notify listener about the current time (so UI like gradients/dimming can react), for both debug and real time.
        try {
            onTimeChanged(currentTime)
        } catch (e: Exception) {
            Log.w("ClockManager", "onTimeChanged callback failed: ${e.message}")
        }
        updateTimeText()
        updateDateText()
        if (fontManager.isNightShiftEnabled()) {
            fontManager.applyNightShiftTransition(
                currentTime,
                sunTimeApi,
                true
            )
        }
    }
}