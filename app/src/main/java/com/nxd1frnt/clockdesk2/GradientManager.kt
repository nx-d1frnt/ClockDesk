package com.nxd1frnt.clockdesk2

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.util.Log
import android.widget.LinearLayout
import java.util.*

class GradientManager(
    private val backgroundLayout: LinearLayout,
    private val sunTimeApi: SunTimeApi,
    private val locationManager: LocationManager,
    private val handler: Handler
) {
    private var currentSimulatedTime: Date? = null
    private var isDemoMode = false
    private val debugCycleInterval = 500L
    private var simulatedTime: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    private var lastRealTimeDay: Int? = null

    private val gradientUpdateRunnable = object : Runnable {
        override fun run() {
            updateGradient()
            handler.postDelayed(this, if (isDemoMode) debugCycleInterval else 60000)
            Log.d("GradientUpdate", "Gradient updated at ${System.currentTimeMillis()}")
        }
    }

    fun startUpdates() {
        handler.removeCallbacks(gradientUpdateRunnable)
        handler.post(gradientUpdateRunnable)
    }

    fun stopUpdates() {
        handler.removeCallbacks(gradientUpdateRunnable)
    }

    fun updateSimulatedTime(time: Date) {
        currentSimulatedTime = time
        if (isDemoMode) {
            updateGradient()
        }
    }

    private fun normalizeSunTimesToSimulatedDay() {
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
            "GradientManager",
            "Normalized sun times for demo mode: sunrise=${sunTimeApi.sunriseTime}, sunset=${sunTimeApi.sunsetTime}"
        )
    }

    fun toggleDebugMode(enabled: Boolean) {
        isDemoMode = enabled
        if (isDemoMode) {
            simulatedTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            normalizeSunTimesToSimulatedDay() // <-- Add this line
        } else {
            lastRealTimeDay = null
        }
        startUpdates()
    }

    private fun checkAndUpdateSunTimesForRealTime(currentTime: Date) {
        val currentCal = Calendar.getInstance().apply { time = currentTime }
        val currentDay = currentCal.get(Calendar.DAY_OF_YEAR)
        if (lastRealTimeDay != null && lastRealTimeDay != currentDay) {
            Log.d("GradientManager", "Day changed to ${currentCal.time}, refreshing sun times")
            locationManager.loadCoordinates { lat, lon ->
                sunTimeApi.fetchSunTimes(lat, lon) {
                    Log.d(
                        "GradientManager",
                        "Sun times updated for new day: sunrise=${sunTimeApi.sunriseTime}, sunset=${sunTimeApi.sunsetTime}"
                    )
                }
            }
        }
        lastRealTimeDay = currentDay
    }

    fun updateGradient() {
        val currentTime = when {
            isDemoMode && currentSimulatedTime != null -> currentSimulatedTime!!
            isDemoMode -> {
                simulatedTime.add(Calendar.MINUTE, 1)
                if (simulatedTime.get(Calendar.HOUR_OF_DAY) >= 24) {
                    simulatedTime.set(Calendar.HOUR_OF_DAY, 0)
                    simulatedTime.set(Calendar.MINUTE, 0)
                    simulatedTime.add(Calendar.DAY_OF_MONTH, 1)
                    normalizeSunTimesToSimulatedDay()
                }
                simulatedTime.time
            }

            else -> {
                val realTime = Calendar.getInstance().time
                checkAndUpdateSunTimesForRealTime(realTime)
                realTime
            }
        }
        val (topColor, bottomColor) = getSkyGradientColors(currentTime)
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor)
        )
        backgroundLayout.background = gradientDrawable
        Log.d("GradientUpdate", "Colors: top=$topColor, bottom=$bottomColor at time=$currentTime")
    }

    fun getCurrentGradient(): GradientDrawable {
        val (topColor, bottomColor) = getSkyGradientColors(if (isDemoMode) simulatedTime.time else Calendar.getInstance().time)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor)
        )
    }

    private fun getSkyGradientColors(currentTime: Date): Pair<Int, Int> {
        val sunrise = sunTimeApi.sunriseTime
            ?: run { sunTimeApi.setFallbackTimes(); sunTimeApi.sunriseTime!! }
        val sunset =
            sunTimeApi.sunsetTime ?: run { sunTimeApi.setFallbackTimes(); sunTimeApi.sunsetTime!! }
        val dawn =
            sunTimeApi.dawnTime ?: run { sunTimeApi.setFallbackTimes(); sunTimeApi.dawnTime!! }
        val solarNoon = sunTimeApi.solarNoonTime
            ?: run { sunTimeApi.setFallbackTimes(); sunTimeApi.solarNoonTime!! }
        val dusk =
            sunTimeApi.duskTime ?: run { sunTimeApi.setFallbackTimes(); sunTimeApi.duskTime!! }

        // Use simulatedTime's date in debug mode, real-time date otherwise
        val today = if (isDemoMode) {
            (simulatedTime.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } else {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        val currentCal = Calendar.getInstance().apply { time = currentTime }
        currentCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        currentCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        currentCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val dawnCal = Calendar.getInstance().apply { time = dawn }
        dawnCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        dawnCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        dawnCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val sunriseCal = Calendar.getInstance().apply { time = sunrise }
        sunriseCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        sunriseCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        sunriseCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val solarNoonCal = Calendar.getInstance().apply { time = solarNoon }
        solarNoonCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        solarNoonCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        solarNoonCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val sunsetCal = Calendar.getInstance().apply { time = sunset }
        sunsetCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        sunsetCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        sunsetCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val duskCal = Calendar.getInstance().apply { time = dusk }
        duskCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        duskCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        duskCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val postSunsetCal =
            Calendar.getInstance().apply { time = sunsetCal.time; add(Calendar.MINUTE, 30) }
        val fullNightCal =
            Calendar.getInstance().apply { time = postSunsetCal.time; add(Calendar.MINUTE, 40) }

        return when {
            currentCal.time.before(dawnCal.time) -> {
                Log.d("Gradient", "Night phase at ${currentCal.time}")
                Pair(0xFF08090D.toInt(), 0xFF161B1F.toInt())
            }

            currentCal.time.before(sunriseCal.time) -> {
                val factor =
                    (currentCal.time.time - dawnCal.time.time).toFloat() / (sunriseCal.time.time - dawnCal.time.time)
                Log.d("Gradient", "Dawn to sunrise at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF08090D.toInt(), 0xFF504787.toInt(), factor),
                    interpolateColor(0xFF161B1F.toInt(), 0xFFFE8A34.toInt(), factor)
                )
            }

            currentCal.time.before(solarNoonCal.time) -> {
                val factor =
                    (currentCal.time.time - sunriseCal.time.time).toFloat() / (solarNoonCal.time.time - sunriseCal.time.time)
                Log.d("Gradient", "Sunrise to solar noon at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF504787.toInt(), 0xFF1E90FF.toInt(), factor),
                    interpolateColor(0xFFFE8A34.toInt(), 0xFFB0E0E6.toInt(), factor)
                )
            }

            currentCal.time.before(sunsetCal.time) -> {
                Log.d("Gradient", "Midday phase at ${currentCal.time}")
                Pair(0xFF1E90FF.toInt(), 0xFFB0E0E6.toInt())
            }

            currentCal.time.before(duskCal.time) -> {
                val factor =
                    (currentCal.time.time - sunsetCal.time.time).toFloat() / (duskCal.time.time - sunsetCal.time.time)
                Log.d("Gradient", "Sunset to dusk at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF1E90FF.toInt(), 0xFF393854.toInt(), factor),
                    interpolateColor(0xFFB0E0E6.toInt(), 0xFFF97D3D.toInt(), factor)
                )
            }

            currentCal.time.before(postSunsetCal.time) -> {
                val factor =
                    (currentCal.time.time - duskCal.time.time).toFloat() / (postSunsetCal.time.time - duskCal.time.time)
                Log.d("Gradient", "Dusk to post-sunset at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF393854.toInt(), 0xFF52565F.toInt(), factor),
                    interpolateColor(0xFFF97D3D.toInt(), 0xFFF4794D.toInt(), factor)
                )
            }

            currentCal.time.before(fullNightCal.time) -> {
                val factor =
                    (currentCal.time.time - postSunsetCal.time.time).toFloat() / (fullNightCal.time.time - postSunsetCal.time.time)
                Log.d("Gradient", "Post-sunset to full night at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF52565F.toInt(), 0xFF08090D.toInt(), factor),
                    interpolateColor(0xFFF4794D.toInt(), 0xFF161B1F.toInt(), factor)
                )
            }

            else -> {
                Log.d("Gradient", "Full night phase at ${currentCal.time}")
                Pair(0xFF08090D.toInt(), 0xFF161B1F.toInt())
            }
        }
    }

    private fun interpolateColor(color1: Int, color2: Int, factor: Float): Int {
        val clampedFactor = factor.coerceIn(0f, 1f)
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        val a1 = Color.alpha(color1)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        val a2 = Color.alpha(color2)
        return Color.argb(
            (a1 + (a2 - a1) * clampedFactor).toInt(),
            (r1 + (r2 - r1) * clampedFactor).toInt(),
            (g1 + (g2 - g1) * clampedFactor).toInt(),
            (b1 + (b2 - b1) * clampedFactor).toInt()
        )
    }
}