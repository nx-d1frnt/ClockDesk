package com.nxd1frnt.clockdesk2.music.plugins

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.music.IMusicPlugin
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.music.PluginState
import com.nxd1frnt.clockdesk2.music.ui.LastFmSettingsFragment
import com.nxd1frnt.clockdesk2.network.NetworkManager
import org.json.JSONException

class LastFmPlugin(private val context: Context) : IMusicPlugin {
    override val id = "lastfm"
    override val displayName = context.getString(R.string.lastfm_plugin_name)
    override val description = context.getString(R.string.lastfm_plugin_description)

    override val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>? =
        LastFmSettingsFragment::class.java

    private val GENERIC_LASTFM_IMAGE_IDS = setOf(
        "2a96cbd8b46e442fc41c2b86b821562f"
    )

    private var callback: ((PluginState) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val requestQueue = NetworkManager.getRequestQueue(context)

    private var apiKey: String = ""
    private var username: String = ""
    private var isEnabled = false
    private var refreshInterval = 30000L

    //unique instanceid for debugging
    private val instanceId = System.identityHashCode(this)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "lastfm_username", "lastfm_api_key", "enable_lastfm" -> {
                Log.d("LastFmPlugin", "[$instanceId] Critical settings changed, restarting loop...")
                loadPrefs()
                restartLoop()
            }
            "last_fm_refresh_interval" -> {
                loadPrefs()
                Log.d("LastFmPlugin", "[$instanceId] Interval updated to ${refreshInterval / 1000}s")
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isEnabled) {
                fetch()
            }
            val delay = refreshInterval.coerceAtLeast(5000L)
            handler.postDelayed(this, delay)
        }
    }

    override fun init() {
        Log.d("LastFmPlugin", "[$instanceId] INIT called")
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        loadPrefs()
        restartLoop()
    }

    private fun loadPrefs() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        username = prefs.getString("lastfm_username", "") ?: ""
        apiKey = prefs.getString("lastfm_api_key", "") ?: ""
        isEnabled = prefs.getBoolean("enable_lastfm", false)

        val rawInterval = prefs.getInt("last_fm_refresh_interval", 30)
        refreshInterval = rawInterval.toLong() * 1000L

        // Log.v("LastFmPlugin", "[$instanceId] Loaded prefs: enabled=$isEnabled, interval=$rawInterval")

        if (apiKey.isEmpty() || username.isEmpty()) {
            if (isEnabled) Log.w("LastFmPlugin", "[$instanceId] Missing credentials")
            isEnabled = false
            callback?.invoke(PluginState.Disabled)
        }
    }

    private fun restartLoop() {
        handler.removeCallbacks(updateRunnable)
        if (isEnabled) {
            handler.post(updateRunnable)
        } else {
            callback?.invoke(PluginState.Disabled)
        }
    }

    private fun fetch() {
        val url = Uri.parse("https://ws.audioscrobbler.com/2.0/")
            .buildUpon()
            .appendQueryParameter("method", "user.getrecenttracks")
            .appendQueryParameter("user", username)
            .appendQueryParameter("api_key", apiKey)
            .appendQueryParameter("format", "json")
            .appendQueryParameter("limit", "1")
            .build()
            .toString()

        Log.v("LastFmPlugin", "[$instanceId] Ping Last.fm")

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    val recentTracks = response.optJSONObject("recenttracks")
                    val trackArray = recentTracks?.optJSONArray("track")

                    if (trackArray != null && trackArray.length() > 0) {
                        val trackObj = trackArray.getJSONObject(0)
                        val attr = trackObj.optJSONObject("@attr")

                        if (attr != null && attr.optString("nowplaying") == "true") {
                            val artist = trackObj.getJSONObject("artist").getString("#text")
                            val title = trackObj.getString("name")
                            val album = trackObj.optJSONObject("album")?.optString("#text")

                            var imageUrl = trackObj.optJSONArray("image")?.let { arr ->
                                (0 until arr.length())
                                    .map { arr.getJSONObject(it) }
                                    .firstOrNull { it.getString("size") == "extralarge" }
                                    ?.getString("#text")
                            }

                            if (!imageUrl.isNullOrEmpty() && GENERIC_LASTFM_IMAGE_IDS.any { imageUrl!!.contains(it) }) {
                                imageUrl = null
                            }

                            val track = MusicTrack(
                                title = title,
                                artist = artist,
                                album = album,
                                artworkUrl = imageUrl,
                                sourcePackageName = "com.lastfm"
                            )
                            callback?.invoke(PluginState.Playing(track))
                        } else {
                            callback?.invoke(PluginState.Idle)
                        }
                    } else {
                        callback?.invoke(PluginState.Idle)
                    }
                } catch (e: JSONException) {
                    Log.e("LastFmPlugin", "[$instanceId] Parse error", e)
                    callback?.invoke(PluginState.Idle)
                }
            },
            { error ->
                Log.e("LastFmPlugin", "[$instanceId] Network error: ${error.message}")
                callback?.invoke(PluginState.Idle)
            }
        )

        request.retryPolicy = DefaultRetryPolicy(
            10000,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        requestQueue.add(request)
    }

    override fun setCallback(callback: (PluginState) -> Unit) {
        this.callback = callback
    }

    override fun destroy() {
        Log.d("LastFmPlugin", "[$instanceId] DESTROY called")
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        handler.removeCallbacks(updateRunnable)
    }
}