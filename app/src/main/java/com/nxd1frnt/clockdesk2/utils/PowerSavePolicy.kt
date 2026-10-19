package com.nxd1frnt.clockdesk2.utils

import android.content.SharedPreferences

/** Effective rules shared by the activity, screensaver and background updaters. */
data class PowerSavePolicy(
    val pauseAnimations: Boolean,
    val maxFps: Int,
    val suspendWeather: Boolean,
    val brightnessOverride: Float?,
    val disableLightSensor: Boolean,
    val enableSmartPixels: Boolean
) {
    companion object {
        fun read(prefs: SharedPreferences, enabled: Boolean) = PowerSavePolicy(
            pauseAnimations = enabled && prefs.getBoolean("power_saver_disable_animations", true),
            maxFps = if (enabled && prefs.getBoolean("power_saver_limit_fps", true))
                prefs.getInt("power_saver_fps_limit_value", 30).coerceIn(1, 120) else 0,
            suspendWeather = enabled && prefs.getBoolean("power_saver_disable_weather", true),
            brightnessOverride = if (enabled && prefs.getBoolean("power_saver_lock_brightness", true))
                (prefs.getInt("power_saver_brightness_level", 1) / 100f).coerceIn(0.01f, 1f) else null,
            disableLightSensor = enabled && prefs.getBoolean("power_saver_disable_light_sensor", true),
            enableSmartPixels = prefs.getBoolean("smart_pixels_enabled", false) ||
                (enabled && prefs.getBoolean("power_saver_enable_smart_pixels", true))
        )
    }
}
