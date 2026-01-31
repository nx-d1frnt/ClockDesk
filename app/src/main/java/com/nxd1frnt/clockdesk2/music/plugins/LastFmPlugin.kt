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
import com.nxd1frnt.clockdesk2.utils.Logger
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

    private var userRefreshInterval = 30000L // User-defined refresh interval
    private var dynamicRefreshEnabled = false // Dynamic refresh rate toggle
    private var activeRefreshInterval = 5000L // Interval when music is playing

    private var isPlaying = false

    private var currentAlbumArtUrl: String? = null

    //unique instanceid for debugging
    private lateinit var prefs: SharedPreferences
    private val instanceId = System.identityHashCode(this)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "lastfm_username", "lastfm_api_key", "enable_lastfm" -> {
                Logger.d("LastFmPlugin"){"[$instanceId] Critical settings changed, restarting loop..."}
                loadPrefs()
                restartLoop(debounce = true)
            }
            "last_fm_refresh_interval" -> {
                loadPrefs()
                Logger.d("LastFmPlugin"){"[$instanceId] Interval updated to ${userRefreshInterval / 1000}s"}
            }
            "dynamic_refresh_threshold", "last_fm_dynamic_refresh_rate" -> {
                loadPrefs()
                Logger.d("LastFmPlugin"){"[$instanceId] Dynamic refresh settings updated"}
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isEnabled) {
                fetch()
            }

            val nextInterval = if (dynamicRefreshEnabled && isPlaying) {
                activeRefreshInterval
            } else {
                userRefreshInterval
            }

            val finalDelay = nextInterval.coerceAtLeast(5000L)

            handler.postDelayed(this, finalDelay)
        }
    }

    override fun init() {
        Logger.d("LastFmPlugin"){"[$instanceId] INIT called"}
        prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        loadPrefs()
        restartLoop(debounce = false)
    }

        private fun isGenericLastFmImage(url: String): Boolean {
        return GENERIC_LASTFM_IMAGE_IDS.any { url.contains(it) } // Check if URL contains any known generic ID
}

     private fun fetchAlbumArtFromThirdParty(artist: String, songName: String, onResult: (String?) -> Unit) {
        val searchTerm = "$artist - $songName"
        val url = Uri.parse("https://itunes.apple.com/search")
            .buildUpon()
            .appendQueryParameter("term", searchTerm)
            .appendQueryParameter("entity", "song") // Search for songs
            .appendQueryParameter("limit", "1")
            .build()
            .toString()

        Logger.d("LastFmPlugin"){"Fallback: Requesting from iTunes: $url"}

        val itunesRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val resultCount = response.optInt("resultCount", 0)
                    if (resultCount > 0) {
                        val results = response.getJSONArray("results")
                        val firstResult = results.getJSONObject(0)

                        // Try to get 600x600 art, fall back to 100x100
                        var artUrl = firstResult.optString("artworkUrl600")
                        if (artUrl.isEmpty()) {
                            artUrl = firstResult.optString("artworkUrl100")
                        }

                        if (artUrl.isNotEmpty()) {
                            // Replace 100x100 with 600x600 for higher res
                            val highResArtUrl = artUrl.replace("100x100", "600x600")
                            Logger.d("LastFmPlugin"){"Fallback: Found iTunes art: $highResArtUrl"}
                            onResult(highResArtUrl)
                        } else {
                            onResult(null)
                        }
                    } else {
                        Logger.d("LastFmPlugin"){"Fallback: iTunes found no results for '$searchTerm'"}
                        onResult(null)
                    }
                } catch (e: JSONException) {
                    Logger.e("LastFmPlugin"){"Fallback: iTunes JSON parsing error${e.message}"}
                    onResult(null)
                }
            },
            { error ->
                Logger.e("LastFmPlugin"){"Fallback: iTunes Volley error: ${error.message}"}
                onResult(null)
            }
        )
        requestQueue.add(itunesRequest)
    }

    private fun loadPrefs() {
        username = prefs.getString("lastfm_username", "") ?: ""
        apiKey = prefs.getString("lastfm_api_key", "") ?: ""

        // Считываем основные настройки
        val switchEnabled = prefs.getBoolean("enable_lastfm", false)
        val rawInterval = prefs.getInt("last_fm_refresh_interval", 30)
        val dynamicRefreshThreshold = prefs.getInt("dynamic_refresh_threshold", 5)

        dynamicRefreshEnabled = prefs.getBoolean("last_fm_dynamic_refresh_rate", false)

        userRefreshInterval = rawInterval.toLong() * 1000L
        activeRefreshInterval = dynamicRefreshThreshold.toLong() * 1000L

        if (switchEnabled) {
            if (apiKey.isEmpty() || username.isEmpty()) {
                if (isEnabled) Logger.w("LastFmPlugin"){"[$instanceId] Enabled but credentials missing"}
                isEnabled = false
                callback?.invoke(PluginState.Disabled)
            } else {
                isEnabled = true
            }
        } else {
            isEnabled = false
            callback?.invoke(PluginState.Disabled)
        }
    }

    private fun restartLoop(debounce: Boolean) {
        handler.removeCallbacks(updateRunnable)

        if (isEnabled) {
            if (debounce) {
                handler.postDelayed(updateRunnable, 1000)
            } else {
                handler.post(updateRunnable)
            }
        } else {
            isPlaying = false
            callback?.invoke(PluginState.Disabled)
        }
    }

    private fun fetch() {
        if (!isEnabled || apiKey.isEmpty() || username.isEmpty()) return

        Logger.d("LastFmPlugin"){"[$instanceId] Fetching recent tracks for user: $username"}
        val url = Uri.parse("https://ws.audioscrobbler.com/2.0/")
            .buildUpon()
            .appendQueryParameter("method", "user.getrecenttracks")
            .appendQueryParameter("user", username)
            .appendQueryParameter("api_key", apiKey)
            .appendQueryParameter("format", "json")
            .appendQueryParameter("limit", "1")
            .build()
            .toString()

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    val recentTracks = response.optJSONObject("recenttracks")
                    val trackArray = recentTracks?.optJSONArray("track")

                    if (trackArray != null && trackArray.length() > 0) {
                        val trackObj = trackArray.getJSONObject(0)
                        val attr = trackObj.optJSONObject("@attr")

                        val isNowPlaying = attr != null && attr.optString("nowplaying") == "true"

                        this.isPlaying = isNowPlaying

                        if (isNowPlaying) {
                            val artist = trackObj.getJSONObject("artist").getString("#text")
                            val title = trackObj.getString("name")
                            val album = trackObj.optJSONObject("album")?.optString("#text")

                            var imageUrl = trackObj.optJSONArray("image")?.let { arr ->
                                (0 until arr.length())
                                    .map { arr.getJSONObject(it) }
                                    .firstOrNull { it.getString("size") == "extralarge" }
                                    ?.getString("#text")
                            }

                        val isGeneric = !imageUrl.isNullOrEmpty() && isGenericLastFmImage(imageUrl) // Check for generic image

                        if (!imageUrl.isNullOrEmpty() && !isGeneric) {
                            // We have a good, non-generic URL from Last.fm
                            Logger.d("LastFmPlugin"){"[$instanceId] Got Last.fm art: $imageUrl"}
                            currentAlbumArtUrl = imageUrl

                            Logger.d("LastFmPlugin"){"[$instanceId] Now Playing: $artist - $title"}
                            Logger.d("LastFmPlugin"){"[$instanceId] Artwork URL: ${imageUrl ?: "none"}"}

                            val track = MusicTrack(
                                title = title,
                                artist = artist,
                                album = album,
                                artworkUrl = imageUrl,
                                sourcePackageName = "com.lastfm"
                            )
                            callback?.invoke(PluginState.Playing(track))
                        } else {
                            // Generic URL or no URL. Try to fetch from iTunes.
                            // The fallback will invoke the plugin callback once it has a result.
                            Logger.d("LastFmPlugin"){"[$instanceId] Last.fm art is generic or empty. Fetching from fallback."}
                            fetchAlbumArtFromThirdParty(artist, title) { artUrl ->
                                if (!artUrl.isNullOrEmpty()) {
                                    Logger.d("LastFmPlugin"){"[$instanceId] Fallback art found: $artUrl"}
                                    currentAlbumArtUrl = artUrl
                                } else {
                                    Logger.d("LastFmPlugin"){"[$instanceId] Fallback returned no art"}
                                }

                                Logger.d("LastFmPlugin"){"[$instanceId] Now Playing: $artist - $title"}
                                Logger.d("LastFmPlugin"){"[$instanceId] Artwork URL: ${artUrl ?: "none"}"}

                                val track = MusicTrack(
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    artworkUrl = artUrl,
                                    sourcePackageName = "com.lastfm"
                                )
                                callback?.invoke(PluginState.Playing(track))
                            }
                        }
                        } else {
                            callback?.invoke(PluginState.Idle)
                        }
                    } else {
                        this.isPlaying = false
                        callback?.invoke(PluginState.Idle)
                    }
                } catch (e: JSONException) {
                    Logger.e("LastFmPlugin"){"[$instanceId] Parse error: ${e.message}"}
                    this.isPlaying = false
                    callback?.invoke(PluginState.Idle)
                }
            },
            { error ->
                Logger.e("LastFmPlugin"){"[$instanceId] Network error: ${error.message}"}
                this.isPlaying = false
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
        Logger.d("LastFmPlugin"){"[$instanceId] DESTROY called"}
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        }
        handler.removeCallbacks(updateRunnable)
    }
}