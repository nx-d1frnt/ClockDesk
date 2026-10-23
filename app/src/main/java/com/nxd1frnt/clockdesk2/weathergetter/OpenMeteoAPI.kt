package com.nxd1frnt.clockdesk2.weathergetter

import android.content.Context
import org.json.JSONObject
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.utils.LocationManager
import com.nxd1frnt.clockdesk2.utils.Logger

class OpenMeteoAPI(
    context: Context,
    locationManager: LocationManager,
    private val onWeatherUpdated: () -> Unit
): WeatherGetter(context, locationManager, onWeatherUpdated) {

    private var activeRequest: Request<*>? = null

    override fun fetch(latitude: Double, longitude: Double) {
        activeRequest?.cancel()
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val tempUnit = prefs.getString("temperature_unit", "celsius") ?: "celsius"
        val windUnit = prefs.getString("wind_speed_unit", "kmh") ?: "kmh"
        val precipUnit = prefs.getString("precipitation_unit", "mm") ?: "mm"

        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,is_day,wind_speed_10m,precipitation,cloud_cover,visibility,uv_index" +
                    "&hourly=weather_code,temperature_2m&timezone=auto&forecast_days=2" +
                    "&temperature_unit=$tempUnit&wind_speed_unit=$windUnit&precipitation_unit=$precipUnit"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val current = response.getJSONObject("current")
                    temperature = current.getDouble("temperature_2m")
                    weatherCode = current.getInt("weather_code")
                    isDay = current.getInt("is_day") == 1

                    // Optional values may be present as JSON null (e.g. unavailable UV data).
                    windSpeed = current.optionalDouble("wind_speed_10m")
                    precipitation = current.optionalDouble("precipitation")
                    cloudCover = current.optionalDouble("cloud_cover")?.toInt()
                    visibility = current.optionalDouble("visibility")
                    uvIndex = current.optionalDouble("uv_index")

                    val hourlyCodesList = mutableListOf<Int>()
                    val hourlyTempsList = mutableListOf<Double>()
                    try {
                        if (response.has("hourly")) {
                            val hourly = response.getJSONObject("hourly")
                            val hourlyCodesArray = hourly.optJSONArray("weather_code")
                            if (hourlyCodesArray != null) {
                                for (i in 0 until hourlyCodesArray.length()) {
                                    hourlyCodesList.add(hourlyCodesArray.getInt(i))
                                }
                            }
                            val hourlyTempsArray = hourly.optJSONArray("temperature_2m")
                            if (hourlyTempsArray != null) {
                                for (i in 0 until hourlyTempsArray.length()) {
                                    hourlyTempsList.add(hourlyTempsArray.getDouble(i))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("OpenMeteoApi") { "Error parsing hourly forecast: ${e.message}" }
                    }

                    Logger.d("OpenMeteoApi"){"Weather: Code=$weatherCode, Wind=$windSpeed km/h, " +
                            "Precipitation=$precipitation mm/h, CloudCover=$cloudCover%, Visibility=$visibility m, " +
                            "UvIndex=$uvIndex, HourlyCodesSize=${hourlyCodesList.size}"}

                    WeatherGetter.updateCache(
                        temp = temperature,
                        code = weatherCode,
                        isDay = isDay,
                        wind = windSpeed,
                        hourlyCodes = hourlyCodesList,
                        hourlyTemps = hourlyTempsList,
                        uvIndex = uvIndex,
                        precipitation = precipitation,
                        cloudCover = cloudCover,
                        visibility = visibility
                    )

                    onFetchSucceeded()
                    onWeatherUpdated()

                } catch (e: Exception) {
                    Logger.e("OpenMeteoApi"){"Error parsing weather JSON ${e.message}"}
                    onFetchFailed()
                }
            },
            {
                Logger.e("OpenMeteoApi"){"Error fetching weather: ${it.message}"}
                onFetchFailed()
            }
        )
        // Every scheduled refresh must reach the server, even if HTTP cache headers allow reuse.
        request.setShouldCache(false)
        activeRequest = request
        requestQueue.add(request)
    }
}
internal fun JSONObject.optionalDouble(key: String): Double? =
    optDouble(key, Double.NaN).takeIf { it.isFinite() }
