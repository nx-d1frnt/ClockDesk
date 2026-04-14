package com.nxd1frnt.clockdesk2.background.plugins

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.background.BackgroundState
import com.nxd1frnt.clockdesk2.background.IBackgroundPlugin
import com.nxd1frnt.clockdesk2.network.NetworkManager
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.nxd1frnt.clockdesk2.utils.Logger
import java.text.SimpleDateFormat
import java.util.*

class BingDailyWallpaperPlugin(private val context: Context) : IBackgroundPlugin {
    override val id = "bing_daily"
    override val displayName = context.getString(R.string.bing_daily_wallpaper_name)
    override val description = context.getString(R.string.bing_daily_wallpaper_description)
    override val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>? = null

    private var callback: ((BackgroundState) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val requestQueue = NetworkManager.getRequestQueue(context)

    private var isEnabled = false
    private var refreshInterval = 3600000L // 1 hour default
    private lateinit var prefs: SharedPreferences

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "enable_bing_daily", "bing_daily_refresh_interval" -> {
                loadPrefs()
                if (isEnabled) {
                    fetchWallpaper()
                }
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isEnabled) {
                fetchWallpaper()
            }
            handler.postDelayed(this, refreshInterval)
        }
    }

    override fun init() {
        Logger.d("BingDailyPlugin") { "INIT called" }
        prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        loadPrefs()
        
        if (isEnabled) {
            handler.post(updateRunnable)
        }
    }

    private fun loadPrefs() {
        isEnabled = prefs.getBoolean("enable_bing_daily", false)
        val intervalMinutes = prefs.getInt("bing_daily_refresh_interval", 60).coerceAtLeast(15)
        refreshInterval = intervalMinutes.toLong() * 60000L
        
        if (!isEnabled) {
            callback?.invoke(BackgroundState.Disabled)
        }
    }

    private fun fetchWallpaper() {
        if (!isEnabled) return

        // Bing API endpoint for daily wallpaper info
        val url = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US"

        val request = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    // Parse the JSON response
                    val startIndex = response.indexOf("\"url\":\"")
                    if (startIndex != -1) {
                        val urlStart = startIndex + 7
                        val urlEnd = response.indexOf("\"", urlStart)
                        if (urlEnd != -1) {
                            var imageUrl = response.substring(urlStart, urlEnd)
                            // Bing returns relative URL, make it absolute
                            if (!imageUrl.startsWith("http")) {
                                imageUrl = "https://www.bing.com$imageUrl"
                            }
                            
                            Logger.d("BingDailyPlugin") { "Found wallpaper: $imageUrl" }
                            callback?.invoke(BackgroundState.Available(imageUrl))
                        } else {
                            callback?.invoke(BackgroundState.Unavailable)
                        }
                    } else {
                        callback?.invoke(BackgroundState.Unavailable)
                    }
                } catch (e: Exception) {
                    Logger.e("BingDailyPlugin") { "Parse error: ${e.message}" }
                    callback?.invoke(BackgroundState.Unavailable)
                }
            },
            { error ->
                Logger.e("BingDailyPlugin") { "Network error: ${error.message}" }
                callback?.invoke(BackgroundState.Unavailable)
            }
        )

        requestQueue.add(request)
    }

    override fun setCallback(callback: (BackgroundState) -> Unit) {
        this.callback = callback
    }

    override fun destroy() {
        Logger.d("BingDailyPlugin") { "DESTROY called" }
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        handler.removeCallbacks(updateRunnable)
    }
}
