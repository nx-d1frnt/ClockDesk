package com.nxd1frnt.clockdesk2.utils

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.network.NetworkManager
import java.net.URLEncoder

object GeocodingHelper {
    fun geocodeCity(
        context: Context,
        cityName: String,
        onSuccess: (Double, Double, String) -> Unit,
        onError: (String) -> Unit
    ) {
        val encodedCity = try {
            URLEncoder.encode(cityName, "UTF-8")
        } catch (e: Exception) {
            cityName
        }
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json"

        val requestQueue = NetworkManager.getRequestQueue(context)
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val results = response.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val firstResult = results.getJSONObject(0)
                        val lat = firstResult.getDouble("latitude")
                        val lon = firstResult.getDouble("longitude")
                        val name = firstResult.optString("name", cityName)
                        val country = firstResult.optString("country", "")
                        val resolvedName = if (country.isNotEmpty()) "$name, $country" else name
                        onSuccess(lat, lon, resolvedName)
                    } else {
                        onError("City not found")
                    }
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
}
