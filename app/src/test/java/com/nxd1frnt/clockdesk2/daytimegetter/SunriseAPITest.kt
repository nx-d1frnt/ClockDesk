package com.nxd1frnt.clockdesk2.daytimegetter

import android.app.Application
import com.nxd1frnt.clockdesk2.utils.LocationManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SunriseAPITest {
    private lateinit var originalZone: TimeZone
    private lateinit var api: SunriseAPI

    @Before fun setup() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val context = RuntimeEnvironment.getApplication()
        api = SunriseAPI(context, LocationManager(context, 1))
    }

    @After fun cleanup() { TimeZone.setDefault(originalZone) }

    private fun response(sunrise: String = "2020-01-02T06:00:00+00:00") = JSONObject("""
        {"results":{
            "sunrise":"$sunrise", "sunset":"2020-01-02T18:00:00+00:00",
            "solar_noon":"2020-01-02T12:00:00Z",
            "civil_twilight_begin":"2020-01-02T05:30:00+00:00",
            "civil_twilight_end":"2020-01-02T18:30:00+00:00"
        }}
    """)

    @Test fun isoOffsetsAreParsedAndTimesNormalizedToCurrentLocalDate() {
        assertTrue(api.parseSunTimes(response()))
        val sunrise = Calendar.getInstance().apply { time = api.sunriseTime!! }
        val today = Calendar.getInstance()
        assertEquals(today.get(Calendar.YEAR), sunrise.get(Calendar.YEAR))
        assertEquals(today.get(Calendar.DAY_OF_YEAR), sunrise.get(Calendar.DAY_OF_YEAR))
        assertEquals(6, sunrise.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun invalidTimesAreRejectedRatherThanCachedAsFallbacks() {
        assertFalse(api.parseSunTimes(response("invalid")))
        assertFalse(api.parseSunTimes(response("2020-01-02T19:00:00+00:00")))
    }
}
