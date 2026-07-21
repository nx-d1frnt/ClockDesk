package com.nxd1frnt.clockdesk2.utils

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.network.NetworkManager
import java.net.URLEncoder
import java.util.Locale

data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "",
    val admin1: String = "",
    val displayName: String = ""
)

object GeocodingHelper {
    fun searchCities(
        context: Context,
        query: String,
        count: Int = 10,
        language: String = Locale.getDefault().language,
        onSuccess: (List<GeocodingResult>) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            onSuccess(emptyList())
            return
        }

        val encodedCity = try {
            URLEncoder.encode(trimmedQuery, "UTF-8")
        } catch (e: Exception) {
            trimmedQuery
        }

        val langCode = if (language.isNotBlank()) language else "en"
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=$count&language=$langCode&format=json"

        val requestQueue = NetworkManager.getRequestQueue(context)
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val results = response.optJSONArray("results")
                    val list = mutableListOf<GeocodingResult>()
                    if (results != null) {
                        for (i in 0 until results.length()) {
                            val item = results.getJSONObject(i)
                            val lat = item.getDouble("latitude")
                            val lon = item.getDouble("longitude")
                            val name = item.optString("name", trimmedQuery)
                            val country = item.optString("country", "")
                            val admin1 = item.optString("admin1", "")

                            val parts = mutableListOf<String>()
                            parts.add(name)
                            if (admin1.isNotEmpty() && !admin1.equals(name, ignoreCase = true)) {
                                parts.add(admin1)
                            }
                            if (country.isNotEmpty()) {
                                parts.add(country)
                            }
                            val displayName = parts.joinToString(", ")

                            list.add(GeocodingResult(name, lat, lon, country, admin1, displayName))
                        }
                    }
                    onSuccess(list)
                } catch (e: Exception) {
                    onError(e.message ?: "Parsing error")
                }
            },
            { error ->
                onError(error.message ?: "Network error")
            }
        )
        requestQueue.add(request)
    }

    fun geocodeCity(
        context: Context,
        cityName: String,
        onSuccess: (Double, Double, String) -> Unit,
        onError: (String) -> Unit
    ) {
        searchCities(
            context = context,
            query = cityName,
            count = 1,
            onSuccess = { list ->
                if (list.isNotEmpty()) {
                    val first = list[0]
                    onSuccess(first.latitude, first.longitude, first.displayName)
                } else {
                    onError("City not found")
                }
            },
            onError = onError
        )
    }
}
