package com.nxd1frnt.clockdesk2.desks.ui

import android.app.Application
import android.widget.LinearLayout
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WeatherDeskViewTest {
    private lateinit var view: WeatherDeskView
    private lateinit var hourly: LinearLayout

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_ClockDesk)
        context.getSharedPreferences("ClockDeskPrefs", 0).edit().clear().commit()
        WeatherGetter.updateCache(20.0, 0, true, 5.0, List(48) { 0 }, List(48) { 20.0 })
        view = WeatherDeskView(context)
        hourly = view.findViewById(R.id.weather_hourly_container)
        view.updateWeatherData()
    }

    @After fun cleanup() {
        WeatherGetter.updateCache(null, null, null, null, emptyList())
    }

    @Test fun revisitingTheForecastKeepsTheExistingCards() {
        val cards = (0 until hourly.childCount).map { hourly.getChildAt(it) }
        assertEquals(12, cards.size)
        repeat(3) {
            view.updateWeatherData()
            cards.forEachIndexed { index, card -> assertSame(card, hourly.getChildAt(index)) }
        }
    }

    @Test fun newForecastAndUnitsUpdateExistingCardsAndRemoveSurplus() {
        val first = hourly.getChildAt(0)
        WeatherGetter.cachedHourlyTemps = List(48) { 72.0 }
        view.context.getSharedPreferences("ClockDeskPrefs", 0)
            .edit().putString("temperature_unit", "fahrenheit").commit()
        view.updateWeatherData()
        assertSame(first, hourly.getChildAt(0))
        assertEquals("72°F", first.findViewById<TextView>(R.id.forecast_item_temp).text.toString())
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        WeatherGetter.cachedHourlyCodes = List(currentHour + 4) { 0 }
        view.updateWeatherData()
        assertEquals(3, hourly.childCount)
        assertSame(first, hourly.getChildAt(0))
        WeatherGetter.cachedHourlyCodes = List(48) { 0 }
        view.updateWeatherData()
        assertEquals(12, hourly.childCount)
        assertSame(first, hourly.getChildAt(0))
    }
}
