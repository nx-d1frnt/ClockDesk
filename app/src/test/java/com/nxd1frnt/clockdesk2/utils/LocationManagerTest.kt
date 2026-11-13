package com.nxd1frnt.clockdesk2.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager as AndroidLocationManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocationManagerTest {

    @Test
    fun fetchLocationUsesNetworkProviderWhenOnlyCoarsePermissionGranted() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val systemLm = app.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
        val networkLoc = Location(AndroidLocationManager.NETWORK_PROVIDER).apply {
            latitude = 50.4501
            longitude = 30.5234
        }
        shadowOf(systemLm).setLastKnownLocation(AndroidLocationManager.NETWORK_PROVIDER, networkLoc)

        var gotLat = 0.0
        var gotLon = 0.0
        LocationManager(app, 100).onRequestPermissionsResult(
            100,
            intArrayOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_GRANTED)
        ) { lat, lon ->
            gotLat = lat
            gotLon = lon
        }

        assertEquals(50.4501, gotLat, 0.0001)
        assertEquals(30.5234, gotLon, 0.0001)
    }
}
