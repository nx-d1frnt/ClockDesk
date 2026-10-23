package com.nxd1frnt.clockdesk2.daytimegetter

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.utils.LocationManager
import com.nxd1frnt.clockdesk2.utils.Logger
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SunriseAPI(private val context: Context, private val locationManager: LocationManager): DayTimeGetter(context, locationManager) {
    private val cache = SunTimesCache(context)

    override fun fetch(latitude: Double, longitude: Double, callback: () -> Unit) {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time.time
        val cacheKey = "sun_times_${latitude}_${longitude}_${today}"
        val cached = cache.read(cacheKey)

        if (cached != null) {
            sunriseTime = Date(cached[0])
            sunsetTime = Date(cached[1])
            dawnTime = Date(cached[2])
            duskTime = Date(cached[3])
            solarNoonTime = Date(cached[4])
            Logger.d("SunTimes"){"Loaded cached sun times: sunrise=$sunriseTime, sunset=$sunsetTime"}
            callback()
            return
        }

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(today))
        val url = "https://api.sunrise-sunset.org/json?lat=$latitude&lng=$longitude&date=$date&formatted=0"
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    if (response.optString("status") == "OK" && parseSunTimes(response)) {
                        cache.write(cacheKey, listOf(
                            sunriseTime!!.time, sunsetTime!!.time, dawnTime!!.time,
                            duskTime!!.time, solarNoonTime!!.time
                        ))
                        Logger.d("SunTimes"){"Fetched and cached: sunrise=$sunriseTime, sunset=$sunsetTime"}
                    } else {
                        setDefault()
                    }
                } catch (e: Exception) {
                    Logger.e("SunTimes"){"Failed to parse sun times: ${e.message}"}
                    setDefault()
                }
                callback()
            },
            {
                setDefault()
                Logger.d("SunTimes"){"Used fallback times"}
                callback()
            }
        )
        request.setShouldCache(false)
        requestQueue.add(request)
    }

    internal fun parseSunTimes(response: JSONObject): Boolean {
        val results = response.getJSONObject("results")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { isLenient = false }

        // Get today's date for normalization
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        fun normalizeTime(time: String): Date? {
            return try {
                val parsed = dateFormat.parse(time) ?: return null
                val cal = Calendar.getInstance().apply { setTime(parsed) }
                // Normalize to today's local date.
                cal.set(Calendar.YEAR, today.get(Calendar.YEAR))
                cal.set(Calendar.MONTH, today.get(Calendar.MONTH))
                cal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))
                cal.time
            } catch (e: Exception) {
                Logger.e("SunTimes"){"Failed to parse time: $time ${e.message}"}
                null
            }
        }

        sunriseTime = normalizeTime(results.getString("sunrise"))
        sunsetTime = normalizeTime(results.getString("sunset"))
        solarNoonTime = normalizeTime(results.getString("solar_noon"))
        dawnTime = normalizeTime(results.getString("civil_twilight_begin"))
        duskTime = normalizeTime(results.getString("civil_twilight_end"))

        // Validate times
        if (sunriseTime == null || sunsetTime == null || dawnTime == null || duskTime == null || solarNoonTime == null) {
            Logger.w("SunTimes"){"One or more sun times are null, using fallbacks"}
            return false
        } else if (sunriseTime!!.after(sunsetTime) || dawnTime!!.after(sunriseTime) || duskTime!!.before(sunsetTime)) {
            Logger.w("SunTimes"){"Invalid sun times detected: sunrise=$sunriseTime, sunset=$sunsetTime, dawn=$dawnTime, dusk=$duskTime"}
            return false
        } else {
            Logger.d("SunTimes"){"Parsed: sunrise=$sunriseTime, sunset=$sunsetTime, dawn=$dawnTime, dusk=$duskTime"}
            return true
        }
    }
}
