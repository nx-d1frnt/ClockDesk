package com.nxd1frnt.clockdesk2.utils

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
    visibility: Double?,
    windUnit: String = "kmh",
    precipUnit: String = "mm"
): Float {
    val rawWind = windSpeed ?: 0.0
    val rawPrecip = precipitation ?: 0.0

    val wind = when (windUnit) {
        "mph" -> rawWind * 1.60934
        "ms" -> rawWind * 3.6
        else -> rawWind
    }

    val precip = when (precipUnit) {
        "inch" -> rawPrecip * 25.4
        else -> rawPrecip
    }

    val clouds = cloudCover ?: 0
    val vis = visibility ?: 10000.0

    val intensity = when (wmoCode) {
        // clear sky
        0 -> {
            val windBoost = min(wind / 40.0, 0.3)
            (0.8 + windBoost).toFloat()
        }
        // cloudy
        1, 2, 3 -> {
            val baseIntensity = when {
                clouds >= 80 -> 1.0  // overcast
                clouds >= 50 -> 0.8  // cloudy
                clouds >= 25 -> 0.6  // partly cloudy
                else -> 0.4          // mostly clear
            }
            val windBoost = min(wind / 40.0, 0.3)
            (baseIntensity + windBoost).toFloat()
        }

        // fog
        45, 48 -> {
            val baseIntensity = when {
                vis < 300 -> 1.2  // thick fog
                vis < 1000 -> 1.0 // fog
                vis < 3000 -> 0.7 // light fog
                else -> 0.5       // haze
            }
            baseIntensity.toFloat()
        }

        // drizzle
        51, 53, 55, 56, 57 -> {
            val baseIntensity = when {
                precip >= 1.5 -> 1.0  // moderate drizzle
                precip >= 0.5 -> 0.8  // light drizzle
                precip > 0.0 -> 0.6   // very light drizzle
                else -> 0.6           // default for drizzle code
            }
            val windBoost = min(wind / 50.0, 0.2)
            (baseIntensity + windBoost).toFloat()
        }

        // rain
        61, 63, 65, 80, 81, 82 -> {
            val baseIntensity = when {
                precip >= 8.0 -> 1.5   // heavy rain
                precip >= 3.0 -> 1.2   // moderate rain
                precip >= 1.0 -> 1.0   // light rain
                precip > 0.0 -> 0.8    // very light rain
                else -> when (wmoCode) {
                    65, 82 -> 1.4      // heavy rain code
                    63, 81 -> 1.1      // moderate rain code
                    else -> 0.8        // light rain code (61, 80)
                }
            }
            val windBoost = min(wind / 40.0, 0.3)
            (baseIntensity + windBoost).toFloat()
        }

        // snow
        71, 73, 75, 77, 85, 86 -> {
            val baseIntensity = when {
                precip >= 4.0 -> 1.5   // heavy snow
                precip >= 2.0 -> 1.2   // moderate snow
                precip >= 0.5 -> 1.0   // light snow
                precip > 0.0 -> 0.8    // flurries
                else -> when (wmoCode) {
                    75, 86 -> 1.4      // heavy snow code
                    73, 85 -> 1.1      // moderate snow code
                    else -> 0.8        // light snow code (71, 77)
                }
            }
            val windBoost = min(wind / 30.0, 0.3)
            (baseIntensity + windBoost).toFloat()
        }

        // thunderstorm
        95, 96, 99 -> {
            val baseIntensity = when {
                precip >= 10.0 -> 1.6  // severe thunderstorm
                precip >= 4.0 -> 1.4   // strong thunderstorm
                precip > 0.0 -> 1.2    // thunderstorm
                else -> if (wmoCode == 99 || wmoCode == 96) 1.5 else 1.3
            }
            baseIntensity.toFloat()
        }

        // default case for unlisted WMO codes
        else -> 0.8f
    }

    return intensity.coerceIn(0.2f, 1.6f)
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
        matrix.setScale(0.88f, 0.88f, 0.92f, 1f)
        val nightTint = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, -5f,
            0f, 1f, 0f, 0f, -3f,
            0f, 0f, 1f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(nightTint)
    }

    if (intensity <= 0.05f) return matrix

    when (wmoCode) {
        // overcast + fog → desaturate
        3, 45, 48 -> {
            val sat = scale(0.55f, intensity)
            val saturation = ColorMatrix()
            saturation.setSaturation(sat)
            matrix.postConcat(saturation)
        }

        51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82 -> {
            val sat = scale(0.80f, intensity)
            val saturation = ColorMatrix()
            saturation.setSaturation(sat)
            matrix.postConcat(saturation)

            val rOffset = -3f * intensity.coerceAtMost(1.5f)
            val gOffset = 2f * intensity.coerceAtMost(1.5f)
            val bOffset = 5f * intensity.coerceAtMost(1.5f)

            val rainTint = ColorMatrix(floatArrayOf(
                0.98f, 0f, 0f, 0f, rOffset,
                0f, 0.99f, 0f, 0f, gOffset,
                0f, 0f, 1.03f, 0f, bOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(rainTint)
        }

        71, 73, 75, 77, 85, 86 -> {
            val sat = scale(0.90f, intensity)
            val saturation = ColorMatrix()
            saturation.setSaturation(sat)
            matrix.postConcat(saturation)

            val bOffset = 8f * intensity.coerceAtMost(1.5f)
            val snowTint = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1.03f, 0f, bOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(snowTint)
        }

        95, 96, 99 -> {
            val sat = scale(0.70f, intensity)
            val saturation = ColorMatrix()
            saturation.setSaturation(sat)
            matrix.postConcat(saturation)

            val rOffset = 3f * intensity.coerceAtMost(1.5f)
            val bOffset = 6f * intensity.coerceAtMost(1.5f)

            val stormTint = ColorMatrix(floatArrayOf(
                0.92f, 0f, 0f, 0f, rOffset,
                0f, 0.92f, 0f, 0f, 0f,
                0f, 0f, 0.98f, 0f, bOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.postConcat(stormTint)
        }

        0 -> {
            if (!isNight) {
                val sat = scale(1.05f, intensity)
                val saturation = ColorMatrix()
                saturation.setSaturation(sat)
                matrix.postConcat(saturation)

                val rScale = scale(1.02f, intensity)
                val rOffset = 3f * intensity.coerceAtMost(1.5f)

                val sunTint = ColorMatrix(floatArrayOf(
                    rScale, 0f, 0f, 0f, rOffset,
                    0f, 1.01f, 0f, 0f, 2f * intensity.coerceAtMost(1.5f),
                    0f, 0f, 0.98f, 0f, -2f * intensity.coerceAtMost(1.5f),
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