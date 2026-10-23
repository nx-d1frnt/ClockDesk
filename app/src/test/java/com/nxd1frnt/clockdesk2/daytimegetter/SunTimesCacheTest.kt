package com.nxd1frnt.clockdesk2.daytimegetter

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SunTimesCacheTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val settings get() = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
    private val cachePrefs get() = context.getSharedPreferences("SunTimesCache", Context.MODE_PRIVATE)

    @Before fun setup() {
        settings.edit().clear().commit()
        cachePrefs.edit().clear().commit()
    }

    @Test fun legacyCacheIsRemovedWithoutRemovingUserSettings() {
        val editor = settings.edit().putString("temperature_unit", "celsius")
            .putBoolean("sunrise_enabled", true)
        listOf("sunrise", "sunset", "dawn", "dusk", "solar_noon").forEach {
            editor.putLong("${it}_sun_times_50.0_30.0_1234", 100L)
        }
        editor.commit()
        SunTimesCache(context)
        assertEquals(setOf("temperature_unit", "sunrise_enabled"), settings.all.keys)
    }

    @Test fun cacheRemainsBoundedAcrossDatesAndLocationsAndSurvivesRecreation() {
        val cache = SunTimesCache(context)
        val times = listOf(100L, 200L, 50L, 250L, 150L)
        repeat(100) { cache.write("location-and-date-$it", times) }
        assertEquals(6, cachePrefs.all.size)
        assertTrue(settings.all.isEmpty())
        assertNull(cache.read("location-and-date-98"))
        assertEquals(times, SunTimesCache(context).read("location-and-date-99"))
    }

    @Test fun incompleteCacheIsNotUsed() {
        cachePrefs.edit().putString("key", "today").putLong("sunrise", 100L)
            .putLong("sunset", 200L).commit()
        assertNull(SunTimesCache(context).read("today"))
    }
}
