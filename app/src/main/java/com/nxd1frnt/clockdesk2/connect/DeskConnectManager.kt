package com.nxd1frnt.clockdesk2.connect

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.nxd1frnt.clockdesk2.connect.discovery.DeskConnectDiscovery
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectPacket
import com.nxd1frnt.clockdesk2.connect.security.DeskConnectSecurity
import com.nxd1frnt.clockdesk2.connect.transport.DeskConnectConnection
import com.nxd1frnt.clockdesk2.connect.transport.DeskConnectServer
import com.nxd1frnt.clockdesk2.utils.Logger
import org.json.JSONObject
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLSocket

class DeskConnectManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: DeskConnectManager? = null

        fun getInstance(context: Context): DeskConnectManager {
            return instance ?: synchronized(this) {
                instance ?: DeskConnectManager(context.applicationContext).also { instance = it }
            }
        }

        fun calculateVerificationKey(certA: Certificate, certB: Certificate): String {
            val bytesA = certA.publicKey.encoded
            val bytesB = certB.publicKey.encoded
            val concat = if (compareUnsigned(bytesA, bytesB) < 0) {
                bytesB + bytesA
            } else {
                bytesA + bytesB
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(concat)
            return hash.joinToString("") { String.format("%02x", it) }.take(8).uppercase()
        }

        private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
            val minLen = minOf(a.size, b.size)
            for (i in 0 until minLen) {
                val aByte = a[i].toInt() and 0xFF
                val bByte = b[i].toInt() and 0xFF
                if (aByte != bByte) return aByte - bByte
            }
            return a.size - b.size
        }
    }

    val security = DeskConnectSecurity(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sharedPrefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val discoveredDevices = ConcurrentHashMap<String, DeskConnectDevice>()
    val activeConnections = ConcurrentHashMap<String, DeskConnectConnection>()

    private val pendingOutgoingPairRequests = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val pendingIncomingPairRequests = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val connectingDevices = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private var server: DeskConnectServer? = null
    private var discovery: DeskConnectDiscovery? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val iconCacheByHash = object : LruCache<String, ByteArray>(64) {}
    private val iconCacheByApp = object : LruCache<String, ByteArray>(64) {}

    // Listeners
    interface DeviceListener {
        fun onDeviceDiscovered(device: DeskConnectDevice)
        fun onDeviceConnected(device: DeskConnectDevice)
        fun onDeviceDisconnected(device: DeskConnectDevice)
        fun onPairingRequested(device: DeskConnectDevice, verificationKey: String)
        fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean)
    }

    interface NotificationListener {
        fun onNotificationReceived(
            deviceId: String,
            notificationId: String,
            appName: String,
            title: String,
            text: String,
            timestamp: Long,
            iconBytes: ByteArray?,
            isClearable: Boolean
        )
        fun onNotificationDismissed(deviceId: String, notificationId: String)
    }

    interface TelephonyListener {
        fun onCallStateChanged(
            deviceId: String,
            event: String,
            phoneNumber: String,
            contactName: String?,
            phoneThumbnail: String?
        )
    }

    interface BatteryListener {
        fun onBatteryUpdated(deviceId: String, currentCharge: Int, isCharging: Boolean, thresholdEvent: Int)
    }

    interface MprisListener {
        fun onPlayerListReceived(deviceId: String, playerList: List<String>) {}
        fun onAlbumArtTransferred(deviceId: String, albumArtUrl: String, artworkBytes: ByteArray) {}
        fun onMprisUpdated(
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
        ) {}
    }

    val deviceListeners = CopyOnWriteArrayList<DeviceListener>()
    val notificationListeners = CopyOnWriteArrayList<NotificationListener>()
    val batteryListeners = CopyOnWriteArrayList<BatteryListener>()
    val telephonyListeners = CopyOnWriteArrayList<TelephonyListener>()
    val mprisListeners = CopyOnWriteArrayList<MprisListener>()

    var isEnabled: Boolean
        get() = sharedPrefs.getBoolean("deskconnect_enabled", true)
        set(value) {
            sharedPrefs.edit().putBoolean("deskconnect_enabled", value).apply()
            if (value) start() else stop()
        }

    val customDeviceName: String
        get() = sharedPrefs.getString("deskconnect_device_name", null)
            ?: "ClockDesk (${Build.MODEL})"

    init {
        loadPairedDevices()
        if (isEnabled) {
            start()
        }
    }

    fun start() {
        if (server != null) return

        registerNetworkCallback()

        val s = DeskConnectServer(security, customDeviceName) { sslSocket, cert, remoteDeviceId, initialPacket ->
            handleIncomingSslConnection(sslSocket, cert, remoteDeviceId, initialPacket)
        }
        if (s.start()) {
            server = s
        }

        val portToAdvertise = server?.boundPort ?: DeskConnectServer.DEFAULT_PORT

        discovery = DeskConnectDiscovery(
            context = context,
            security = security,
            deviceName = customDeviceName,
            tcpPort = portToAdvertise,
            shouldBroadcast = {
                val paired = discoveredDevices.values.filter { it.isPaired }
                paired.isEmpty() || paired.any { !it.isConnected }
            },
            isDeviceConnected = { devId ->
                activeConnections[devId]?.isAlive() == true
            },
            onDeviceDiscovered = { discoveredDevice ->
                handleDiscoveredDevice(discoveredDevice)
            }
        ).apply {
            start()
        }

        Logger.d("DeskConnectManager") { "DeskConnect service started with deviceId '${security.deviceId}' and name '$customDeviceName' on TCP $portToAdvertise" }
    }

    fun stop() {
        unregisterNetworkCallback()

        discovery?.stop()
        discovery = null

        server?.stop()
        server = null

        activeConnections.values.forEach { it.close() }
        activeConnections.clear()
        pendingOutgoingPairRequests.clear()
        pendingIncomingPairRequests.clear()
        connectingDevices.clear()
        Logger.d("DeskConnectManager") { "DeskConnect service stopped" }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            connectivityManager = cm

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Logger.i("DeskConnect/Network") { "Wi-Fi network available, broadcasting discovery" }
                    mainHandler.postDelayed({
                        if (isEnabled) {
                            broadcastDiscovery()
                        }
                    }, 1000L)
                }

                override fun onLost(network: Network) {
                    Logger.i("DeskConnect/Network") { "Wi-Fi network lost, closing active connections" }
                    mainHandler.post {
                        activeConnections.values.forEach { conn ->
                            conn.close()
                        }
                    }
                }
            }
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
            Logger.d("DeskConnect/Network") { "Wi-Fi network monitor registered" }
        } catch (e: Exception) {
            Logger.w("DeskConnect/Network") { "Failed to register network callback: ${e.message}" }
        }
    }

    private fun unregisterNetworkCallback() {
        val cm = connectivityManager
        val cb = networkCallback
        if (cm != null && cb != null) {
            try {
                cm.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                // Ignored
            }
        }
        networkCallback = null
        connectivityManager = null
    }

    fun broadcastDiscovery() {
        discovery?.broadcastIdentity()
    }

    fun restart() {
        stop()
        if (isEnabled) {
            start()
        }
    }

    private fun loadPairedDevices() {
        val pairedIds = security.getPairedDeviceIds()
        for (id in pairedIds) {
            val name = sharedPrefs.getString("deskconnect_paired_name_$id", id) ?: id
            val type = sharedPrefs.getString("deskconnect_paired_type_$id", "phone") ?: "phone"
            val device = DeskConnectDevice(
                deviceId = id,
                deviceName = name,
                deviceType = type,
                isPaired = true
            )
            discoveredDevices[id] = device
        }
    }

    private fun handleDiscoveredDevice(device: DeskConnectDevice) {
        val isPaired = security.isDevicePaired(device.deviceId)
        val existing = discoveredDevices[device.deviceId]

        if (existing != null) {
            existing.deviceName = device.deviceName
            existing.deviceType = device.deviceType
            existing.ipAddress = device.ipAddress
            existing.tcpPort = device.tcpPort
            existing.protocolVersion = device.protocolVersion
            existing.incomingCapabilities = device.incomingCapabilities
            existing.outgoingCapabilities = device.outgoingCapabilities
            existing.lastSeenTimestamp = System.currentTimeMillis()
            existing.isPaired = isPaired
        } else {
            device.isPaired = isPaired
            discoveredDevices[device.deviceId] = device
        }

        val target = discoveredDevices[device.deviceId] ?: device

        if (target.isPaired) {
            sharedPrefs.edit()
                .putString("deskconnect_paired_name_${target.deviceId}", target.deviceName)
                .putString("deskconnect_paired_type_${target.deviceId}", target.deviceType)
                .apply()
        }

        mainHandler.post {
            deviceListeners.forEach { it.onDeviceDiscovered(target) }
        }

        if (target.isPaired && !activeConnections.containsKey(target.deviceId) && !connectingDevices.contains(target.deviceId) && target.ipAddress != null) {
            if (security.deviceId > target.deviceId) {
                connectToDevice(target)
            }
        }
    }

    private fun handleIncomingSslConnection(
        sslSocket: SSLSocket,
        cert: X509Certificate?,
        remoteDeviceId: String,
        initialPacket: DeskConnectPacket
    ) {
        val body = initialPacket.body
        val remoteName = body.optString("deviceName", "Device ${remoteDeviceId.take(6)}")
        val deviceType = body.optString("deviceType", "phone")

        val device = discoveredDevices.getOrPut(remoteDeviceId) {
            DeskConnectDevice(
                deviceId = remoteDeviceId,
                deviceName = remoteName,
                deviceType = deviceType,
                ipAddress = sslSocket.inetAddress,
                isPaired = security.isDevicePaired(remoteDeviceId),
                certificate = cert
            )
        }
        device.deviceName = remoteName
        device.deviceType = deviceType
        device.ipAddress = sslSocket.inetAddress
        device.certificate = cert
        device.isConnected = true
        device.isPaired = security.isDevicePaired(remoteDeviceId)

        val connection = DeskConnectConnection(
            socket = sslSocket,
            device = device,
            security = security,
            onPacketReceived = { conn, packet, payload ->
                handlePacket(conn, packet, payload)
            },
            onDisconnected = { conn ->
                handleDisconnected(conn)
            }
        )

        val oldConn = activeConnections.put(remoteDeviceId, connection)
        if (oldConn != null && oldConn != connection) {
            oldConn.isReplaced = true
            oldConn.close()
        }

        if (device.isPaired) {
            connection.sendPacket(DeskConnectPacket.createMprisPlayerListRequest())
        }

        Logger.i("DeskConnect/Connection") {
            "Incoming connection established with ${device.deviceName} (${device.deviceId}) from ${sslSocket.inetAddress?.hostAddress} [type=${device.deviceType}, paired=${device.isPaired}]"
        }

        mainHandler.post {
            deviceListeners.forEach { it.onDeviceDiscovered(device) }
            deviceListeners.forEach { it.onDeviceConnected(device) }
        }
    }

    fun connectToDevice(device: DeskConnectDevice) {
        if (activeConnections.containsKey(device.deviceId)) return
        if (!connectingDevices.add(device.deviceId)) return
        val s = server ?: run {
            connectingDevices.remove(device.deviceId)
            return
        }

        s.connectToDevice(device, onConnected = { sslSocket, cert ->
            connectingDevices.remove(device.deviceId)
            device.certificate = cert
            device.ipAddress = sslSocket.inetAddress
            device.isConnected = true
            device.isPaired = security.isDevicePaired(device.deviceId)

            val connection = DeskConnectConnection(
                socket = sslSocket,
                device = device,
                security = security,
                onPacketReceived = { conn, packet, payload ->
                    handlePacket(conn, packet, payload)
                },
                onDisconnected = { conn ->
                    handleDisconnected(conn)
                }
            )

            val oldConn = activeConnections.put(device.deviceId, connection)
            if (oldConn != null && oldConn != connection) {
                oldConn.isReplaced = true
                oldConn.close()
            }

            if (device.isPaired) {
                connection.sendPacket(DeskConnectPacket.createMprisPlayerListRequest())
            }

            Logger.i("DeskConnect/Connection") {
                "Outgoing connection established to ${device.deviceName} (${device.deviceId}) at ${device.ipAddress?.hostAddress}:${device.tcpPort} [type=${device.deviceType}, paired=${device.isPaired}]"
            }

            mainHandler.post {
                deviceListeners.forEach { it.onDeviceConnected(device) }
            }
        }, onError = {
            connectingDevices.remove(device.deviceId)
            device.isConnected = false
            Logger.w("DeskConnect/Connection") {
                "Failed outgoing connection to ${device.deviceName} (${device.deviceId}) at ${device.ipAddress?.hostAddress}:${device.tcpPort}: ${it.message}"
            }
        })
    }

    private fun handlePacket(conn: DeskConnectConnection, packet: DeskConnectPacket, payload: ByteArray?) {
        Logger.d("DeskConnectManager") { "Received packet type '${packet.type}' from ${conn.device.deviceId}" }

        when (packet.type) {
            DeskConnectPacket.TYPE_IDENTITY -> {
                val body = packet.body
                val remoteId = body.optString("deviceId", conn.device.deviceId)
                val remoteName = body.optString("deviceName", conn.device.deviceName)
                val deviceType = body.optString("deviceType", conn.device.deviceType)

                val device = discoveredDevices.getOrPut(remoteId) {
                    DeskConnectDevice(
                        deviceId = remoteId,
                        deviceName = remoteName,
                        deviceType = deviceType,
                        ipAddress = conn.socket.inetAddress,
                        isPaired = security.isDevicePaired(remoteId),
                        certificate = conn.device.certificate
                    )
                }
                device.deviceName = remoteName
                device.deviceType = deviceType
                device.ipAddress = conn.socket.inetAddress
                device.isConnected = true
                device.isPaired = security.isDevicePaired(remoteId)
                conn.device = device

                val oldConn = activeConnections.put(remoteId, conn)
                if (oldConn != null && oldConn != conn) {
                    oldConn.isReplaced = true
                    oldConn.close()
                }

                if (device.isPaired) {
                    conn.sendPacket(DeskConnectPacket.createMprisPlayerListRequest())
                }

                Logger.i("DeskConnect/Connection") {
                    "Identity packet confirmed for ${device.deviceName} (${device.deviceId}) [type=${device.deviceType}, paired=${device.isPaired}]"
                }

                mainHandler.post {
                    deviceListeners.forEach { it.onDeviceDiscovered(device) }
                    deviceListeners.forEach { it.onDeviceConnected(device) }
                }
            }

            DeskConnectPacket.TYPE_PAIR -> {
                val wantsPair = packet.body.optBoolean("pair", false)
                val dev = conn.device
                if (wantsPair) {
                    if (dev.isPaired || security.isDevicePaired(dev.deviceId)) {
                        dev.isPaired = true
                        conn.sendPacket(DeskConnectPacket.createMprisPlayerListRequest())
                        return
                    }

                    if (pendingOutgoingPairRequests.contains(dev.deviceId)) {
                        pendingOutgoingPairRequests.remove(dev.deviceId)
                        if (dev.certificate != null) {
                            security.trustDevice(dev.deviceId, dev.certificate!!)
                        }
                        dev.isPaired = true
                        sharedPrefs.edit().putString("deskconnect_paired_name_${dev.deviceId}", dev.deviceName).apply()

                        conn.sendPacket(DeskConnectPacket.createMprisPlayerListRequest())

                        mainHandler.post {
                            deviceListeners.forEach { it.onPairingStateChanged(dev, true) }
                        }
                    } else {
                        if (pendingIncomingPairRequests.contains(dev.deviceId)) {
                            return
                        }
                        pendingIncomingPairRequests.add(dev.deviceId)

                        val verificationKey = if (dev.certificate != null) {
                            calculateVerificationKey(security.certificate, dev.certificate!!)
                        } else {
                            security.getCertificateFingerprint().take(8).uppercase()
                        }

                        mainHandler.post {
                            deviceListeners.forEach { it.onPairingRequested(dev, verificationKey) }
                        }
                    }
                } else {
                    pendingOutgoingPairRequests.remove(dev.deviceId)
                    pendingIncomingPairRequests.remove(dev.deviceId)
                    unpairDevice(dev.deviceId)
                }
            }

            DeskConnectPacket.TYPE_NOTIFICATION -> {
                if (!conn.device.isPaired) return
                val body = packet.body
                val nId = body.optString("id", "")
                val isCancel = body.optBoolean("isCancel", false)

                if (isCancel && nId.isNotEmpty()) {
                    Logger.d("DeskConnectManager") { "Received notification isCancel for id='$nId' from ${conn.device.getDisplayName()}" }
                    mainHandler.post {
                        notificationListeners.forEach {
                            it.onNotificationDismissed(conn.device.deviceId, nId)
                        }
                    }
                    return
                }

                val appName = body.optString("appName", "App")
                val title = body.optString("title", "")
                val text = body.optString("text", body.optString("ticker", ""))
                val time = body.optLong("time", System.currentTimeMillis())
                val isClearable = body.optBoolean("isClearable", true)
                val isSilent = body.optBoolean("silent", false)
                val payloadHash = body.optString("payloadHash", "").takeIf { it.isNotEmpty() }

                var iconBytes = payload
                if (iconBytes != null && iconBytes.isNotEmpty()) {
                    if (payloadHash != null) {
                        iconCacheByHash.put(payloadHash, iconBytes)
                    }
                    if (appName.isNotEmpty()) {
                        iconCacheByApp.put(appName.lowercase(), iconBytes)
                    }
                } else {
                    if (payloadHash != null) {
                        iconBytes = iconCacheByHash.get(payloadHash)
                    }
                    if (iconBytes == null && appName.isNotEmpty()) {
                        iconBytes = iconCacheByApp.get(appName.lowercase())
                    }
                }

                if (nId.isNotEmpty() && !isSilent && (title.isNotEmpty() || text.isNotEmpty())) {
                    mainHandler.post {
                        notificationListeners.forEach {
                            it.onNotificationReceived(
                                deviceId = conn.device.deviceId,
                                notificationId = nId,
                                appName = appName,
                                title = title,
                                text = text,
                                timestamp = time,
                                iconBytes = iconBytes,
                                isClearable = isClearable
                            )
                        }
                    }
                }
            }

            DeskConnectPacket.TYPE_NOTIFICATION_REQUEST -> {
                val cancelId = packet.body.optString("cancel", "")
                if (cancelId.isNotEmpty()) {
                    mainHandler.post {
                        notificationListeners.forEach {
                            it.onNotificationDismissed(conn.device.deviceId, cancelId)
                        }
                    }
                }
            }

            DeskConnectPacket.TYPE_TELEPHONY -> {
                if (!conn.device.isPaired) return
                val body = packet.body
                val event = body.optString("event", "")
                val phoneNumber = body.optString("phoneNumber", "")
                val contactName = body.optString("contactName", null)
                val phoneThumbnail = body.optString("phoneThumbnail", "").takeIf { it.isNotEmpty() }

                if (event.isNotEmpty()) {
                    mainHandler.post {
                        telephonyListeners.forEach {
                            it.onCallStateChanged(conn.device.deviceId, event, phoneNumber, contactName, phoneThumbnail)
                        }
                    }
                }
            }

            DeskConnectPacket.TYPE_BATTERY_REQUEST -> {
                if (!conn.device.isPaired) return
                val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

                val respBody = JSONObject().apply {
                    put("currentCharge", pct)
                    put("isCharging", isCharging)
                    put("thresholdEvent", 0)
                }
                conn.sendPacket(DeskConnectPacket(id = 0, type = DeskConnectPacket.TYPE_BATTERY, body = respBody))
            }

            DeskConnectPacket.TYPE_BATTERY -> {
                if (!conn.device.isPaired) return
                val body = packet.body
                val charge = body.optInt("currentCharge", -1)
                val isCharging = body.optBoolean("isCharging", false)
                val threshold = body.optInt("thresholdEvent", 0)

                if (charge >= 0) {
                    mainHandler.post {
                        batteryListeners.forEach {
                            it.onBatteryUpdated(conn.device.deviceId, charge, isCharging, threshold)
                        }
                    }
                }
            }

            DeskConnectPacket.TYPE_MPRIS -> {
                if (!conn.device.isPaired) return
                val body = packet.body
                val devName = conn.device.getDisplayName()

                Logger.i("DeskConnect/MPRIS") {
                    "MPRIS packet from '$devName' (${conn.device.deviceId}): " +
                    "body=$body, hasPayload=${payload != null} (${payload?.size ?: 0} bytes)"
                }

                if (body.optBoolean("transferringAlbumArt", false)) {
                    val player = body.optString("player", "")
                    val artUrl = body.optString("albumArtUrl", "")
                    Logger.i("DeskConnect/MPRIS") {
                        "Received transferringAlbumArt payload for player '$player' from '$devName': " +
                        "url='$artUrl', payloadSize=${payload?.size ?: 0} bytes"
                    }
                    if (payload != null && artUrl.isNotEmpty()) {
                        DeskConnectArtCache.getInstance(context).put(artUrl, payload)
                        mainHandler.post {
                            mprisListeners.forEach {
                                it.onAlbumArtTransferred(conn.device.deviceId, artUrl, payload)
                                if (player.isNotEmpty()) {
                                    it.onMprisUpdated(
                                        deviceId = conn.device.deviceId,
                                        player = player,
                                        title = null,
                                        artist = null,
                                        album = null,
                                        isPlaying = null,
                                        positionMs = null,
                                        lengthMs = null,
                                        artworkBytes = payload,
                                        artworkUrl = artUrl
                                    )
                                }
                            }
                        }
                    }
                    return
                }

                val playerListArray = body.optJSONArray("playerList")
                if (playerListArray != null) {
                    val players = mutableListOf<String>()
                    for (i in 0 until playerListArray.length()) {
                        val playerName = playerListArray.optString(i)
                        if (playerName.isNotEmpty()) {
                            players.add(playerName)
                            Logger.d("DeskConnect/MPRIS") { "Requesting player status for '$playerName' from '$devName'" }
                            conn.sendPacket(DeskConnectPacket.createMprisPlayerStatusRequest(playerName))
                        }
                    }
                    Logger.i("DeskConnect/MPRIS") { "Discovered players on '$devName': $players" }
                    mainHandler.post {
                        mprisListeners.forEach {
                            it.onPlayerListReceived(conn.device.deviceId, players)
                        }
                    }
                }

                if (body.has("player")) {
                    val player = body.optString("player", "Media Player")
                    val rawTitle = if (body.has("title")) body.optString("title").trim() else null
                    val rawArtist = if (body.has("artist")) body.optString("artist").trim() else null
                    val nowPlaying = if (body.has("nowPlaying")) body.optString("nowPlaying").trim() else null
                    val album = if (body.has("album")) body.optString("album").trim() else null
                    val isPlaying = if (body.has("isPlaying")) body.optBoolean("isPlaying") else null
                    val pos = if (body.has("pos")) body.optLong("pos") else null
                    val len = if (body.has("length")) body.optLong("length") else null
                    val albumArtUrl = if (body.has("albumArtUrl")) body.optString("albumArtUrl").takeIf { it.isNotEmpty() } else null

                    var trackTitle = rawTitle
                    var trackArtist = rawArtist

                    val webSuffixes = listOf(" - YouTube Music", " - YouTube", " - SoundCloud", " - Twitch")
                    for (suffix in webSuffixes) {
                        if (trackTitle != null && trackTitle.endsWith(suffix, ignoreCase = true)) {
                            trackTitle = trackTitle.substring(0, trackTitle.length - suffix.length).trim()
                            break
                        }
                    }

                    if (trackTitle.isNullOrEmpty() && !nowPlaying.isNullOrEmpty()) {
                        var np: String = nowPlaying
                        for (suffix in webSuffixes) {
                            if (np.endsWith(suffix, ignoreCase = true)) {
                                np = np.substring(0, np.length - suffix.length).trim()
                                break
                            }
                        }
                        if (np.contains(" - ")) {
                            trackArtist = np.substringBefore(" - ").trim()
                            trackTitle = np.substringAfter(" - ").trim()
                        } else {
                            trackTitle = np.trim()
                        }
                    } else if (trackArtist.isNullOrEmpty() && !trackTitle.isNullOrEmpty() && trackTitle.contains(" - ")) {
                        trackArtist = trackTitle.substringBefore(" - ").trim()
                        trackTitle = trackTitle.substringAfter(" - ").trim()
                    }

                    Logger.i("DeskConnect/MPRIS") {
                        "MPRIS update from '$devName' [player: $player]: " +
                        "title='$trackTitle', artist='$trackArtist', album='$album', isPlaying=$isPlaying, " +
                        "albumArtUrl='$albumArtUrl', payload=${payload?.size ?: 0} bytes"
                    }

                    if (albumArtUrl != null) {
                        val cachedBitmap = DeskConnectArtCache.getInstance(context).get(albumArtUrl)
                        if (cachedBitmap != null) {
                            Logger.d("DeskConnect/MPRIS") { "albumArtUrl for '$player' already cached in memory: $albumArtUrl" }
                        } else if (albumArtUrl.startsWith("http://") || albumArtUrl.startsWith("https://")) {
                            Logger.d("DeskConnect/MPRIS") { "albumArtUrl for '$player' is direct HTTP(S) URL: $albumArtUrl, prefetching..." }
                            DeskConnectArtCache.getInstance(context).fetchHttpArt(albumArtUrl)
                        } else {
                            Logger.i("DeskConnect/MPRIS") {
                                "albumArtUrl for '$player' is remote/local URI ('$albumArtUrl'). " +
                                "Requesting album art payload transfer from '$devName'..."
                            }
                            conn.sendPacket(DeskConnectPacket.createMprisAlbumArtRequest(player, albumArtUrl))
                        }
                    } else {
                        Logger.d("DeskConnect/MPRIS") { "Device '$devName' did not provide any albumArtUrl for '$player'" }
                    }

                    if (trackTitle.isNullOrEmpty() && trackArtist.isNullOrEmpty() && isPlaying == true) {
                        Logger.d("DeskConnect/MPRIS") { "Player '$player' is playing but has no metadata, requesting status..." }
                        conn.sendPacket(DeskConnectPacket.createMprisPlayerStatusRequest(player))
                    }

                    mainHandler.post {
                        mprisListeners.forEach {
                            it.onMprisUpdated(
                                deviceId = conn.device.deviceId,
                                player = player,
                                title = trackTitle,
                                artist = trackArtist,
                                album = album,
                                isPlaying = isPlaying,
                                positionMs = pos,
                                lengthMs = len,
                                artworkBytes = payload,
                                artworkUrl = albumArtUrl
                            )
                        }
                    }
                }
            }

            DeskConnectPacket.TYPE_PING -> {
                val msg = packet.body.optString("message", "")
                if (msg.isNotBlank()) {
                    mainHandler.post {
                        Toast.makeText(context, "${conn.device.getDisplayName()}: $msg", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleDisconnected(conn: DeskConnectConnection) {
        val dev = conn.device
        if (conn.isReplaced) {
            Logger.d("DeskConnect/Connection") {
                "Old connection socket closed (superseded by new link) for ${dev.deviceName} (${dev.deviceId})"
            }
            return
        }

        if (activeConnections[dev.deviceId] == conn) {
            activeConnections.remove(dev.deviceId)
            discovery?.onDeviceDisconnected(dev.deviceId)
            // Wait 1000ms grace period: if the peer reconnected with a new socket during renegotiation,
            // don't drop media player state or broadcast false disconnects
            mainHandler.postDelayed({
                val currentConn = activeConnections[dev.deviceId]
                if (currentConn == null || !currentConn.isAlive()) {
                    dev.isConnected = false
                    Logger.i("DeskConnect/Connection") {
                        "Device disconnected: ${dev.deviceName} (${dev.deviceId}) [ip=${dev.ipAddress?.hostAddress ?: "unknown"}]"
                    }
                    deviceListeners.forEach { it.onDeviceDisconnected(dev) }
                } else {
                    Logger.d("DeskConnect/Connection") {
                        "Device ${dev.deviceName} (${dev.deviceId}) connection gracefully maintained by newer socket"
                    }
                }
            }, 1000L)
        }
    }

    fun sendPing(deviceId: String, message: String = "Ping!") {
        val conn = activeConnections[deviceId]
        if (conn != null && conn.device.isPaired) {
            conn.sendPacket(DeskConnectPacket.createPing(message))
        }
    }

    fun requestPair(device: DeskConnectDevice) {
        pendingOutgoingPairRequests.add(device.deviceId)
        val conn = activeConnections[device.deviceId]
        if (conn != null) {
            conn.sendPacket(DeskConnectPacket.createPair(true))
        } else {
            connectToDevice(device)
            mainHandler.postDelayed({
                activeConnections[device.deviceId]?.sendPacket(DeskConnectPacket.createPair(true))
            }, 800)
        }
    }

    fun acceptPair(device: DeskConnectDevice) {
        pendingIncomingPairRequests.remove(device.deviceId)
        if (device.certificate != null) {
            security.trustDevice(device.deviceId, device.certificate!!)
        } else {
            security.trustDevice(device.deviceId, security.certificate)
        }
        device.isPaired = true
        sharedPrefs.edit()
            .putString("deskconnect_paired_name_${device.deviceId}", device.deviceName)
            .putString("deskconnect_paired_type_${device.deviceId}", device.deviceType)
            .apply()

        val conn = activeConnections[device.deviceId]
        conn?.sendPacket(DeskConnectPacket.createPair(true))
        conn?.sendPacket(DeskConnectPacket.createMprisPlayerListRequest())

        mainHandler.post {
            deviceListeners.forEach { it.onPairingStateChanged(device, true) }
        }
    }

    fun rejectPair(device: DeskConnectDevice) {
        pendingIncomingPairRequests.remove(device.deviceId)
        activeConnections[device.deviceId]?.sendPacket(DeskConnectPacket.createPair(false))
        mainHandler.post {
            deviceListeners.forEach { it.onPairingStateChanged(device, false) }
        }
    }

    fun unpairDevice(deviceId: String) {
        pendingOutgoingPairRequests.remove(deviceId)
        pendingIncomingPairRequests.remove(deviceId)
        security.unpairDevice(deviceId)
        sharedPrefs.edit()
            .remove("deskconnect_paired_name_$deviceId")
            .remove("deskconnect_paired_type_$deviceId")
            .apply()

        val dev = discoveredDevices[deviceId]
        if (dev != null) {
            dev.isPaired = false
            mainHandler.post {
                deviceListeners.forEach { it.onPairingStateChanged(dev, false) }
            }
        }

        activeConnections[deviceId]?.sendPacket(DeskConnectPacket.createPair(false))
    }

    fun sendMprisAction(player: String, action: String) {
        val packet = DeskConnectPacket.createMprisRequest(player, action)
        activeConnections.values.forEach { conn ->
            if (conn.device.isPaired) {
                conn.sendPacket(packet)
            }
        }
    }

    fun dismissNotification(deviceId: String, notificationId: String) {
        val conn = activeConnections[deviceId]
        if (conn != null && conn.device.isPaired) {
            conn.sendPacket(DeskConnectPacket.createNotificationCancel(notificationId))
        }
        mainHandler.post {
            notificationListeners.forEach {
                it.onNotificationDismissed(deviceId, notificationId)
            }
        }
    }
}
