package com.nxd1frnt.clockdesk2.connect.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectPacket
import com.nxd1frnt.clockdesk2.connect.security.DeskConnectSecurity
import com.nxd1frnt.clockdesk2.utils.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DeskConnectDiscovery(
    private val context: Context,
    private val security: DeskConnectSecurity,
    private val deviceName: String,
    private val tcpPort: Int = 1716,
    private val shouldBroadcast: (() -> Boolean)? = null,
    private val isDeviceConnected: ((String) -> Boolean)? = null,
    private val onDeviceDiscovered: (DeskConnectDevice) -> Unit
) {
    companion object {
        const val UDP_PORT = 1716
        private const val BUFFER_SIZE = 8192
        private const val UNICAST_REPLY_THROTTLE_MS = 15000L
    }

    private val isRunning = AtomicBoolean(false)
    private var receiveSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var listenerThread: Thread? = null
    private var broadcasterThread: Thread? = null

    private val udpExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DeskConnect-UDP-Worker")
    }

    private val lastRepliedTime = ConcurrentHashMap<String, Long>()

    fun start() {
        if (isRunning.getAndSet(true)) return

        acquireMulticastLock()

        listenerThread = Thread {
            runUdpListener()
        }.apply {
            name = "DeskConnect-UDP-Listener"
            start()
        }

        broadcasterThread = Thread {
            runBroadcaster()
        }.apply {
            name = "DeskConnect-UDP-Broadcaster"
            start()
        }

        Logger.d("DeskConnectDiscovery") { "Discovery started on UDP port $UDP_PORT (advertising TCP port $tcpPort)" }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        try {
            receiveSocket?.close()
        } catch (e: Exception) {
            // Ignored
        }
        receiveSocket = null

        listenerThread?.interrupt()
        listenerThread = null

        broadcasterThread?.interrupt()
        broadcasterThread = null

        try {
            udpExecutor.shutdownNow()
        } catch (e: Exception) {
            // Ignored
        }

        releaseMulticastLock()
        lastRepliedTime.clear()
        Logger.d("DeskConnectDiscovery") { "Discovery stopped" }
    }

    fun broadcastIdentity() {
        udpExecutor.execute {
            sendBroadcastPacket()
        }
    }

    private fun runUdpListener() {
        try {
            val sock = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(UDP_PORT))
            }
            receiveSocket = sock

            val buffer = ByteArray(BUFFER_SIZE)
            while (isRunning.get() && !sock.isClosed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    sock.receive(packet)

                    val jsonStr = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8).trim()
                    val deskPacket = DeskConnectPacket.fromJson(jsonStr)

                    if (deskPacket != null && deskPacket.type == DeskConnectPacket.TYPE_IDENTITY) {
                        handleIdentityPacket(deskPacket, packet.address)
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Logger.w("DeskConnectDiscovery") { "Error receiving UDP packet: ${e.message}" }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("DeskConnectDiscovery") { "Failed to bind UDP socket on $UDP_PORT: ${e.message}" }
        }
    }

    private fun handleIdentityPacket(packet: DeskConnectPacket, remoteAddress: InetAddress) {
        val body = packet.body
        val remoteDeviceId = body.optString("deviceId", "")
        if (remoteDeviceId.isEmpty() || remoteDeviceId == security.deviceId) {
            return
        }

        val remoteName = body.optString("deviceName", "Device ${remoteDeviceId.take(6)}")
        val deviceType = body.optString("deviceType", "phone")
        val remoteTcpPort = body.optInt("tcpPort", 1716)
        val protocolVersion = body.optInt("protocolVersion", 7)

        val incomingCaps = mutableSetOf<String>()
        val inCapsArray = body.optJSONArray("incomingCapabilities")
        if (inCapsArray != null) {
            for (i in 0 until inCapsArray.length()) {
                incomingCaps.add(inCapsArray.getString(i))
            }
        }

        val outgoingCaps = mutableSetOf<String>()
        val outCapsArray = body.optJSONArray("outgoingCapabilities")
        if (outCapsArray != null) {
            for (i in 0 until outCapsArray.length()) {
                outgoingCaps.add(outCapsArray.getString(i))
            }
        }

        val device = DeskConnectDevice(
            deviceId = remoteDeviceId,
            deviceName = remoteName,
            deviceType = deviceType,
            ipAddress = remoteAddress,
            tcpPort = remoteTcpPort,
            protocolVersion = protocolVersion,
            incomingCapabilities = incomingCaps,
            outgoingCapabilities = outgoingCaps,
            isPaired = security.isDevicePaired(remoteDeviceId),
            lastSeenTimestamp = System.currentTimeMillis()
        )

        // Do not send unicast reply if already connected over TCP (prevents connection thrashing)
        val isConnected = isDeviceConnected?.invoke(remoteDeviceId) ?: false
        if (!isConnected) {
            val now = System.currentTimeMillis()
            val lastReplied = lastRepliedTime[remoteDeviceId] ?: 0L
            if (now - lastReplied > UNICAST_REPLY_THROTTLE_MS) {
                lastRepliedTime[remoteDeviceId] = now
                sendUnicastIdentity(remoteAddress, UDP_PORT)
            }
        }

        onDeviceDiscovered(device)
    }

    private fun sendUnicastIdentity(targetAddress: InetAddress, port: Int) {
        udpExecutor.execute {
            try {
                val identityPacket = DeskConnectPacket.createFullIdentity(
                    deviceId = security.deviceId,
                    deviceName = deviceName,
                    deviceType = "tablet",
                    tcpPort = tcpPort
                )
                val json = identityPacket.toJson() + "\n"
                val data = json.toByteArray(StandardCharsets.UTF_8)
                val p = DatagramPacket(data, data.size, targetAddress, port)

                DatagramSocket().use { sendSocket ->
                    sendSocket.send(p)
                }
            } catch (e: Exception) {
                Logger.w("DeskConnectDiscovery") { "Failed to send unicast identity to $targetAddress: ${e.message}" }
            }
        }
    }

    private fun runBroadcaster() {
        // Initial burst: announce ourselves immediately on startup
        sendBroadcastPacket()
        try {
            Thread.sleep(2000)
        } catch (e: InterruptedException) {
            return
        }
        if (!isRunning.get()) return
        // Send a 2nd packet to ensure initial discovery even with Wi-Fi packet drops
        sendBroadcastPacket()

        // Background maintenance loop: ONLY broadcast if we have disconnected/unpaired peers,
        // and do it at a conservative 60-second interval to avoid resetting active connections.
        while (isRunning.get()) {
            try {
                Thread.sleep(60000)
            } catch (e: InterruptedException) {
                break
            }
            if (!isRunning.get()) break

            if (shouldBroadcast?.invoke() != false) {
                sendBroadcastPacket()
            }
        }
    }

    private fun sendBroadcastPacket() {
        try {
            val identityPacket = DeskConnectPacket.createFullIdentity(
                deviceId = security.deviceId,
                deviceName = deviceName,
                deviceType = "tablet",
                tcpPort = tcpPort
            )
            val json = identityPacket.toJson() + "\n"
            val data = json.toByteArray(StandardCharsets.UTF_8)

            val broadcastAddresses = getBroadcastAddresses()

            DatagramSocket().use { sendSocket ->
                sendSocket.broadcast = true
                for (addr in broadcastAddresses) {
                    try {
                        val p = DatagramPacket(data, data.size, addr, UDP_PORT)
                        sendSocket.send(p)
                    } catch (e: Exception) {
                        // Ignore individual network interface failures
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("DeskConnectDiscovery") { "Failed to send identity broadcast: ${e.message}" }
        }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) {
                        list.add(broadcast)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("DeskConnectDiscovery") { "Error retrieving broadcast addresses: ${e.message}" }
        }

        if (list.isEmpty()) {
            try {
                list.add(InetAddress.getByName("255.255.255.255"))
            } catch (ignored: Exception) {}
        }
        return list
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("DeskConnectMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Logger.w("DeskConnectDiscovery") { "Failed to acquire multicast lock: ${e.message}" }
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            multicastLock = null
        } catch (e: Exception) {
            Logger.w("DeskConnectDiscovery") { "Failed to release multicast lock: ${e.message}" }
        }
    }
}
