package com.nxd1frnt.clockdesk2

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import java.text.SimpleDateFormat
import java.util.*

class OpenMeteoApi(private val context: Context, private val locationManager: LocationManager) {
val currenttemp: Double? = null
    fun fetchCurrentWeather(latitude: Double, longitude: Double, callback: (Double?, Int?, Boolean?) -> Unit) {
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code,is_day&forecast_days=1"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val current = response.getJSONObject("current")
                    val temperature = current.getDouble("temperature_2m")
                    val weatherCode = current.getInt("weather_code")
                    val isDay = current.getInt("is_day") == 1

                    Log.d("OpenMeteoApi", "Temp: $temperature°C, Code: $weatherCode, Day: $isDay")
                    callback(temperature, weatherCode, isDay)
                } catch (e: Exception) {
                    Log.e("OpenMeteoApi", "Error parsing weather JSON", e)
                    callback(null, null, null)
                }
            },
            {
                Log.e("OpenMeteoApi", "Error fetching weather")
                callback(null, null, null)
            }
        )

        Volley.newRequestQueue(context).add(request)
    }
    fun getCurrentTemperature(): Double? {
        return currenttemp
    }
    fun celsiusToFahrenheit(celsius: Double): Double {
        return celsius * 9 / 5 + 32
    }
    fun fahrenheitToCelsius(fahrenheit: Double): Double {
        return (fahrenheit - 32) * 5 / 9
    }
    fun getWeatherIconRes(weatherCode: Int, isDay: Boolean): Int {
        return when (weatherCode) {
            0 -> if (isDay) R.drawable.ic_clear_day else R.drawable.ic_clear_night

            1, 2, 3 -> if (isDay) R.drawable.ic_mostly_cloudy_day else R.drawable.ic_mostly_cloudy_night
            45, 48 -> R.drawable.ic_fog
            51, 53, 55, 56, 57 -> R.drawable.ic_drizzle
            61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_rain
            71, 73, 75, 77, 85, 86 -> R.drawable.ic_snow
            95, 96, 99 -> R.drawable.ic_thunderstorm
            else -> R.drawable.ic_weather_unknown
        }
    }
}