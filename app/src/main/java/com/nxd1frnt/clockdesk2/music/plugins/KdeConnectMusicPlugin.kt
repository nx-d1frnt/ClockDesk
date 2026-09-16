package com.nxd1frnt.clockdesk2.music.plugins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nxd1frnt.clockdesk2.connect.DeskConnectArtCache
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectPacket
import com.nxd1frnt.clockdesk2.music.IMusicPlugin
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.music.PluginState
import com.nxd1frnt.clockdesk2.utils.Logger
import java.util.concurrent.ConcurrentHashMap

class KdeConnectMusicPlugin(private val context: Context) : IMusicPlugin {
    override val id = "deskconnect_mpris"
    override val displayName = "DeskConnect"
    override val description = "Streams playback metadata & controls from paired phone or PC"
    override val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>? = null

    private var callback: ((PluginState) -> Unit)? = null
    private val deskConnectManager = DeskConnectManager.getInstance(context)
    private val artCache = DeskConnectArtCache.getInstance(context)

    private data class PlayerInfo(
        val deviceId: String,
        val playerName: String,
        var title: String,
        var artist: String,
        var album: String,
        var isPlaying: Boolean,
        var artworkUrl: String? = null,
        var artworkBitmap: Bitmap? = null,
        var rawAlbumArtUrl: String? = null,
        var lastUpdated: Long = System.currentTimeMillis()
    )

    private val activePlayers = ConcurrentHashMap<String, PlayerInfo>()
    private val knownPlayerLists = ConcurrentHashMap<String, Set<String>>()

    // Sticky player selection tracking (mirrors KDE Connect's MprisMediaSession)
    private var selectedDeviceId: String? = null
    private var selectedPlayerName: String? = null
    private var currentlyDispatchedKey: String? = null

    private val artListener: (String, Bitmap) -> Unit = { url, bitmap ->
        var updatedAny = false
        activePlayers.values.forEach { player ->
            if (player.rawAlbumArtUrl == url && player.artworkBitmap == null) {
                Logger.i("KdeConnectMusicPlugin") { "DeskConnectArtCache resolved artwork for '${player.playerName}' ($url)" }
                player.artworkBitmap = bitmap
                updatedAny = true
            }
        }
        if (updatedAny) {
            evaluateCurrentPlayback()
        }
    }

    private val deviceListener = object : DeskConnectManager.DeviceListener {
        override fun onDeviceDiscovered(device: DeskConnectDevice) {}

        override fun onDeviceConnected(device: DeskConnectDevice) {
            Logger.i("KdeConnectMusicPlugin") { "Connected to ${device.deviceName} (${device.deviceId}), requesting MPRIS players..." }
            if (device.isPaired) {
                deskConnectManager.activeConnections[device.deviceId]?.sendPacket(
                    DeskConnectPacket.createMprisPlayerListRequest()
                )
            }
        }

        override fun onDeviceDisconnected(device: DeskConnectDevice) {
            Logger.i("KdeConnectMusicPlugin") { "Disconnected from ${device.deviceName} (${device.deviceId}), purging its active players" }
            activePlayers.keys.forEach { key ->
                if (key.startsWith("${device.deviceId}:")) {
                    activePlayers.remove(key)
                }
            }
            knownPlayerLists.remove(device.deviceId)
            if (selectedDeviceId == device.deviceId) {
                selectedDeviceId = null
                selectedPlayerName = null
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
                knownPlayerLists.remove(device.deviceId)
                if (selectedDeviceId == device.deviceId) {
                    selectedDeviceId = null
                    selectedPlayerName = null
                }
                evaluateCurrentPlayback()
            }
        }
    }

