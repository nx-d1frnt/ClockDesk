package com.nxd1frnt.clockdesk2.daytimegetter

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.nxd1frnt.clockdesk2.LocationManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SunriseAPI(private val context: Context, private val locationManager: LocationManager): DayTimeGetter(context, locationManager) {
    override fun fetch(latitude: Double, longitude: Double, callback: () -> Unit) {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time.time
        val cacheKey = "sun_times_${latitude}_${longitude}_${today}"
        val cachedSunrise = prefs.getLong("sunrise_$cacheKey", 0L)
        val cachedSunset = prefs.getLong("sunset_$cacheKey", 0L)

        if (cachedSunrise != 0L && cachedSunset != 0L) {
            sunriseTime = Date(cachedSunrise)
            sunsetTime = Date(cachedSunset)
            dawnTime = Date(prefs.getLong("dawn_$cacheKey", 0L))
            duskTime = Date(prefs.getLong("dusk_$cacheKey", 0L))
            solarNoonTime = Date(prefs.getLong("solar_noon_$cacheKey", 0L))
            Log.d("SunTimes", "Loaded cached sun times: sunrise=$sunriseTime, sunset=$sunsetTime")
            callback()
            return
        }

        val url = "https://api.sunrise-sunset.org/json?lat=$latitude&lng=$longitude&date=today&formatted=0"
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                parseSunTimes(response)
                with(prefs.edit()) {
                    putLong("sunrise_$cacheKey", sunriseTime?.time ?: 0L)
                    putLong("sunset_$cacheKey", sunsetTime?.time ?: 0L)
                    putLong("dawn_$cacheKey", dawnTime?.time ?: 0L)
                    putLong("dusk_$cacheKey", duskTime?.time ?: 0L)
                    putLong("solar_noon_$cacheKey", solarNoonTime?.time ?: 0L)
                    apply()
                }
                Log.d("SunTimes", "Fetched and cached: sunrise=$sunriseTime, sunset=$sunsetTime")
                callback()
            },
            {
                setDefault()
                Log.d("SunTimes", "Used fallback times")
                callback()
            }
        )
        //Volley.newRequestQueue(context).add(request)
        requestQueue.add(request)
    }

    private fun parseSunTimes(response: JSONObject) {
        val results = response.getJSONObject("results")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())

        // Get today's date for normalization
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        fun normalizeTime(time: String): Date? {
            return try {
                val parsed = dateFormat.parse(time.replace("Z", "+0000")) ?: return null
                val cal = Calendar.getInstance().apply { setTime(parsed) }                // Normalize to today's date                cal.set(Calendar.YEAR, today.get(Calendar.YEAR))
                cal.set(Calendar.MONTH, today.get(Calendar.MONTH))
                cal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))
                cal.time
            } catch (e: Exception) {
                Log.e("SunTimes", "Failed to parse time: $time", e)
                null
            }
        }

        sunriseTime = normalizeTime(results.getString("sunrise")) ?: run { setDefault(); sunriseTime }
        sunsetTime = normalizeTime(results.getString("sunset")) ?: run { setDefault(); sunsetTime }
        solarNoonTime = normalizeTime(results.getString("solar_noon")) ?: run { setDefault(); solarNoonTime }
        dawnTime = normalizeTime(results.getString("civil_twilight_begin")) ?: run { setDefault(); dawnTime }
        duskTime = normalizeTime(results.getString("civil_twilight_end")) ?: run { setDefault(); duskTime }

        // Validate times
        if (sunriseTime == null || sunsetTime == null || dawnTime == null || duskTime == null || solarNoonTime == null) {
            Log.w("SunTimes", "One or more sun times are null, using fallbacks")
            setDefault()
        } else if (sunriseTime!!.after(sunsetTime) || dawnTime!!.after(sunriseTime) || duskTime!!.before(sunsetTime)) {
            Log.w("SunTimes", "Invalid sun times detected: sunrise=$sunriseTime, sunset=$sunsetTime, dawn=$dawnTime, dusk=$duskTime")
            setDefault()
        } else {
            Log.d("SunTimes", "Parsed: sunrise=$sunriseTime, sunset=$sunsetTime, dawn=$dawnTime, dusk=$duskTime")
        }
    }
}
