package com.nxd1frnt.clockdesk2.musicgetter

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
import javax.security.auth.callback.Callback

class LastFmAPI(private val context: Context, private val callback: () -> Unit): MusicGetter(context, callback) {
    override fun startUpdates() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val username = prefs.getString("lastfm_username", "")
        val apiKey = prefs.getString("lastfm_api_key", "")
        enabled = prefs.getBoolean("enable_lastfm", false)

        if (apiKey.isNullOrEmpty() || username.isNullOrEmpty()) {
            enabled = false
            callback()
            return
        }

        super.startUpdates()
    }

    override fun fetch() {
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
                        enabled = false
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

                            if (trackInfo != currentTrack) {
                                currentTrack = trackInfo
                                currentTrack = trackInfo
                                enabled = true
                            }
                        } else {
                            enabled = false
                            currentTrack = null
                        }
                    }
                    callback()
                } catch (e: JSONException) {
                    Log.e("LastFmManager", "JSON parsing error", e)
                }
            },
            { error ->
                Log.e(
                    "LastFmManager",
                    "Volley error: ${error.networkResponse?.statusCode} ${error.message}"
                )
            }
        )
        requestQueue.add(jsonObjectRequest)
    }
}
