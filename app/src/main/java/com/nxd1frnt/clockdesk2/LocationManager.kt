package com.nxd1frnt.clockdesk2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.location.LocationManager
import android.util.Log

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

    fun fetchLocation(callback: (Double, Double) -> Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w("LocationManager", "Location permission denied, using fallback: New York")
            callback(40.7128, -74.0060)
            return
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                Log.d("LocationManager", "Fetched location: lat=${location.latitude}, lon=${location.longitude}")
                callback(location.latitude, location.longitude)
            } else {
                Log.w("LocationManager", "No location available, using fallback: New York")
                callback(40.7128, -74.0060)
            }
        } catch (e: SecurityException) {
            Log.e("LocationManager", "Security exception fetching location", e)
            callback(40.7128, -74.0060)
        }
    }
}