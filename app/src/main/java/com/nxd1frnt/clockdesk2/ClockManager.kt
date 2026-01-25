package com.nxd1frnt.clockdesk2

import android.content.Context
import android.os.Handler
import android.util.Log
import android.widget.TextView
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import com.nxd1frnt.clockdesk2.utils.PowerSaveObserver
import java.text.SimpleDateFormat
import java.util.*

class ClockManager(
    private val timeText: TextView,
    private val dateText: TextView,
    private val handler: Handler,
    private val fontManager: FontManager,
    private val dayTimeGetter: DayTimeGetter,
    private val locationManager: LocationManager,
    private val debugCallback: (String, String, String) -> Unit,
    private val onTimeChanged: (Date) -> Unit,
    initialLoggingState: Boolean
) : PowerSaveObserver {

    private var isLowPower = false

    override fun onPowerSaveModeChanged(isEnabled: Boolean) {
        isLowPower = isEnabled
        updateTimeText()
        Log.d("ClockManager", "Power saving mode changed: isLowPower=$isLowPower")
    }
    private var isDebugMode = false
    private val debugCycleInterval = 5L // milliseconds
    private var simulatedTime: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    private var lastRealTimeDay: Int? = null

    private var additionalLoggingEnabled = initialLoggingState

    private val clockUpdateRunnable = object : Runnable {
        override fun run() {
            updateClock()
            val interval = when {
                isDebugMode -> debugCycleInterval
                isLowPower -> 60000L // 1 minute
                else -> 1000L // 1 second
            }
            handler.postDelayed(this, interval)
            if (additionalLoggingEnabled) Log.d("ClockUpdate", "Clock updated at ${System.currentTimeMillis()}")
        }
    }

    fun setAdditionalLogging(enabled: Boolean) {
        additionalLoggingEnabled = enabled
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

        dayTimeGetter.sunriseTime = normalizeTime(dayTimeGetter.sunriseTime)
        dayTimeGetter.sunsetTime = normalizeTime(dayTimeGetter.sunsetTime)
        dayTimeGetter.dawnTime = normalizeTime(dayTimeGetter.dawnTime)
        dayTimeGetter.duskTime = normalizeTime(dayTimeGetter.duskTime)
        dayTimeGetter.solarNoonTime = normalizeTime(dayTimeGetter.solarNoonTime)
        Log.d(
            "ClockManager",
            "Re-normalized sun times for ${today.time}: sunrise=${dayTimeGetter.sunriseTime}, sunset=${dayTimeGetter.sunsetTime}"
        )
    }

    private fun checkAndUpdateSunTimesForRealTime(currentTime: Date) {
        val currentCal = Calendar.getInstance().apply { time = currentTime }
        val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
        if (lastRealTimeDay != null && lastRealTimeDay != currentDay) {
            if (additionalLoggingEnabled) Log.d("ClockManager", "Day changed to ${currentCal.time}, refreshing sun times")
            locationManager.loadCoordinates { lat, lon ->
                dayTimeGetter.fetch(lat, lon) {
                    Log.d(
                        "ClockManager",
                        "Sun times updated for new day: sunrise=${dayTimeGetter.sunriseTime}, sunset=${dayTimeGetter.sunsetTime}"
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
            val sunriseStr = dayTimeGetter.sunriseTime?.let {
                SimpleDateFormat(timePattern, Locale.getDefault()).format(it)
            } ?: "06:00"
            val sunsetStr = dayTimeGetter.sunsetTime?.let {
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
                dayTimeGetter,
                true
            )
        }
    }
}