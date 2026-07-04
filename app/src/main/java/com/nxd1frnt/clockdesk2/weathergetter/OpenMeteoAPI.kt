package com.nxd1frnt.clockdesk2.weathergetter

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.utils.LocationManager
import com.nxd1frnt.clockdesk2.utils.Logger

class OpenMeteoAPI(
    context: Context,
    locationManager: LocationManager,
    private val onWeatherUpdated: () -> Unit
): WeatherGetter(context, locationManager, onWeatherUpdated) {

    override fun fetch(latitude: Double, longitude: Double) {
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,is_day,wind_speed_10m,precipitation,cloud_cover,visibility,uv_index" +
                    "&hourly=weather_code&daily=weather_code,temperature_2m_max&timezone=auto&forecast_days=5"

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

                    uvIndex = if (current.has("uv_index")) {
                        current.getDouble("uv_index")
                    } else {
                        null
                    }

                    val hourlyCodesList = mutableListOf<Int>()
                    try {
                        if (response.has("hourly")) {
                            val hourly = response.getJSONObject("hourly")
                            val hourlyCodesArray = hourly.optJSONArray("weather_code")
                            if (hourlyCodesArray != null) {
                                for (i in 0 until hourlyCodesArray.length()) {
                                    hourlyCodesList.add(hourlyCodesArray.getInt(i))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("OpenMeteoApi") { "Error parsing hourly forecast: ${e.message}" }
                    }

                    val dailyCodesList = mutableListOf<Int>()
                    val dailyMaxTempsList = mutableListOf<Double>()
                    try {
                        if (response.has("daily")) {
                            val daily = response.getJSONObject("daily")
                            val dailyCodesArray = daily.optJSONArray("weather_code")
                            val dailyMaxTempsArray = daily.optJSONArray("temperature_2m_max")
                            if (dailyCodesArray != null && dailyMaxTempsArray != null) {
                                val len = minOf(dailyCodesArray.length(), dailyMaxTempsArray.length())
                                for (i in 0 until len) {
                                    dailyCodesList.add(dailyCodesArray.getInt(i))
                                    dailyMaxTempsList.add(dailyMaxTempsArray.getDouble(i))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("OpenMeteoApi") { "Error parsing daily forecast: ${e.message}" }
                    }

                    Logger.d("OpenMeteoApi"){"Weather: Code=$weatherCode, Wind=$windSpeed km/h, " +
                            "Precipitation=$precipitation mm/h, CloudCover=$cloudCover%, Visibility=$visibility m, " +
                            "UvIndex=$uvIndex, HourlyCodesSize=${hourlyCodesList.size}, DailyCodesSize=${dailyCodesList.size}"}

                    WeatherGetter.updateCache(
                        temperature, weatherCode, isDay, windSpeed, hourlyCodesList, uvIndex,
                        dailyCodes = dailyCodesList, dailyMaxTemps = dailyMaxTempsList
                    )

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