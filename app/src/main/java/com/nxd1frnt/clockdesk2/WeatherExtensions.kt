package com.nxd1frnt.clockdesk2

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.util.Log
import android.widget.ImageView
import kotlin.math.min

private fun scale(value: Float, intensity: Float, default: Float = 0.8f): Float {
    val result = default + (value - default) * intensity
    return result.coerceIn(0f, 1.5f)
}

fun calculateWeatherIntensity(
    wmoCode: Int,
    windSpeed: Double?,
    precipitation: Double?,
    cloudCover: Int?,
    visibility: Double?
): Float {
    val wind = windSpeed ?: 0.0
    val precip = precipitation ?: 0.0
    val clouds = cloudCover ?: 0
    val vis = visibility ?: 10000.0

    val intensity = when (wmoCode) {
        //clear sky
        0 -> {
            val windBoost = min(wind / 30.0, 0.5) // wind can add some intensity
            (1.0 + windBoost).toFloat()
        }
        //cloudy
        1, 2, 3 -> {
            val baseIntensity = when {
                clouds >= 75 -> 1.2  // overcast
                clouds >= 50 -> 0.8  // cloudy
                clouds >= 25 -> 0.5  // partly cloudy
                else -> 0.3          // mostly clear
            }
            val windBoost = min(wind / 40.0, 0.4)
            (baseIntensity + windBoost).toFloat()
        }

        //fog
        45, 48 -> {
            val baseIntensity = when {
                vis < 200 -> 1.8 // thick fog
                vis < 300 -> 1.6 // moderate fog
                vis < 500 -> 1.4 // dense fog
                vis < 1000 -> 1.0 // fog
                vis < 2000 -> 0.6 // light fog
                else -> 0.4 // haze
            }
            baseIntensity.toFloat()
        }

        //drizzle
        51, 53, 55, 56, 57 -> {
            val baseIntensity = when {
                precip >= 1.0 -> 0.8  // moderate drizzle
                precip >= 0.5 -> 0.6  // light drizzle
                else -> 0.4           // very light drizzle
            }
            val windBoost = min(wind / 50.0, 0.3)
            (baseIntensity + windBoost).toFloat()
        }

        //rain
        61, 63, 65, 80, 81, 82 -> {
            val baseIntensity = when {
                precip >= 10.0 -> 2.0 // heavy rain
                precip >= 4.0 -> 1.6   // moderate rain
                precip >= 2.0 -> 1.2   // light rain
                precip >= 0.5 -> 0.8   // very light rain
                else -> 0.6            // no rain
            }
            val windBoost = min(wind / 40.0, 0.4)
            min((baseIntensity + windBoost).toFloat(), 2.0f)
        }
        // snow
        71, 73, 75, 77, 85, 86 -> {
            val baseIntensity = when {
                precip >= 5.0 -> 1.8   // heavy snow
                precip >= 3.0 -> 1.6   // moderate snow
                precip >= 2.0 -> 1.4   // light snow
                precip >= 1.0 -> 1.0   // very light snow
                precip >= 0.5 -> 0.7   // flurries
                else -> 0.5           // no snow
            }
            val windBoost = min(wind / 30.0, 0.5)
            min((baseIntensity + windBoost).toFloat(), 2.0f)
        }

        // thunderstorm
        95, 96, 99 -> {
            val baseIntensity = when {
                precip >= 15.0 -> 2.0  // severe thunderstorm
                precip >= 8.0 -> 1.8   // strong thunderstorm
                precip >= 4.0 -> 1.5   // thunderstorm
                else -> 1.3            // moderate thunderstorm
            }
            min(baseIntensity.toFloat(), 2.0f)
        }

        // default case for unlisted WMO codes
        else -> 0.5f
    }

    //Log.d("WeatherIntensity",
    //    "WMO=$wmoCode, Wind=${wind.toInt()}, Precip=$precip, Clouds=$clouds%, Vis=${vis.toInt()}m -> Intensity=${"%.2f".format(intensity)}")

    return intensity.coerceIn(0.0f, 2.0f)
}


