package com.nxd1frnt.clockdesk2.musicgetter

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.nxd1frnt.clockdesk2.network.NetworkManager
import org.json.JSONException
import javax.security.auth.callback.Callback

open class MusicGetter(private val context: Context, private val callback: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    val requestQueue = NetworkManager.getRequestQueue(context)
    var enabled = false
    var currentTrack: String? = null
    var currentAlbumArtUrl: String? = null
    var userPreselectedBackgroundUri: String? = null
    var wasGradientBackgroundActive = false
    var refreshInterval: Long = 30000L
    private var isPowerSavingMode = false
    private var isPlayingNow = true
    val updateRunnable = object : Runnable {
        override fun run() {
            fetch()
            context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE).also { prefs ->
                val refreshInterval = when {
                    isPowerSavingMode -> 30000L // 30 seconds
                    //isPlayingNow -> 10000L
                    else -> prefs.getInt("last_fm_refresh_interval", 30) * 1000L
                }
                Log.d("MusicGetter", "Refresh interval set to $refreshInterval ms")
            }
            handler.postDelayed(this, refreshInterval)
        }
    }

    open fun startUpdates() {
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        callback()
    }

    fun stopUpdates() {
        handler.removeCallbacks(updateRunnable)
    }

    open fun fetch() {
        currentTrack = null
        currentAlbumArtUrl = null
        enabled = false
    }

    fun setPowerSavingMode(enabled: Boolean){
        if (isPowerSavingMode == enabled) return
        isPowerSavingMode = enabled
        Log.d("MusicGetter", "Power saving mode set to $enabled")
    }
}