package com.nxd1frnt.clockdesk2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.location.LocationManager
import android.util.Log
import com.nxd1frnt.clockdesk2.utils.Logger

class LocationManager(private val context: Context, private val permissionRequestCode: Int) {
    fun loadCoordinates(callback: (Double, Double) -> Unit) {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("useManualCoordinates", false)) {
            val latitude: Any = prefs.getString("latitude", "40.7128f")?.toDouble() ?: 40.7128f
            val longitude: Any = prefs.getString("longitude", "-74.0060f")?.toDouble() ?: -74.0060f
            Logger.d("LocationManager"){"Using manual coordinates: lat=$latitude, lon=$longitude"}
            callback(latitude as Double, longitude as Double) } else { fetchLocation(callback) }
    }
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray, callback: (Double, Double) -> Unit) {
        if (requestCode == permissionRequestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation(callback)
        } else {
            callback(40.7128, -74.0060) // Fallback: New York
            Logger.d("LocationManager"){"Location permission denied, using fallback: New York"}
        }
    }

    fun fetchLocation(callback: (Double, Double) -> Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Logger.w("LocationManager"){"Location permission denied, using fallback: New York"}
            callback(40.7128, -74.0060)
            return
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                Logger.d("LocationManager"){"Fetched location: lat=${location.latitude}, lon=${location.longitude}"}
                callback(location.latitude, location.longitude)
            } else {
                Logger.w("LocationManager"){"No location available, using fallback: New York"}
                callback(40.7128, -74.0060)
            }
        } catch (e: SecurityException) {
            Logger.e("LocationManager"){"Security exception fetching location: ${e.message}"}
            callback(40.7128, -74.0060)
        }
    }
}