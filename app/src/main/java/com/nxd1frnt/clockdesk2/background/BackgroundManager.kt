package com.nxd1frnt.clockdesk2.background

import android.content.Context
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import java.util.Calendar
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * BackgroundManager centralizes access to preferences related to backgrounds.
 * It handles saved URIs, blur, dim settings, and night shift.
 */
class BackgroundManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Cache for night shift factor calculations to avoid redundant computations
    private val nightShiftCache = ConcurrentHashMap<String, Float>()
    private val dimIntensityCache = ConcurrentHashMap<String, Int>()

    companion object {
        const val PREFS_NAME = "ClockDeskPrefs"
        private const val KEY_BACKGROUND_URI = "background_uri"
        private const val KEY_BACKGROUND_URIS = "background_uris"
        private const val KEY_BACKGROUND_BLUR_INTENSITY = "background_blur_intensity"
        private const val KEY_DIM_MODE = "background_dim_mode" // 0=off,1=continuous,2=dynamic
        private const val KEY_DIM_ENABLED = "background_dim_enabled" // legacy (0/1) kept for compatibility
        private const val KEY_DIM_INTENSITY = "background_dim_intensity"

        private const val KEY_ZOOM_ENABLED = "background_zoom_enabled"
        private const val KEY_NIGHT_SHIFT_ENABLED = "background_night_shift_enabled"

        const val DIM_MODE_OFF = 0
        const val DIM_MODE_CONTINUOUS = 1
        const val DIM_MODE_DYNAMIC = 2
        private const val KEY_USE_ACCENT_FONT = "use_accent_font_color"
        private const val KEY_WEATHER_ENABLED = "weather_effects_enabled"
        private const val KEY_MANUAL_WEATHER_ENABLED = "manual_weather_enabled"
        private const val KEY_MANUAL_WEATHER_TYPE = "manual_weather_type"
        private const val KEY_MANUAL_WEATHER_INTENSITY = "manual_weather_intensity"
    }

    fun getSavedBackgroundUri(): String? = prefs.getString(KEY_BACKGROUND_URI, null)
    fun setSavedBackgroundUri(uri: String?) {
        prefs.edit().apply {
            if (uri == null) remove(KEY_BACKGROUND_URI) else putString(KEY_BACKGROUND_URI, uri)
        }.apply()
    }

    fun getSavedUriSet(): MutableSet<String> = prefs.getStringSet(KEY_BACKGROUND_URIS, emptySet())?.toMutableSet() ?: mutableSetOf()
    fun addSavedUri(uri: String) {
        val set = getSavedUriSet()
        set.add(uri)
        prefs.edit().putStringSet(KEY_BACKGROUND_URIS, set).apply()
    }
    fun removeSavedUri(uri: String) {
        val set = getSavedUriSet()
        if (set.remove(uri)) prefs.edit().putStringSet(KEY_BACKGROUND_URIS, set).apply()
    }

    fun getBlurIntensity(): Int = prefs.getInt(KEY_BACKGROUND_BLUR_INTENSITY, 0)
    fun setBlurIntensity(v: Int) { prefs.edit().putInt(KEY_BACKGROUND_BLUR_INTENSITY, v).apply() }

    fun getDimMode(): Int = prefs.getInt(KEY_DIM_MODE, DIM_MODE_OFF)
    fun setDimMode(mode: Int) { prefs.edit().putInt(KEY_DIM_MODE, mode).apply() }

    fun getZoomEnabled(): Boolean = prefs.getBoolean(KEY_ZOOM_ENABLED, false)
    fun setZoomEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_ZOOM_ENABLED, enabled).apply() }

    // Legacy compatibility: previously callers used an "enabled" int. Keep this for now.
    fun isDimEnabled(): Int = prefs.getInt(KEY_DIM_ENABLED, if (getDimMode() == DIM_MODE_OFF) 0 else 1)
    fun setDimEnabled(status: Int) { prefs.edit().putInt(KEY_DIM_ENABLED, status).apply() }

    fun getDimIntensity(): Int = prefs.getInt(KEY_DIM_INTENSITY, 0)
    fun setDimIntensity(i: Int) { prefs.edit().putInt(KEY_DIM_INTENSITY, i).apply() }

    fun isWeatherEffectsEnabled(): Boolean = prefs.getBoolean(KEY_WEATHER_ENABLED, true)
    fun setWeatherEffectsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEATHER_ENABLED, enabled).apply()
    }

    // Background Night Shift Feature
    fun isNightShiftEnabled(): Boolean = prefs.getBoolean(KEY_NIGHT_SHIFT_ENABLED, false)
    fun setNightShiftEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NIGHT_SHIFT_ENABLED, enabled).apply()
    }

    // Manual Mode Toggle
    fun isManualWeatherEnabled(): Boolean = prefs.getBoolean(KEY_MANUAL_WEATHER_ENABLED, false)
    fun setManualWeatherEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MANUAL_WEATHER_ENABLED, enabled).apply()
    }

    // Saved Weather Type and Intensity
    fun getManualWeatherType(): Int = prefs.getInt(KEY_MANUAL_WEATHER_TYPE, 1) // Default RAIN (1)
    fun setManualWeatherType(typeOrdinal: Int) {
        prefs.edit().putInt(KEY_MANUAL_WEATHER_TYPE, typeOrdinal).apply()
    }

    fun getManualWeatherIntensity(): Int = prefs.getInt(KEY_MANUAL_WEATHER_INTENSITY, 100)
    fun setManualWeatherIntensity(value: Int) {
        prefs.edit().putInt(KEY_MANUAL_WEATHER_INTENSITY, value).apply()
    }

    /**
     * Compute the night shift factor (0.0 to 1.0) based on sun times.
     * Synchronized with FontManager's logic for UI harmony.
     */
    fun computeNightShiftFactor(currentTime: Date, sunTimeApi: DayTimeGetter): Float {
        if (!isNightShiftEnabled()) return 0f

        // Create a cache key based on time and sun times
        val sunrise = sunTimeApi.sunriseTime ?: run { sunTimeApi.setDefault(); sunTimeApi.sunriseTime!! }
        val sunset = sunTimeApi.sunsetTime ?: run { sunTimeApi.setDefault(); sunTimeApi.sunsetTime!! }
        
        // Round time to nearest minute for cache efficiency
        val timeKey = "${currentTime.time / 60000}_${sunrise.time / 60000}_${sunset.time / 60000}"
        
        return nightShiftCache.getOrPut(timeKey) {
            val preSunrise = Calendar.getInstance().apply { time = sunrise; add(Calendar.MINUTE, -40) }.time
            val postSunset = Calendar.getInstance().apply { time = sunset; add(Calendar.MINUTE, 30) }.time
            val fullNight = Calendar.getInstance().apply { time = postSunset; add(Calendar.MINUTE, 40) }.time

            when {
                currentTime.before(preSunrise) -> 1.0f // Full night before pre-sunrise
                currentTime.before(sunrise) -> {
                    1.0f - ((currentTime.time - preSunrise.time).toFloat() / (sunrise.time - preSunrise.time))
                }
                currentTime.before(postSunset) -> 0.0f // Daytime: no shift
                currentTime.before(fullNight) -> {
                    (currentTime.time - postSunset.time).toFloat() / (fullNight.time - postSunset.time)
                }
                else -> 1.0f // Full night after fullNight
            }.coerceIn(0f, 1f)
        }
    }

    /**
     * Compute the effective dim intensity for the provided time using sun times.
     */
    fun computeEffectiveDimIntensity(currentTime: Date, sunTimeApi: DayTimeGetter): Int {
        val mode = getDimMode()
        val userIntensity = getDimIntensity().coerceIn(0, 50)
        if (mode == DIM_MODE_OFF || userIntensity <= 0) return 0
        if (mode == DIM_MODE_CONTINUOUS) return userIntensity

        // DYNAMIC mode: mirror night transitions but produce an intensity factor 0..1
        val sunrise = sunTimeApi.sunriseTime ?: run { sunTimeApi.setDefault(); sunTimeApi.sunriseTime!! }
        val sunset = sunTimeApi.sunsetTime ?: run { sunTimeApi.setDefault(); sunTimeApi.sunsetTime!! }
        
        // Create a cache key based on time and sun times
        val timeKey = "${currentTime.time / 60000}_${sunrise.time / 60000}_${sunset.time / 60000}_$userIntensity"
        
        return dimIntensityCache.getOrPut(timeKey) {
            val preSunrise = Calendar.getInstance().apply { time = sunrise; add(Calendar.MINUTE, -40) }.time
            val postSunset = Calendar.getInstance().apply { time = sunset; add(Calendar.MINUTE, 30) }.time
            val fullNight = Calendar.getInstance().apply { time = postSunset; add(Calendar.MINUTE, 40) }.time

            val factor = when {
                currentTime.before(preSunrise) -> 1.0f // Full night before pre-sunrise
                currentTime.before(sunrise) -> {
                    val f = (currentTime.time - preSunrise.time).toFloat() / (sunrise.time - preSunrise.time)
                    (1.0f - f).coerceIn(0f, 1f) // transition to day: decrease dim
                }
                currentTime.before(postSunset) -> 0.0f // Daytime: no dim
                currentTime.before(fullNight) -> {
                    val f = (currentTime.time - postSunset.time).toFloat() / (fullNight.time - postSunset.time)
                    f.coerceIn(0f, 1f) // transition to full night: increase dim
                }
                else -> 1.0f // Full night after fullNight
            }

            (userIntensity * factor).toInt().coerceIn(0, userIntensity)
        }
    }

    fun clearDim() {
        prefs.edit().putInt(KEY_DIM_MODE, DIM_MODE_OFF).putInt(KEY_DIM_INTENSITY, 0).putInt(KEY_DIM_ENABLED, 0).apply()
    }
}