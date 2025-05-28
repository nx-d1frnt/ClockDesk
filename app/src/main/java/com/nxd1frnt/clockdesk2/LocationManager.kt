package com.nxd1frnt.clockdesk2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.location.LocationManager

class LocationManager(private val context: Context, private val permissionRequestCode: Int) {
    fun loadCoordinates(callback: (Double, Double) -> Unit) {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("useManualCoordinates", false)) {
            val latitude = prefs.getFloat("latitude", 40.7128f).toDouble()
            val longitude = prefs.getFloat("longitude", -74.0060f).toDouble()
            callback(latitude, longitude) } else { fetchLocation(callback) }
    }
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray, callback: (Double, Double) -> Unit) {
        if (requestCode == permissionRequestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation(callback)
        } else {
            callback(40.7128, -74.0060) // Fallback: New York
        }
    }

    private fun fetchLocation(callback: (Double, Double) -> Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback(40.7128, -74.0060)
            return
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                callback(location.latitude, location.longitude)
            } else {
                callback(40.7128, -74.0060)
            }
        } catch (e: SecurityException) {
            callback(40.7128, -74.0060)
        }
    }
}