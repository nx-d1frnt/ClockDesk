package com.nxd1frnt.clockdesk2

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.util.Log
import android.widget.LinearLayout
import java.util.*

class GradientManager( private val backgroundLayout: LinearLayout, private val sunTimeApi: SunTimeApi, private val handler: Handler ) {
    private var isDebugMode = false
    private val debugCycleInterval = 5L // 500ms per 30 minutes
    private var simulatedTime: Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
    private val gradientUpdateRunnable = object : Runnable {
        override fun run() {
            updateGradient()
            handler.postDelayed(this, if (isDebugMode) debugCycleInterval else 60000)
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

    fun toggleDebugMode(enabled: Boolean) {
        isDebugMode = enabled
        if (isDebugMode) {
            simulatedTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
        startUpdates()
    }

    fun updateGradient() {
        val currentTime = if (isDebugMode) {
            simulatedTime.add(Calendar.MINUTE, 1)
            if (simulatedTime.get(Calendar.HOUR_OF_DAY) >= 24) {
                simulatedTime.set(Calendar.HOUR_OF_DAY, 0)
                simulatedTime.set(Calendar.MINUTE, 0)
            }
            simulatedTime.time
        } else {
            Calendar.getInstance().time
        }
        val (topColor, bottomColor) = getSkyGradientColors(currentTime)
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor)
        )
        backgroundLayout.background = gradientDrawable
        Log.d("GradientUpdate", "Colors: top=$topColor, bottom=$bottomColor")
    }

    private fun getSkyGradientColors(currentTime: Date): Pair<Int, Int> {
        val sunrise = sunTimeApi.sunriseTime ?: sunTimeApi.setFallbackTimes().let { sunTimeApi.sunriseTime!! }
        val sunset = sunTimeApi.sunsetTime ?: sunTimeApi.setFallbackTimes().let { sunTimeApi.sunsetTime!! }
        val dawn = sunTimeApi.dawnTime ?: sunTimeApi.setFallbackTimes().let { sunTimeApi.dawnTime!! }
        val solarNoon = sunTimeApi.solarNoonTime ?: sunTimeApi.setFallbackTimes().let { sunTimeApi.solarNoonTime!! }
        val dusk = sunTimeApi.duskTime ?: sunTimeApi.setFallbackTimes().let { sunTimeApi.duskTime!! }

        // Normalize times to today
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
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

        val postSunsetCal = Calendar.getInstance().apply { time = sunsetCal.time; add(Calendar.MINUTE, 30) }
        val fullNightCal = Calendar.getInstance().apply { time = postSunsetCal.time; add(Calendar.MINUTE, 40) }

        return when {
            currentCal.time.before(dawnCal.time) -> {
                Log.d("Gradient", "Night phase at ${currentCal.time}")
                Pair(0xFF08090D.toInt(), 0xFF161B1F.toInt())
            }
            currentCal.time.before(sunriseCal.time) -> {
                val factor = (currentCal.time.time - dawnCal.time.time).toFloat() / (sunriseCal.time.time - dawnCal.time.time)
                Log.d("Gradient", "Dawn to sunrise at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF08090D.toInt(), 0xFF504787.toInt(), factor),
                    interpolateColor(0xFF161B1F.toInt(), 0xFFFE8A34.toInt(), factor)
                )
            }
            currentCal.time.before(solarNoonCal.time) -> {
                val factor = (currentCal.time.time - sunriseCal.time.time).toFloat() / (solarNoonCal.time.time - sunriseCal.time.time)
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
                val factor = (currentCal.time.time - sunsetCal.time.time).toFloat() / (duskCal.time.time - sunsetCal.time.time)
                Log.d("Gradient", "Sunset to dusk at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF1E90FF.toInt(), 0xFF393854.toInt(), factor),
                    interpolateColor(0xFFB0E0E6.toInt(), 0xFFF97D3D.toInt(), factor)
                )
            }
            currentCal.time.before(postSunsetCal.time) -> {
                val factor = (currentCal.time.time - duskCal.time.time).toFloat() / (postSunsetCal.time.time - duskCal.time.time)
                Log.d("Gradient", "Dusk to post-sunset at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF393854.toInt(), 0xFF52565F.toInt(), factor),
                    interpolateColor(0xFFF97D3D.toInt(), 0xFFF4794D.toInt(), factor)
                )
            }
            currentCal.time.before(fullNightCal.time) -> {
                val factor = (currentCal.time.time - postSunsetCal.time.time).toFloat() / (fullNightCal.time.time - postSunsetCal.time.time)
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