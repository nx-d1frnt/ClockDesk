package com.nxd1frnt.clockdesk2.connect

import android.content.Context
import android.content.SharedPreferences
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
            phoneThumbnailBase64: String? = null
        )
    }

    interface BatteryListener {
        fun onBatteryUpdated(deviceId: String, currentCharge: Int, isCharging: Boolean, thresholdEvent: Int)
    }

    interface MprisListener {
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
        )

        fun onPlayerListReceived(
            deviceId: String,
            playerList: List<String>
        ) {}
    }

    val deviceListeners = CopyOnWriteArrayList<DeviceListener>()
    val notificationListeners = CopyOnWriteArrayList<NotificationListener>()
    val telephonyListeners = CopyOnWriteArrayList<TelephonyListener>()
    val batteryListeners = CopyOnWriteArrayList<BatteryListener>()
    val mprisListeners = CopyOnWriteArrayList<MprisListener>()

    val isEnabled: Boolean
        get() = sharedPrefs.getBoolean("deskconnect_enabled", true)

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
            tcpPort = portToAdvertise
        ) { discoveredDevice ->
            handleDiscoveredDevice(discoveredDevice)
        }.apply {
            start()
        }

        Logger.d("DeskConnectManager") { "DeskConnect service started with deviceId '${security.deviceId}' and name '$customDeviceName' on TCP $portToAdvertise" }
    }

    fun stop() {
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
            oldConn.close()
        }

        if (device.isPaired) {
            connection.sendPacket(DeskConnectPacket.createMprisPlayerListRequest())
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
                oldConn.close()
            }

            if (device.isPaired) {
                connection.sendPacket(DeskConnectPacket.createMprisPlayerListRequest())
            }

            mainHandler.post {
                deviceListeners.forEach { it.onDeviceConnected(device) }
            }
        }, onError = {
            connectingDevices.remove(device.deviceId)
            device.isConnected = false
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
                device.isConnected = true
                device.isPaired = security.isDevicePaired(remoteId)
                conn.device = device

                val oldConn = activeConnections.put(remoteId, conn)
                if (oldConn != null && oldConn != conn) {
                    oldConn.close()
                }

                if (device.isPaired) {
                    conn.sendPacket(DeskConnectPacket.createMprisPlayerListRequest())
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

                val playerListArray = body.optJSONArray("playerList")
                if (playerListArray != null) {
                    val players = mutableListOf<String>()
                    for (i in 0 until playerListArray.length()) {
                        val playerName = playerListArray.optString(i)
                        if (playerName.isNotEmpty()) {
                            players.add(playerName)
                            conn.sendPacket(DeskConnectPacket.createMprisPlayerStatusRequest(playerName))
                        }
                    }
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

                    if (trackTitle.isNullOrEmpty() && !nowPlaying.isNullOrEmpty()) {
                        if (nowPlaying.contains(" - ")) {
                            trackArtist = nowPlaying.substringBefore(" - ").trim()
                            trackTitle = nowPlaying.substringAfter(" - ").trim()
                        } else {
                            trackTitle = nowPlaying.trim()
                        }
                    } else if (trackArtist.isNullOrEmpty() && !trackTitle.isNullOrEmpty() && trackTitle.contains(" - ")) {
                        trackArtist = trackTitle.substringBefore(" - ").trim()
                        trackTitle = trackTitle.substringAfter(" - ").trim()
                    }

                    if (trackTitle.isNullOrEmpty() && trackArtist.isNullOrEmpty() && isPlaying == true) {
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
                val msg = packet.body.optString("message", "Ping!")
                mainHandler.post {
                    Toast.makeText(context, "${conn.device.getDisplayName()}: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleDisconnected(conn: DeskConnectConnection) {
        val dev = conn.device
        if (activeConnections[dev.deviceId] == conn) {
            activeConnections.remove(dev.deviceId)
            dev.isConnected = false
            mainHandler.post {
                deviceListeners.forEach { it.onDeviceDisconnected(dev) }
            }
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
    }
}