    private val mprisListener = object : DeskConnectManager.MprisListener {
        override fun onPlayerListReceived(deviceId: String, playerList: List<String>) {
            Logger.d("KdeConnectMusicPlugin") { "Received playerList for device $deviceId: $playerList" }
            knownPlayerLists[deviceId] = playerList.toSet()

            // Remove players for this device that are no longer listed
            activePlayers.keys.forEach { key ->
                if (key.startsWith("$deviceId:")) {
                    val playerName = key.substringAfter("$deviceId:")
                    if (playerList.isNotEmpty() && !playerList.contains(playerName)) {
                        activePlayers.remove(key)
                        if (selectedDeviceId == deviceId && selectedPlayerName == playerName) {
                            selectedDeviceId = null
                            selectedPlayerName = null
                        }
                    }
                }
            }
            evaluateCurrentPlayback()
        }

        override fun onAlbumArtTransferred(deviceId: String, albumArtUrl: String, artworkBytes: ByteArray) {
            val bitmap = artCache.put(albumArtUrl, artworkBytes)
            if (bitmap != null) {
                Logger.i("KdeConnectMusicPlugin") { "onAlbumArtTransferred: Cached ${bitmap.width}x${bitmap.height} for $albumArtUrl" }
                var updatedAny = false
                activePlayers.values.forEach { player ->
                    if (player.rawAlbumArtUrl == albumArtUrl) {
                        player.artworkBitmap = bitmap
                        updatedAny = true
                    }
                }
                if (updatedAny) {
                    evaluateCurrentPlayback()
                }
            }
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
            val allowedPlayers = knownPlayerLists[deviceId]
            if (allowedPlayers != null && allowedPlayers.isNotEmpty() && !allowedPlayers.contains(player)) {
                Logger.d("KdeConnectMusicPlugin") {
                    "Ignoring MPRIS update from unlisted player '$player' on device $deviceId (allowed: $allowedPlayers)"
                }
                return
            }

            val key = "$deviceId:$player"

            Logger.i("KdeConnectMusicPlugin") {
                "onMprisUpdated: deviceId=$deviceId, player=$player, " +
                "title='$title', artist='$artist', album='$album', isPlaying=$isPlaying, " +
                "albumArtUrl='$artworkUrl', artworkBytes=${artworkBytes?.size ?: 0}"
            }

            var decodedBitmap: Bitmap? = null
            if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                decodedBitmap = artCache.put(artworkUrl ?: key, artworkBytes)
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

                if (!artworkUrl.isNullOrEmpty() && existing.rawAlbumArtUrl != artworkUrl) {
                    existing.rawAlbumArtUrl = artworkUrl
                    trackChanged = true
                }

                if (trackChanged) {
                    Logger.d("KdeConnectMusicPlugin") { "Track changed on $key -> '${existing.artist} - ${existing.title}'" }
                    val cached = artCache.get(existing.rawAlbumArtUrl)
                    existing.artworkBitmap = cached
                    existing.artworkUrl = if (isHttpArt) artworkUrl else null
                }

                if (isPlaying != null) {
                    existing.isPlaying = isPlaying
                }
                existing.lastUpdated = System.currentTimeMillis()

                if (decodedBitmap != null) {
                    existing.artworkBitmap = decodedBitmap
                    existing.artworkUrl = null
                } else if (isHttpArt) {
                    existing.artworkUrl = artworkUrl
                    val cached = artCache.get(artworkUrl)
                    if (cached != null) {
                        existing.artworkBitmap = cached
                    } else {
                        artCache.fetchHttpArt(artworkUrl)
                    }
                } else if (!artworkUrl.isNullOrEmpty()) {
                    val cached = artCache.get(artworkUrl)
                    if (cached != null) {
                        existing.artworkBitmap = cached
                    }
                }

                if (existing.isPlaying && existing.title.isEmpty() && existing.artist.isEmpty()) {
                    Logger.d("KdeConnectMusicPlugin") { "Player $key is playing with missing metadata, requesting status..." }
                    deskConnectManager.activeConnections[deviceId]?.sendPacket(
                        DeskConnectPacket.createMprisPlayerStatusRequest(player)
                    )
                }
            } else {
                val initialBitmap = decodedBitmap ?: artCache.get(artworkUrl)
                val newPlayer = PlayerInfo(
                    deviceId = deviceId,
                    playerName = player,
                    title = title ?: "",
                    artist = artist ?: "",
                    album = album ?: "",
                    isPlaying = isPlaying ?: false,
                    artworkUrl = if (isHttpArt) artworkUrl else null,
                    artworkBitmap = initialBitmap,
                    rawAlbumArtUrl = artworkUrl,
                    lastUpdated = System.currentTimeMillis()
                )
                activePlayers[key] = newPlayer

                if (isHttpArt && initialBitmap == null && artworkUrl != null) {
                    artCache.fetchHttpArt(artworkUrl)
                }

                if (newPlayer.isPlaying && newPlayer.title.isEmpty() && newPlayer.artist.isEmpty()) {
                    Logger.d("KdeConnectMusicPlugin") { "New player $key is playing with missing metadata, requesting status..." }
                    deskConnectManager.activeConnections[deviceId]?.sendPacket(
                        DeskConnectPacket.createMprisPlayerStatusRequest(player)
                    )
                }
            }

            evaluateCurrentPlayback()
        }
    }

    /**
     * Mirrors KDE Connect's MprisMediaSession.findPlayer() selection logic,
     * tailored to ClockDesk's requirement that only actively playing media is shown.
     */
    private fun findPlayer(): PlayerInfo? {
        val currentDevId = selectedDeviceId
        val currentPlayer = selectedPlayerName

        // 1. If currently selected player is still connected and actively playing, stick to it!
        if (currentDevId != null && currentPlayer != null) {
            val isConnected = deskConnectManager.activeConnections.containsKey(currentDevId)
            val current = activePlayers["$currentDevId:$currentPlayer"]
            if (isConnected && current != null && current.isPlaying && (current.title.isNotEmpty() || current.artist.isNotEmpty())) {
                return current
            }
        }

        // 2. Try another playing player on the currently selected device
        if (currentDevId != null && deskConnectManager.activeConnections.containsKey(currentDevId)) {
            val anotherOnCurrentDevice = activePlayers.values
                .filter {
                    it.deviceId == currentDevId &&
                    it.isPlaying &&
                    (it.title.isNotEmpty() || it.artist.isNotEmpty())
                }
                .maxByOrNull { it.lastUpdated }

            if (anotherOnCurrentDevice != null) {
                return anotherOnCurrentDevice
            }
        }

        // 3. Try any playing player on any other connected device
        val anotherDevicePlayer = activePlayers.values
            .filter {
                deskConnectManager.activeConnections.containsKey(it.deviceId) &&
                it.isPlaying &&
                (it.title.isNotEmpty() || it.artist.isNotEmpty())
            }
            .maxByOrNull { it.lastUpdated }

        return anotherDevicePlayer
    }

    @Synchronized
    private fun evaluateCurrentPlayback() {
        val candidate = findPlayer()

        if (candidate != null) {
            val key = "${candidate.deviceId}:${candidate.playerName}"
            selectedDeviceId = candidate.deviceId
            selectedPlayerName = candidate.playerName
            currentlyDispatchedKey = key

            val playerTitle = candidate.title
            val playerArtist = candidate.artist

            Logger.i("KdeConnectMusicPlugin") {
                "evaluateCurrentPlayback: Selected active player '$key' ('$playerArtist - $playerTitle'), " +
                "isPlaying=${candidate.isPlaying}, " +
                "hasBitmap=${candidate.artworkBitmap != null} (${candidate.artworkBitmap?.width}x${candidate.artworkBitmap?.height}), " +
                "artworkUrl='${candidate.artworkUrl}', rawAlbumArtUrl='${candidate.rawAlbumArtUrl}'"
            }

            dispatchTrack(candidate)
        } else {
            if (currentlyDispatchedKey != null) {
                Logger.d("KdeConnectMusicPlugin") { "evaluateCurrentPlayback: No active playback found. Clearing state to Idle." }
            }
            selectedDeviceId = null
            selectedPlayerName = null
            currentlyDispatchedKey = null
            callback?.invoke(PluginState.Idle)
        }
    }

    private fun dispatchTrack(info: PlayerInfo) {
        val dev = deskConnectManager.discoveredDevices[info.deviceId]
        val iconRes = dev?.getDeviceTypeIconRes() ?: com.nxd1frnt.clockdesk2.R.drawable.ic_devices
        Logger.i("KdeConnectMusicPlugin") {
            "dispatchTrack: '${info.artist} - ${info.title}', " +
            "hasBitmap=${info.artworkBitmap != null} (${info.artworkBitmap?.width}x${info.artworkBitmap?.height}), " +
            "artworkUrl='${info.artworkUrl}', " +
            "deviceIconRes=$iconRes"
        }
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

    override fun init() {
        deskConnectManager.mprisListeners.add(mprisListener)
        deskConnectManager.deviceListeners.add(deviceListener)
        artCache.registerListener(artListener)

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
        artCache.unregisterListener(artListener)
        activePlayers.clear()
        knownPlayerLists.clear()
        selectedDeviceId = null
        selectedPlayerName = null
        currentlyDispatchedKey = null
        callback?.invoke(PluginState.Idle)
    }

    override fun setCallback(callback: (PluginState) -> Unit) {
        this.callback = callback
    }
}
