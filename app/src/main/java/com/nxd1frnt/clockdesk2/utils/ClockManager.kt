package com.nxd1frnt.clockdesk2.utils

import android.content.SharedPreferences
import android.os.Handler
import android.util.Log
import android.widget.TextView
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ClockManager(
    private val timeText: TextView,
    private val dateText: TextView,
    private val handler: Handler,
    private val fontManager: FontManager,
    private val dayTimeGetter: DayTimeGetter,
    private val locationManager: LocationManager,
    private val debugCallback: (String, String, String) -> Unit,
    private val onTimeChanged: (Date) -> Unit,
    private val prefs: SharedPreferences,
    initialLoggingState: Boolean
) : PowerSaveObserver {

    private var isLowPower = false

    override fun onPowerSaveModeChanged(isEnabled: Boolean) {
        isLowPower = isEnabled
        updateTimeText()
        Logger.d("ClockManager"){"Power saving mode changed: isLowPower=$isLowPower"}
        startUpdates()
    }
    private var isDebugMode = false
    private val debugCycleInterval = 5L // milliseconds (25 ticks/sec for smooth performance)
    private var simulatedTime: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    private var lastRealTimeDay: Int? = null

    private var additionalLoggingEnabled = initialLoggingState

    private val formatterCache = HashMap<String, SimpleDateFormat>()

    private fun getFormatter(pattern: String): SimpleDateFormat {
        val locale = Locale.getDefault()
        val key = "$pattern|${locale.toLanguageTag()}"
        return formatterCache.getOrPut(key) {
            SimpleDateFormat(pattern, locale)
        }
    }

    private fun shouldUpdateEverySecond(): Boolean {
        if (isDebugMode) return true
        val limitClock = prefs.getBoolean("power_saver_limit_clock", true)
        if (isLowPower && limitClock) return false
        val clockStyle = fontManager.getClockStyle()
        if (clockStyle.isAnalog) return true
        val timePattern = fontManager.getTimeFormatPattern()
        return timePattern.contains('s') || timePattern.contains('S')
    }

    private val clockUpdateRunnable = object : Runnable {
        override fun run() {
            updateClock()
            val now = System.currentTimeMillis()
            val interval = when {
                isDebugMode -> debugCycleInterval
                shouldUpdateEverySecond() -> {
                    val delay = 1000L - (now % 1000L)
                    if (delay <= 0L) 1000L else delay
                }
                else -> {
                    val delay = 60000L - (now % 60000L)
                    if (delay <= 0L) 60000L else delay
                }
            }
            handler.postDelayed(this, interval)
            Logger.v("ClockUpdate"){"Clock updated at $now, next in ${interval}ms"}
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
        val clockStyle = fontManager.getClockStyle()
        val timePattern = fontManager.getTimeFormatPattern().ifBlank { "HH:mm" }

        if (timeText is com.nxd1frnt.clockdesk2.ui.view.ClockTextView) {
            timeText.isAnalogMode = clockStyle.isAnalog
            timeText.setTime(currentTime)
        }

        if (clockStyle.isAnalog) {
            timeText.text = " "
        } else if (clockStyle.isTwoLine) {
            val hourPattern = if (timePattern.contains("h")) "hh" else "HH"
            val minPattern = "mm"
            val hourStr = getFormatter(hourPattern).format(currentTime)
            val minStr = getFormatter(minPattern).format(currentTime)
            val safeHour = hourStr.toCharArray().joinToString("\u2060")
            val safeMin = minStr.toCharArray().joinToString("\u2060")
            timeText.text = "$safeHour\n$safeMin"
        } else {
            timeText.text = getFormatter(timePattern).format(currentTime)
        }
    }

    fun updateDateText() {
        val currentTime = getCurrentTime()
        val dateStyle = fontManager.getDateStyle()

        if (dateText is com.nxd1frnt.clockdesk2.ui.view.DateTextView) {
            dateText.isBadgeMode = dateStyle.isBadge
            dateText.isTwoLineMode = dateStyle.isTwoLine
            dateText.setDate(currentTime)
        }

        if (dateStyle.isBadge) {
            dateText.text = " "
        } else if (dateStyle.isTwoLine) {
            val datePattern = fontManager.getDateFormatPattern().ifBlank { "EEE, MMM dd" }
            dateText.text = formatTwoLineDate(currentTime, datePattern)
        } else {
            val datePattern = fontManager.getDateFormatPattern().ifBlank { "EEE, MMM dd" }
            val effectivePattern = datePattern.replace("\\n", " ").trim()
            dateText.text = getFormatter(effectivePattern).format(currentTime)
        }
    }

    fun formatTwoLineDate(currentTime: Date, pattern: String): String {
        val effectivePattern = pattern.replace("\\n", "\n").trim()
        return try {
            val (topPattern, bottomPattern) = when {
                effectivePattern.contains("\n") -> {
                    val parts = effectivePattern.split("\n", limit = 2)
                    parts[0].trim() to parts[1].trim()
                }
                effectivePattern.contains(",") -> {
                    val parts = effectivePattern.split(",", limit = 2)
                    parts[0].trim() to parts[1].trim()
                }
                effectivePattern.contains(" ") -> {
                    val parts = effectivePattern.split(Regex("\\s+"), limit = 2)
                    parts[0].trim() to parts[1].trim()
                }
                else -> {
                    "EEE" to effectivePattern
                }
            }

            val line1 = getFormatter(topPattern.ifBlank { "EEE" }).format(currentTime)
            val line2 = getFormatter(bottomPattern.ifBlank { "MMM d" }).format(currentTime)
            val safeLine1 = line1.toCharArray().joinToString("\u2060")
            val safeLine2 = line2.toCharArray().joinToString("\u2060")
            "$safeLine1\n$safeLine2"
        } catch (e: Exception) {
            val line1 = getFormatter("EEE").format(currentTime)
            val line2 = getFormatter("MMM d").format(currentTime)
            "$line1\n$line2"
        }
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

        val cal = Calendar.getInstance()
        fun normalizeTime(original: Date?): Date? {
            if (original == null) return null
            cal.time = original
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
        Logger.d("ClockManager"){"Re-normalized sun times for ${today.time}: sunrise=${dayTimeGetter.sunriseTime}, sunset=${dayTimeGetter.sunsetTime}"}
    }

    private fun checkAndUpdateSunTimesForRealTime(currentTime: Date) {
        val currentCal = Calendar.getInstance().apply { time = currentTime }
        val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
        if (lastRealTimeDay != null && lastRealTimeDay != currentDay) {
            if (additionalLoggingEnabled) Log.d("ClockManager", "Day changed to ${currentCal.time}, refreshing sun times")
            locationManager.loadCoordinates { lat, lon ->
                dayTimeGetter.fetch(lat, lon) {
                    Logger.d("ClockManager"){"Sun times updated for new day: sunrise=${dayTimeGetter.sunriseTime}, sunset=${dayTimeGetter.sunsetTime}"}
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

            val timeStr = getFormatter(timePattern).format(simulatedTime.time)
            val sunriseStr = dayTimeGetter.sunriseTime?.let {
                getFormatter(timePattern).format(it)
            } ?: "06:00"
            val sunsetStr = dayTimeGetter.sunsetTime?.let {
                getFormatter(timePattern).format(it)
            } ?: "20:05"
            debugCallback(timeStr, sunriseStr, sunsetStr)
            Logger.d("DemoMode"){
                "Simulated time: $timeStr, date: ${
                    getFormatter(datePattern).format(simulatedTime.time)
                }"}
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
            Logger.w("ClockManager"){"onTimeChanged callback failed: ${e.message}"}
        }
        updateTimeText()
        updateDateText()
            fontManager.applyNightShiftTransition(
                currentTime,
                dayTimeGetter,
                true
            )
    }
}