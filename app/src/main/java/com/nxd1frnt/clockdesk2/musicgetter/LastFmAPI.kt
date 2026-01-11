package com.nxd1frnt.clockdesk2.musicgetter

import android.content.Context
import android.net.Uri
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.background.BackgroundManager
import org.json.JSONException


/** * DEPRECATED: Use MusicPluginManager with LastFmPlugin instead.
    * MusicGetter implementation for fetching music data from Last.fm API.
 */
class LastFmAPI(
    private val context: Context,
    private val callback: () -> Unit,
    val backgroundManager: BackgroundManager
) : MusicGetter(context, callback) {

    private val GENERIC_LASTFM_IMAGE_IDS = setOf(
        "2a96cbd8b46e442fc41c2b86b821562f" // generic image ID used by Last.fm
        // You can add other known generic IDs here if you find them
    )

    private fun isGenericLastFmImage(url: String): Boolean {
        return GENERIC_LASTFM_IMAGE_IDS.any { url.contains(it) } // Check if URL contains any known generic ID
    }

    private fun fetchAlbumArtFromThirdParty(artist: String, songName: String) {
        val searchTerm = "$artist - $songName"
        val url = Uri.parse("https://itunes.apple.com/search")
            .buildUpon()
            .appendQueryParameter("term", searchTerm)
            .appendQueryParameter("entity", "song") // Search for songs
            .appendQueryParameter("limit", "1")
            .build()
            .toString()

        Log.d("LastFmManager", "Fallback: Requesting from iTunes: $url")

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
                            Log.d("LastFmManager", "Fallback: Found iTunes art: $highResArtUrl")
                            currentAlbumArtUrl = highResArtUrl
                        } else {
                            currentAlbumArtUrl = null
                        }
                    } else {
                        Log.d("LastFmManager", "Fallback: iTunes found no results for '$searchTerm'")
                        currentAlbumArtUrl = null
                    }
                } catch (e: JSONException) {
                    Log.e("LastFmManager", "Fallback: iTunes JSON parsing error", e)
                    currentAlbumArtUrl = null
                }
                callback()
            },
            { error ->
                Log.e("LastFmManager", "Fallback: iTunes Volley error: ${error.message}")
                currentAlbumArtUrl = null
                // IMPORTANT: Call the main callback() even on fallback error
                callback()
            }
        )
        requestQueue.add(itunesRequest)
    }

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
        val albumartEnabled = prefs.getBoolean("lastfm_albumart_background", false)
        userPreselectedBackgroundUri = backgroundManager.getSavedBackgroundUri()
        if (apiKey.isNullOrEmpty() || username.isNullOrEmpty()) {
            Log.w("LastFmManager", "API Key or Username is missing.")
            // --- MODIFIED: Ensure callback is called on early exit ---
            enabled = false
            currentTrack = null
            currentAlbumArtUrl = null
            callback()
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
                //Log.d("LastFmManager", "Response JSON: $response")

                try {
                    if (response.has("error")) {
                        val errorCode = response.getInt("error")
                        val errorMessage = response.getString("message")
                        Log.e("LastFmManager", "Last.fm API Error $errorCode: $errorMessage")
                        enabled = false
                        currentTrack = null
                        currentAlbumArtUrl = null
                        callback()
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
                                enabled = true
                            }

                            if (!albumartEnabled) {
                                // Album art is disabled by user
                                currentAlbumArtUrl = null
                                callback() // Notify MainActivity
                                return@JsonObjectRequest
                            }

                            val imageUrl = track.getJSONArray("image").let { array ->
                                (0 until array.length())
                                    .map { array.getJSONObject(it) }
                                    .firstOrNull { it.getString("size") == "extralarge" }
                                    ?.getString("#text")
                            }

                            val isGeneric = !imageUrl.isNullOrEmpty() && isGenericLastFmImage(imageUrl) // Check for generic image

                            if (!imageUrl.isNullOrEmpty() && !isGeneric) {
                                // We have a good, non-generic URL from Last.fm
                                Log.d("LastFmManager", "Got Last.fm art: $imageUrl")
                                if (userPreselectedBackgroundUri == null && !wasGradientBackgroundActive) {
                                    wasGradientBackgroundActive = userPreselectedBackgroundUri == null
                                }
                                currentAlbumArtUrl = imageUrl
                                callback() // Notify MainActivity
                            } else {
                                // Generic URL or no URL. Try to fetch from iTunes.
                                // This function will handle calling callback()
                                Log.d("LastFmManager", "Last.fm art is generic or empty. Fetching from fallback.")
                                fetchAlbumArtFromThirdParty(artist, songName)
                            }

                        } else {
                            enabled = false
                            currentTrack = null
                            currentAlbumArtUrl = null // Clear art if not playing
                            callback() // Notify MainActivity
                        }
                    } else {
                        enabled = false
                        currentTrack = null
                        currentAlbumArtUrl = null
                        callback() // Notify MainActivity
                    }
                } catch (e: JSONException) {
                    Log.e("LastFmManager", "JSON parsing error", e)
                    enabled = false
                    currentTrack = null
                    currentAlbumArtUrl = null
                    callback()
                }
            },
            { error ->
                Log.e(
                    "LastFmManager",
                    "Volley error: ${error.networkResponse?.statusCode} ${error.message}"
                )
                enabled = false
                currentTrack = null
                currentAlbumArtUrl = null
                callback()
            }
        )
        requestQueue.add(jsonObjectRequest)
    }
}