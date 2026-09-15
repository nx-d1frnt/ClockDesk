package com.nxd1frnt.clockdesk2.music.plugins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectPacket
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
        var isFetchingArtwork: Boolean = false,
        var failedArtworkFetch: Boolean = false,
        var lastUpdated: Long = System.currentTimeMillis()
    )

    private val activePlayers = ConcurrentHashMap<String, PlayerInfo>()
    private var currentlyDispatchedKey: String? = null

    private val deviceListener = object : DeskConnectManager.DeviceListener {
        override fun onDeviceDiscovered(device: DeskConnectDevice) {}

        override fun onDeviceConnected(device: DeskConnectDevice) {
            if (device.isPaired) {
                deskConnectManager.activeConnections[device.deviceId]?.sendPacket(
                    DeskConnectPacket.createMprisPlayerListRequest()
                )
            }
        }

        override fun onDeviceDisconnected(device: DeskConnectDevice) {
            // Immediately purge any player belonging to the disconnected device to prevent stuck playback
            activePlayers.keys.forEach { key ->
                if (key.startsWith("${device.deviceId}:")) {
                    activePlayers.remove(key)
                }
            }
            evaluateCurrentPlayback()
        }

        override fun onPairingRequested(device: DeskConnectDevice, verificationKey: String) {}

        override fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean) {
            if (!isPaired) {
                activePlayers.keys.forEach { key ->
                    if (key.startsWith("${device.deviceId}:")) {
                        activePlayers.remove(key)
                    }
                }
                evaluateCurrentPlayback()
            }
        }
    }

    private val mprisListener = object : DeskConnectManager.MprisListener {
        override fun onPlayerListReceived(deviceId: String, playerList: List<String>) {
            // Remove players for this device that are no longer running
            activePlayers.keys.forEach { key ->
                if (key.startsWith("$deviceId:")) {
                    val playerName = key.substringAfter("$deviceId:")
                    if (!playerList.contains(playerName)) {
                        activePlayers.remove(key)
                    }
                }
            }
            evaluateCurrentPlayback()
        }

        override fun onMprisUpdated(
            deviceId: String,
            player: String,
            title: String?,
            artist: String?,
            album: String?,
            isPlaying: Boolean?,
            positionMs: Long?,
            lengthMs: Long?,
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

            val isHttpArt = !artworkUrl.isNullOrEmpty() &&
                    (artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://"))

            val existing = activePlayers[key]
            if (existing != null) {
                var trackChanged = false
                if (!title.isNullOrEmpty() && existing.title != title) {
                    existing.title = title
                    trackChanged = true
                }
                if (!artist.isNullOrEmpty() && existing.artist != artist) {
                    existing.artist = artist
                    trackChanged = true
                }
                if (!album.isNullOrEmpty()) {
                    existing.album = album
                }

                // If track changed, invalidate previous artwork caches so the new song gets its own art
                if (trackChanged) {
                    existing.artworkBitmap = null
                    existing.artworkUrl = null
                    existing.isFetchingArtwork = false
                    existing.failedArtworkFetch = false
                }

                // If isPlaying is explicitly provided, update it; otherwise preserve existing state
                if (isPlaying != null) {
                    existing.isPlaying = isPlaying
                }
                existing.lastUpdated = System.currentTimeMillis()

                if (decodedBitmap != null) {
                    existing.artworkBitmap = decodedBitmap
                    existing.artworkUrl = null
                } else if (isHttpArt) {
                    existing.artworkUrl = artworkUrl
                    existing.artworkBitmap = null
                }

                // If player is playing but title and artist are still missing, ask for status
                if (existing.isPlaying && existing.title.isEmpty() && existing.artist.isEmpty()) {
                    deskConnectManager.activeConnections[deviceId]?.sendPacket(
                        DeskConnectPacket.createMprisPlayerStatusRequest(player)
                    )
                }
            } else {
                val newPlayer = PlayerInfo(
                    deviceId = deviceId,
                    playerName = player,
                    title = title ?: "",
                    artist = artist ?: "",
                    album = album ?: "",
                    isPlaying = isPlaying ?: false,
                    artworkUrl = if (isHttpArt) artworkUrl else null,
                    artworkBitmap = decodedBitmap,
                    lastUpdated = System.currentTimeMillis()
                )
                activePlayers[key] = newPlayer

                if (newPlayer.isPlaying && newPlayer.title.isEmpty() && newPlayer.artist.isEmpty()) {
                    deskConnectManager.activeConnections[deviceId]?.sendPacket(
                        DeskConnectPacket.createMprisPlayerStatusRequest(player)
                    )
                }
            }

            evaluateCurrentPlayback()
        }
    }

    @Synchronized
    private fun evaluateCurrentPlayback() {
        // Only select players from actively connected devices that are playing with a valid title/artist
        val candidate = activePlayers.values
            .filter { info ->
                val isConnected = deskConnectManager.activeConnections.containsKey(info.deviceId)
                isConnected && info.isPlaying && (info.title.isNotEmpty() || info.artist.isNotEmpty())
            }
            .maxByOrNull { it.lastUpdated }

        if (candidate != null) {
            val key = "${candidate.deviceId}:${candidate.playerName}"
            currentlyDispatchedKey = key

            val playerTitle = candidate.title
            val playerArtist = candidate.artist

            // Query iTunes for album art fallback if missing and not already requested
            if (candidate.artworkBitmap == null && candidate.artworkUrl.isNullOrEmpty() &&
                !candidate.isFetchingArtwork && !candidate.failedArtworkFetch) {
                candidate.isFetchingArtwork = true
                fetchAlbumArtFallback(playerArtist, playerTitle) { fallbackUrl ->
                    candidate.isFetchingArtwork = false
                    if (fallbackUrl.isNullOrEmpty()) {
                        candidate.failedArtworkFetch = true
                    } else {
                        val currentCandidate = activePlayers[key]
                        if (currentCandidate != null &&
                            currentCandidate.title == playerTitle &&
                            currentCandidate.artist == playerArtist) {
                            currentCandidate.artworkUrl = fallbackUrl
                            if (currentlyDispatchedKey == key) {
                                dispatchTrack(currentCandidate)
                            }
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
        val dev = deskConnectManager.discoveredDevices[info.deviceId]
        val iconRes = dev?.getDeviceTypeIconRes() ?: com.nxd1frnt.clockdesk2.R.drawable.ic_devices
        val track = MusicTrack(
            title = info.title,
            artist = info.artist,
            album = info.album.ifEmpty { null },
            artworkUrl = info.artworkUrl,
            artworkBitmap = info.artworkBitmap,
            sourcePackageName = info.playerName,
            sourceIconResId = iconRes
        )
        callback?.invoke(PluginState.Playing(track))
    }

    private fun fetchAlbumArtFallback(artist: String, songName: String, onResult: (String?) -> Unit) {
        val cleanSong = songName
            .replace(Regex("(?i)\\(official (audio|video|music video|lyric video)\\)"), "")
            .replace(Regex("(?i)\\[official (audio|video|music video|lyric video)\\]"), "")
            .trim()

        val searchTerm = if (artist.isNotEmpty() && cleanSong.isNotEmpty()) {
            "$artist - $cleanSong"
        } else {
            cleanSong.ifEmpty { artist }
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
        deskConnectManager.deviceListeners.add(deviceListener)

        // Request player list from all currently active paired connections
        deskConnectManager.activeConnections.values.forEach { conn ->
            if (conn.device.isPaired) {
                conn.sendPacket(DeskConnectPacket.createMprisPlayerListRequest())
            }
        }
        Logger.d("KdeConnectMusicPlugin") { "Initialized DeskConnect music plugin" }
    }

    override fun destroy() {
        deskConnectManager.mprisListeners.remove(mprisListener)
        deskConnectManager.deviceListeners.remove(deviceListener)
        activePlayers.clear()
        currentlyDispatchedKey = null
        callback?.invoke(PluginState.Idle)
    }

    override fun setCallback(callback: (PluginState) -> Unit) {
        this.callback = callback
    }
}
