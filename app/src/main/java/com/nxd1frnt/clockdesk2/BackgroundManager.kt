package com.nxd1frnt.clockdesk2

import android.content.Context
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import java.util.Calendar
import java.util.Date

/**
 * BackgroundManager centralizes access to preferences related to backgrounds.
 * It handles saved URIs, blur and dim settings.
 */
class BackgroundManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "ClockDeskPrefs"
        private const val KEY_BACKGROUND_URI = "background_uri"
        private const val KEY_BACKGROUND_URIS = "background_uris"
        private const val KEY_BACKGROUND_BLUR_INTENSITY = "background_blur_intensity"
        private const val KEY_DIM_MODE = "background_dim_mode" // 0=off,1=continuous,2=dynamic
        private const val KEY_DIM_ENABLED = "background_dim_enabled" // legacy (0/1) kept for compatibility
        private const val KEY_DIM_INTENSITY = "background_dim_intensity"

        private const val KEY_ZOOM_ENABLED = "background_zoom_enabled"

        const val DIM_MODE_OFF = 0
        const val DIM_MODE_CONTINUOUS = 1
        const val DIM_MODE_DYNAMIC = 2
        private const val KEY_USE_ACCENT_FONT = "use_accent_font_color"
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

    /**
     * Compute the effective dim intensity for the provided time using sun times.
     * Returns an integer in the same scale as user-configured intensity (0..max configured value).
     * - If mode is OFF -> 0
     * - If CONTINUOUS -> userIntensity
     * - If DYNAMIC -> compute factor based on sunrise/sunset and scale userIntensity by that factor
     * The transition windows mirror FontManager's night-shift logic.
     */
    fun computeEffectiveDimIntensity(currentTime: Date, sunTimeApi: DayTimeGetter): Int {
        val mode = getDimMode()
        val userIntensity = getDimIntensity().coerceIn(0, 50)
        if (mode == DIM_MODE_OFF || userIntensity <= 0) return 0
        if (mode == DIM_MODE_CONTINUOUS) return userIntensity

        // DYNAMIC mode: mirror FontManager's night transitions but produce an intensity factor 0..1
        // Ensure sun times are available; SunTimeApi may have fallback setters like in FontManager usage
        val sunrise = sunTimeApi.sunriseTime ?: run { sunTimeApi.setDefault(); sunTimeApi.sunriseTime!! }
        val sunset = sunTimeApi.sunsetTime ?: run { sunTimeApi.setDefault(); sunTimeApi.sunsetTime!! }

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

        val result = (userIntensity * factor).toInt().coerceIn(0, userIntensity)
        return result
    }

    fun clearDim() {
        prefs.edit().putInt(KEY_DIM_MODE, DIM_MODE_OFF).putInt(KEY_DIM_INTENSITY, 0).putInt(KEY_DIM_ENABLED, 0).apply()
    }
}
