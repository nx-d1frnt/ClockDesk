package com.nxd1frnt.clockdesk2.weathergetter

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.LocationManager
import com.nxd1frnt.clockdesk2.utils.Logger

class OpenMeteoAPI(
    context: Context,
    locationManager: LocationManager,
    private val onWeatherUpdated: () -> Unit
): WeatherGetter(context, locationManager, onWeatherUpdated) {

    override fun fetch(latitude: Double, longitude: Double) {
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,is_day,wind_speed_10m,precipitation,cloud_cover,visibility" +
                    "&forecast_days=1"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val current = response.getJSONObject("current")
                    temperature = current.getDouble("temperature_2m")
                    weatherCode = current.getInt("weather_code")
                    isDay = current.getInt("is_day") == 1

                    windSpeed = if (current.has("wind_speed_10m")) {
                        current.getDouble("wind_speed_10m")
                    } else {
                        0.0
                    }

                    precipitation = if (current.has("precipitation")) {
                        current.getDouble("precipitation")
                    } else {
                        null
                    }

                    cloudCover = if (current.has("cloud_cover")) {
                        current.getInt("cloud_cover")
                    } else {
                        null
                    }

                    visibility = if (current.has("visibility")) {
                        current.getDouble("visibility")
                    } else {
                        null
                    }

                    Logger.d("OpenMeteoApi"){"Weather: Code=$weatherCode, Wind=$windSpeed km/h, " +
                            "Precipitation=$precipitation mm/h, CloudCover=$cloudCover%, Visibility=$visibility m"}

                    onWeatherUpdated()

                } catch (e: Exception) {
                    Logger.e("OpenMeteoApi"){"Error parsing weather JSON ${e.message}"}
                }
            },
            {
                Logger.e("OpenMeteoApi"){"Error fetching weather: ${it.message}"}
            }
        )
        requestQueue.add(request)
    }
}