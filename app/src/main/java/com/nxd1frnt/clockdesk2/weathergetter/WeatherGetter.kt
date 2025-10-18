package com.nxd1frnt.clockdesk2.weathergetter

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.nxd1frnt.clockdesk2.LocationManager
import com.nxd1frnt.clockdesk2.network.NetworkManager
import java.util.Calendar
import java.util.Date

open class WeatherGetter(private val context: Context, private val locationManager: LocationManager) {
    val requestQueue = NetworkManager.getRequestQueue(context)
    var temperature: Double? = null
    var weatherCode: Int? = null
    var isDay: Boolean? = null

    open fun fetch(latitude: Double, longitude: Double, callback: () -> Unit) {
        callback()
    }
}
