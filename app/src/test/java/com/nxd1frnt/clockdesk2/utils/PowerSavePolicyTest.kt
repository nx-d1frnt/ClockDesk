package com.nxd1frnt.clockdesk2.utils

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PowerSavePolicyTest {
    private val prefs get() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

    @Test fun inactiveModeRestoresUserSmartPixelsWithoutOtherRestrictions() {
        prefs.edit().clear().putBoolean("smart_pixels_enabled", true).commit()
        val policy = PowerSavePolicy.read(prefs, false)
        assertFalse(policy.pauseAnimations)
        assertEquals(0, policy.maxFps)
        assertFalse(policy.suspendWeather)
        assertNull(policy.brightnessOverride)
        assertFalse(policy.disableLightSensor)
        assertTrue(policy.enableSmartPixels)
    }

    @Test fun activeDefaultsApplyAllSavingRules() {
        prefs.edit().clear().commit()
        val policy = PowerSavePolicy.read(prefs, true)
        assertTrue(policy.pauseAnimations)
        assertEquals(30, policy.maxFps)
        assertTrue(policy.suspendWeather)
        assertEquals(0.01f, policy.brightnessOverride!!, 0f)
        assertTrue(policy.disableLightSensor)
        assertTrue(policy.enableSmartPixels)
    }

    @Test fun userCanDisableEachRuleIndependently() {
        prefs.edit().clear()
            .putBoolean("power_saver_disable_animations", false)
            .putBoolean("power_saver_limit_fps", false)
            .putBoolean("power_saver_disable_weather", false)
            .putBoolean("power_saver_lock_brightness", false)
            .putBoolean("power_saver_disable_light_sensor", false)
            .putBoolean("power_saver_enable_smart_pixels", false).commit()
        assertEquals(PowerSavePolicy.read(prefs, false), PowerSavePolicy.read(prefs, true))
    }
}
