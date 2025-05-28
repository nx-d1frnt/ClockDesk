package com.nxd1frnt.clockdesk2

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class SunTimeApi(private val context: Context, private val locationManager: LocationManager) {
    var sunriseTime: Date? = null
    var sunsetTime: Date? = null
    var dawnTime: Date? = null
    var duskTime: Date? = null
    var solarNoonTime: Date? = null
    fun fetchSunTimes(latitude: Double, longitude: Double, callback: () -> Unit) {
        val url = "https://api.sunrise-sunset.org/json?lat=$latitude&lng=$longitude&date=today&formatted=0"
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                parseSunTimes(response)
                Log.d("SunTimes", "Fetched: sunrise=$sunriseTime, sunset=$sunsetTime, dawn=$dawnTime, dusk=$duskTime")
                callback()
            },
            {
                setFallbackTimes()
                Log.d("SunTimes", "Used fallback times")
                callback()
            }
        )
        Volley.newRequestQueue(context).add(request)
    }

    private fun parseSunTimes(response: JSONObject) {
        val results = response.getJSONObject("results")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
        fun normalizeTime(time: String): String = time.replace("Z", "+0000")
        sunriseTime = dateFormat.parse(normalizeTime(results.getString("sunrise")))
        sunsetTime = dateFormat.parse(normalizeTime(results.getString("sunset")))
        solarNoonTime = dateFormat.parse(normalizeTime(results.getString("solar_noon")))
        dawnTime = dateFormat.parse(normalizeTime(results.getString("civil_twilight_begin")))
        duskTime = dateFormat.parse(normalizeTime(results.getString("civil_twilight_end")))
    }

    fun setFallbackTimes() {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        sunriseTime = dateFormat.parse("06:00")?.apply {
            time = today.timeInMillis + (6 * 60 * 60 * 1000)
        } ?: Date()
        sunsetTime = dateFormat.parse("18:00")?.apply {
            time = today.timeInMillis + (18 * 60 * 60 * 1000)
        } ?: Date()
        dawnTime = dateFormat.parse("05:30")?.apply {
            time = today.timeInMillis + (5 * 60 + 30) * 60 * 1000
        } ?: Date()
        solarNoonTime = dateFormat.parse("12:00")?.apply {
            time = today.timeInMillis + (12 * 60 * 60 * 1000)
        } ?: Date()
        duskTime = dateFormat.parse("18:30")?.apply {
            time = today.timeInMillis + (18 * 60 + 30) * 60 * 1000
        } ?: Date()
    }
}