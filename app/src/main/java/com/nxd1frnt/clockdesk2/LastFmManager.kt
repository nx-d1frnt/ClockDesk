package com.nxd1frnt.clockdesk2

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException

class LastFmManager(private val context: Context, private val trackTextView: TextView, private val trackLayout: LinearLayout) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastTrackInfo: String? = null
    private val requestQueue = Volley.newRequestQueue(context.applicationContext)

    private val updateRunnable = object : Runnable {
        override fun run() {
            fetchCurrentTrack()
            handler.postDelayed(this, 30000) // Update every 30 seconds
        }
    }

    fun startUpdates() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val username = prefs.getString("lastfm_username", "")
        val apiKey = prefs.getString("lastfm_api_key", "")
        val enabled = prefs.getBoolean("enable_lastfm", false)
        if (!enabled) {
            trackLayout.visibility = View.GONE
            return
        }
        if (apiKey.isNullOrEmpty()) {
            trackLayout.visibility = View.GONE
            return
        }
        if (username.isNullOrEmpty()) {
            trackLayout.visibility = View.GONE
            return
        }
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
    }

    fun stopUpdates() {
        handler.removeCallbacks(updateRunnable)
    }

    private fun fetchCurrentTrack() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val username = prefs.getString("lastfm_username", "")
        val apiKey = prefs.getString("lastfm_api_key", "")
        if (apiKey.isNullOrEmpty() || username.isNullOrEmpty()) {
            Log.w("LastFmManager", "API Key or Username is missing.")
            return
        }

        val url = Uri.parse("https://ws.audioscrobbler.com/2.0/")
            .buildUpon()
            .appendQueryParameter("method", "user.getrecenttracks")
            .appendQueryParameter("user", username)
            .appendQueryParameter("api_key", apiKey)
            .appendQueryParameter("format", "json")
            .appendQueryParameter("limit", "1")
            .build()
            .toString()

        Log.d("LastFmManager", "Requesting URL: $url")

        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                Log.d("LastFmManager", "Response JSON: ${response.toString()}")

                try {
                    if (response.has("error")) {
                        val errorCode = response.getInt("error")
                        val errorMessage = response.getString("message")
                        Log.e("LastFmManager", "Last.fm API Error $errorCode: $errorMessage")
                        trackLayout.visibility = View.GONE
                        return@JsonObjectRequest
                    }

                    val recentTracks = response.getJSONObject("recenttracks")
                    val trackArray = recentTracks.getJSONArray("track")

                    if (trackArray.length() > 0) {
                        val track = trackArray.getJSONObject(0)
                        val attr = track.optJSONObject("@attr")

                        if (attr != null && attr.optString("nowplaying") == "true") {
                            val artist = track.getJSONObject("artist").getString("#text")
                            val songName = track.getString("name")
                            val trackInfo = "$artist - $songName"

                            if (trackInfo != lastTrackInfo) {
                                lastTrackInfo = trackInfo
                                trackTextView.text = trackInfo
                                trackLayout.visibility = View.VISIBLE
                            }
                        } else {
                            trackLayout.visibility = View.GONE
                            lastTrackInfo = null
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("LastFmManager", "JSON parsing error", e)
                }
            },
            { error ->
                Log.e("LastFmManager", "Volley error: ${error.networkResponse?.statusCode} ${error.message}")
            }
        )
        requestQueue.add(jsonObjectRequest)
    }
}