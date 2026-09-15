package com.nxd1frnt.clockdesk2.music.plugins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.music.IMusicPlugin
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.music.PluginState
import com.nxd1frnt.clockdesk2.network.NetworkManager
import com.nxd1frnt.clockdesk2.utils.Logger
import org.json.JSONException
import java.util.concurrent.ConcurrentHashMap

class KdeConnectMusicPlugin(private val context: Context) : IMusicPlugin {
    override val id = "deskconnect_mpris"
    override val displayName = "DeskConnect"
    override val description = "Streams playback metadata & controls from paired phone or PC"
    override val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>? = null

    private var callback: ((PluginState) -> Unit)? = null
    private val deskConnectManager = DeskConnectManager.getInstance(context)
    private val requestQueue = NetworkManager.getRequestQueue(context)

    private data class PlayerInfo(
        val deviceId: String,
        val playerName: String,
        var title: String,
        var artist: String,
        var album: String,
        var isPlaying: Boolean,
        var artworkUrl: String? = null,
        var artworkBitmap: Bitmap? = null,
        var lastUpdated: Long = System.currentTimeMillis()
    )

    private val activePlayers = ConcurrentHashMap<String, PlayerInfo>()
    private var currentlyDispatchedKey: String? = null

    private val mprisListener = object : DeskConnectManager.MprisListener {
        override fun onMprisUpdated(
            deviceId: String,
            player: String,
            title: String,
            artist: String,
            album: String,
            isPlaying: Boolean,
            positionMs: Long,
            lengthMs: Long,
            artworkBytes: ByteArray?,
            artworkUrl: String?
        ) {
            val key = "$deviceId:$player"

            var decodedBitmap: Bitmap? = null
            if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                try {
                    decodedBitmap = BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size)
                } catch (e: Exception) {
                    Logger.e("KdeConnectMusicPlugin") { "Failed to decode artwork bytes: ${e.message}" }
                }
            }

            val isHttpArt = !artworkUrl.isNullOrEmpty() && (artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://"))

            val existing = activePlayers[key]
            if (existing != null) {
                if (title.isNotEmpty()) existing.title = title
                if (artist.isNotEmpty()) existing.artist = artist
                if (album.isNotEmpty()) existing.album = album
                existing.isPlaying = isPlaying
                existing.lastUpdated = System.currentTimeMillis()

                if (decodedBitmap != null) {
                    existing.artworkBitmap = decodedBitmap
                    existing.artworkUrl = null
                } else if (isHttpArt) {
                    existing.artworkUrl = artworkUrl
                    existing.artworkBitmap = null
                }
            } else {
                activePlayers[key] = PlayerInfo(
                    deviceId = deviceId,
                    playerName = player,
                    title = title,
                    artist = artist,
                    album = album,
                    isPlaying = isPlaying,
                    artworkUrl = if (isHttpArt) artworkUrl else null,
                    artworkBitmap = decodedBitmap,
                    lastUpdated = System.currentTimeMillis()
                )
            }

            evaluateCurrentPlayback()
        }
    }

    @Synchronized
    private fun evaluateCurrentPlayback() {
        // Find any player currently playing with a valid title or artist
        val candidate = activePlayers.values
            .filter { it.isPlaying && (it.title.isNotEmpty() || it.artist.isNotEmpty()) }
            .maxByOrNull { it.lastUpdated }

        if (candidate != null) {
            val key = "${candidate.deviceId}:${candidate.playerName}"
            currentlyDispatchedKey = key

            val playerTitle = candidate.title
            val playerArtist = candidate.artist

            // Check if we need to query iTunes for album art
            if (candidate.artworkBitmap == null && candidate.artworkUrl.isNullOrEmpty()) {
                fetchAlbumArtFallback(playerArtist, playerTitle) { fallbackUrl ->
                    val currentCandidate = activePlayers[key]
                    if (currentCandidate != null && currentCandidate.title == playerTitle && currentCandidate.artist == playerArtist) {
                        currentCandidate.artworkUrl = fallbackUrl
                        if (currentlyDispatchedKey == key) {
                            dispatchTrack(currentCandidate)
                        }
                    }
                }
            }

            dispatchTrack(candidate)
        } else {
            currentlyDispatchedKey = null
            callback?.invoke(PluginState.Idle)
        }
    }

    private fun dispatchTrack(info: PlayerInfo) {
        val track = MusicTrack(
            title = info.title,
            artist = info.artist,
            album = info.album.ifEmpty { null },
            artworkUrl = info.artworkUrl,
            artworkBitmap = info.artworkBitmap,
            sourcePackageName = info.playerName
        )
        callback?.invoke(PluginState.Playing(track))
    }

    private fun fetchAlbumArtFallback(artist: String, songName: String, onResult: (String?) -> Unit) {
        val searchTerm = if (artist.isNotEmpty() && songName.isNotEmpty()) {
            "$artist - $songName"
        } else {
            songName.ifEmpty { artist }
        }

        if (searchTerm.isBlank()) {
            onResult(null)
            return
        }

        val url = Uri.parse("https://itunes.apple.com/search")
            .buildUpon()
            .appendQueryParameter("term", searchTerm)
            .appendQueryParameter("entity", "song")
            .appendQueryParameter("limit", "1")
            .build()
            .toString()

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    val resultCount = response.optInt("resultCount", 0)
                    if (resultCount > 0) {
                        val results = response.getJSONArray("results")
                        val firstResult = results.getJSONObject(0)
                        var artUrl = firstResult.optString("artworkUrl600")
                        if (artUrl.isEmpty()) {
                            artUrl = firstResult.optString("artworkUrl100")
                        }
                        if (artUrl.isNotEmpty()) {
                            val highRes = artUrl.replace("100x100", "600x600")
                            onResult(highRes)
                            return@JsonObjectRequest
                        }
                    }
                    onResult(null)
                } catch (e: JSONException) {
                    onResult(null)
                }
            },
            {
                onResult(null)
            }
        )
        requestQueue.add(request)
    }

    override fun init() {
        deskConnectManager.mprisListeners.add(mprisListener)
        Logger.d("KdeConnectMusicPlugin") { "Initialized DeskConnect music plugin" }
    }

    override fun destroy() {
        deskConnectManager.mprisListeners.remove(mprisListener)
        activePlayers.clear()
        currentlyDispatchedKey = null
        callback?.invoke(PluginState.Idle)
    }

    override fun setCallback(callback: (PluginState) -> Unit) {
        this.callback = callback
    }
}
