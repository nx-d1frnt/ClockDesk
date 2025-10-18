package com.nxd1frnt.clockdesk2.weathergetter

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.nxd1frnt.clockdesk2.LocationManager

class OpenMeteoAPI(private val context: Context, private val locationManager: LocationManager): WeatherGetter(context, locationManager) {
    override fun fetch(latitude: Double, longitude: Double, callback: () -> Unit) {
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code,is_day&forecast_days=1"

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val current = response.getJSONObject("current")
                    temperature = current.getDouble("temperature_2m")
                    weatherCode = current.getInt("weather_code")
                    isDay = current.getInt("is_day") == 1

                    Log.d("OpenMeteoApi", "Temp: $temperature°C, Code: $weatherCode, Day: $isDay")
                    callback()
                } catch (e: Exception) {
                    Log.e("OpenMeteoApi", "Error parsing weather JSON", e)
                    callback()
                }
            },
            {
                Log.e("OpenMeteoApi", "Error fetching weather")
                callback()
            }
        )

        //Volley.newRequestQueue(context).add(request)
        requestQueue.add(request) // Use shared request queue
    }
}