fun getDefaultWeatherIntensity(wmoCode: Int): Float {
    return when (wmoCode) {
        0 -> 1.0f                           // Clear sky
        1, 2, 3 -> 0.5f                      // Cloudy
        45, 48 -> 0.8f                       // Fog
        51, 53, 55, 56, 57 -> 0.5f           // Drizzle
        61, 63 -> 1.0f                       // Rain
        65, 80, 81, 82 -> 1.4f               // Heavy rain
        71, 73, 75, 77, 85, 86 -> 0.9f       // Snow
        95, 96, 99 -> 1.6f                   // Thunderstorm
        else -> 0.5f
    }
}

fun getWeatherMatrix(wmoCode: Int, isNight: Boolean, intensity: Float): ColorMatrix {
    val matrix = ColorMatrix()

    if (isNight) {
        matrix.setScale(0.7f, 0.7f, 0.8f, 1f)
        val nightTint = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, -15f,
            0f, 1f, 0f, 0f, -10f,
            0f, 0f, 1f, 0f, 25f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(nightTint)
    }

    if (intensity <= 0.05f) return matrix

    when (wmoCode) {
        //clear sky, cloudy, fog
        1, 2, 3, 45, 48 -> {
            val sat = scale(0.2f, intensity)
            val saturation = ColorMatrix()
            saturation.setSaturation(sat)
            matrix.postConcat(saturation)
        }

        51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82 -> {
            val sat = scale(0.65f, intensity)
            val saturation = ColorMatrix()
            saturation.setSaturation(sat)
            matrix.postConcat(saturation)

            val rOffset = -8f * intensity.coerceAtMost(1.5f)
            val gOffset = 8f * intensity.coerceAtMost(1.5f)
            val bOffset = 15f * intensity.coerceAtMost(1.5f)

            val rainTint = ColorMatrix(floatArrayOf(
                0.95f, 0f, 0f, 0f, rOffset,
                0f, 0.98f, 0f, 0f, gOffset,
                0f, 0f, 1.08f, 0f, bOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(rainTint)
        }

        71, 73, 75, 77, 85, 86 -> {
            val sat = scale(0.85f, intensity)
            val saturation = ColorMatrix()
            saturation.setSaturation(sat)
            matrix.postConcat(saturation)

            val bOffset = 25f * intensity.coerceAtMost(1.5f)
            val snowTint = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1.12f, 0f, bOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(snowTint)
        }

        95, 96, 99 -> {
            val sat = scale(0.5f, intensity)
            val saturation = ColorMatrix()
            saturation.setSaturation(sat)
            matrix.postConcat(saturation)

            val rOffset = 8f * intensity.coerceAtMost(1.5f)
            val bOffset = 15f * intensity.coerceAtMost(1.5f)

            val stormTint = ColorMatrix(floatArrayOf(
                0.85f, 0f, 0f, 0f, rOffset,
                0f, 0.85f, 0f, 0f, 0f,
                0f, 0f, 0.95f, 0f, bOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(stormTint)
        }

        0 -> {
            if (!isNight) {
                val sat = scale(1.15f, intensity)
                val saturation = ColorMatrix()
                saturation.setSaturation(sat)
                matrix.postConcat(saturation)

                val rScale = scale(1.08f, intensity)
                val rOffset = 12f * intensity.coerceAtMost(1.5f)

                val sunTint = ColorMatrix(floatArrayOf(
                    rScale, 0f, 0f, 0f, rOffset,
                    0f, 1.04f, 0f, 0f, 8f * intensity.coerceAtMost(1.5f),
                    0f, 0f, 0.92f, 0f, -8f * intensity.coerceAtMost(1.5f),
                    0f, 0f, 0f, 1f, 0f
                ))
                matrix.postConcat(sunTint)
            }
        }
    }

    return matrix
}

fun ImageView.applyWeatherFilter(wmoCode: Int, isNight: Boolean, intensity: Float = 1.0f) {
    this.colorFilter = ColorMatrixColorFilter(getWeatherMatrix(wmoCode, isNight, intensity))
}