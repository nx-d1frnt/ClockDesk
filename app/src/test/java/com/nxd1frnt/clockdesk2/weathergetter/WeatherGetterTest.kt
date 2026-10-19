package com.nxd1frnt.clockdesk2.weathergetter

import android.app.Application
import android.content.Context
import android.os.Looper
import com.nxd1frnt.clockdesk2.utils.LocationManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class WeatherGetterTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
    private lateinit var getter: CountingWeather

    private class CountingWeather(context: Context) : WeatherGetter(context, LocationManager(context, 1), {}) {
        var requests = 0
        override fun fetch(latitude: Double, longitude: Double) { requests++ }
    }

    @Before fun setup() {
        prefs.edit().clear().putString("weather_refresh_interval", "30").commit()
        getter = CountingWeather(context)
    }

    @After fun cleanup() { if (::getter.isInitialized) getter.stopUpdates() }

    private fun idle(minutes: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(minutes))

    @Test fun repeatedStartsAndResumeKeepOriginalDeadline() {
        getter.startUpdates(50.0, 30.0)
        idle()
        idle(10)
        getter.stopUpdates()
        getter.startUpdates(50.0, 30.0)
        getter.startUpdates(50.0, 30.0)
        idle()
        assertEquals(1, getter.requests)
        idle(20)
        assertEquals(2, getter.requests)
    }

    @Test fun suspendedWeatherDoesNotFetchEvenAfterStartOrUnitChange() {
        getter.onPowerSaveModeChanged(true)
        getter.startUpdates(50.0, 30.0)
        prefs.edit().putString("temperature_unit", "fahrenheit").commit()
        idle(120)
        assertEquals(0, getter.requests)
        getter.onPowerSaveModeChanged(false)
        idle()
        assertEquals(1, getter.requests)
    }

    @Test fun powerSavingReschedulesExistingDeadlineWithoutFetching() {
        prefs.edit().putBoolean("power_saver_disable_weather", false).commit()
        getter.startUpdates(50.0, 30.0)
        idle()
        idle(10)
        getter.onPowerSaveModeChanged(true)
        idle(20)
        assertEquals(1, getter.requests)
        idle(30)
        assertEquals(2, getter.requests)
    }

    @Test fun changedCoordinatesOrUnitsInvalidateRequestKey() {
        getter.startUpdates(50.0, 30.0)
        idle()
        getter.startUpdates(51.0, 30.0)
        idle()
        prefs.edit().putString("wind_speed_unit", "mph").commit()
        idle()
        assertEquals(3, getter.requests)
    }

    @Test fun stoppingPreventsPolicyChangesFromRestartingUpdates() {
        getter.startUpdates(50.0, 30.0)
        idle()
        getter.stopUpdates()
        getter.onPowerSaveModeChanged(true)
        getter.onPowerSaveModeChanged(false)
        idle(120)
        assertEquals(1, getter.requests)
    }
}
