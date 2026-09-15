package com.nxd1frnt.clockdesk2.connect.transport

import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectPacket
import com.nxd1frnt.clockdesk2.connect.security.DeskConnectSecurity
import com.nxd1frnt.clockdesk2.utils.Logger
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket

class DeskConnectServer(
    private val security: DeskConnectSecurity,
    private val customDeviceName: String,
    private val onIncomingConnection: (SSLSocket, X509Certificate?, String, DeskConnectPacket) -> Unit
) {
    companion object {
        const val DEFAULT_PORT = 1716
        const val MAX_PORT = 1764
        private const val MAX_LINE_BYTES = 512 * 1024
    }

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    var boundPort: Int = DEFAULT_PORT
        private set

    fun start(): Boolean {
        if (isRunning.getAndSet(true)) return true

        var port = DEFAULT_PORT
        var bound = false
        while (port <= MAX_PORT && !bound) {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                boundPort = port
                bound = true
                Logger.d("DeskConnectServer") { "TCP Server bound to port $boundPort" }
            } catch (e: Exception) {
                port++
            }
        }

        if (!bound) {
            Logger.e("DeskConnectServer") { "Failed to bind to any TCP port between $DEFAULT_PORT and $MAX_PORT" }
            isRunning.set(false)
            return false
        }

        serverThread = Thread {
            runServerLoop()
        }.apply {
            name = "DeskConnect-TCP-Server"
            start()
        }
        return true
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignored
        }
        serverSocket = null
        serverThread?.interrupt()
        serverThread = null
        Logger.d("DeskConnectServer") { "TCP Server stopped" }
    }

    private fun runServerLoop() {
        val s = serverSocket ?: return
        while (isRunning.get() && !s.isClosed) {
            try {
                val clientSocket = s.accept()
                Logger.d("DeskConnectServer") { "Accepted incoming TCP socket from ${clientSocket.inetAddress}:${clientSocket.port}" }
                Thread {
                    handleIncomingSocket(clientSocket)
                }.start()
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Logger.e("DeskConnectServer") { "Error accepting client: ${e.message}" }
                }
            }
        }
    }

    private fun readLineBounded(inputStream: InputStream): String {
        val baos = ByteArrayOutputStream()
        while (baos.size() < MAX_LINE_BYTES) {
            val b = inputStream.read()
            if (b == -1 || b == '\n'.code) break
            if (b != '\r'.code) {
                baos.write(b)
            }
        }
        return baos.toString(StandardCharsets.UTF_8.name())
    }

    private fun handleIncomingSocket(plainSocket: Socket) {
        try {
            plainSocket.soTimeout = 10000

            // 1. Read unencrypted identity packet
            val line = readLineBounded(plainSocket.getInputStream())
            if (line.isBlank()) {
                throw IllegalStateException("Received empty identity line")
            }

            val packet = DeskConnectPacket.fromJson(line) ?: throw IllegalStateException("Invalid JSON in identity packet: $line")
            if (packet.type != DeskConnectPacket.TYPE_IDENTITY) {
                throw IllegalStateException("Expected identity packet, got ${packet.type}")
            }

            val remoteDeviceId = packet.body.optString("deviceId", "")
            if (remoteDeviceId.isEmpty() || remoteDeviceId == security.deviceId) {
                plainSocket.close()
                return
            }

            Logger.d("DeskConnectServer") { "Received pre-TLS identity from '$remoteDeviceId', upgrading to SSL (as SSL Client)..." }

            // 2. Upgrade to SSL (In KDE Connect rules: TCP Server acts as SSL Client)
            var peerCert: X509Certificate? = null
            val sslSocket = security.convertToSslSocket(
                socket = plainSocket,
                clientMode = true,
                onPeerCertReceived = { cert -> peerCert = cert }
            )

            sslSocket.startHandshake()

            val certs = sslSocket.session.peerCertificates
            if (!certs.isNullOrEmpty() && certs[0] is X509Certificate) {
                peerCert = certs[0] as X509Certificate
            }

            sslSocket.soTimeout = 0 // Infinite timeout for active connection
            Logger.d("DeskConnectServer") { "SSL Handshake successful (as Client) with $remoteDeviceId" }

            onIncomingConnection(sslSocket, peerCert, remoteDeviceId, packet)
        } catch (e: Exception) {
            Logger.e("DeskConnectServer") { "Failed incoming connection handshake from ${plainSocket.inetAddress}: ${e.message}" }
            try {
                plainSocket.close()
            } catch (ignored: Exception) {}
        }
    }

    fun connectToDevice(
        device: DeskConnectDevice,
        onConnected: (SSLSocket, X509Certificate?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        Thread {
            try {
                val targetAddress = device.ipAddress ?: throw IllegalArgumentException("Device IP address is null")
                val plainSocket = Socket()
                plainSocket.reuseAddress = true
                plainSocket.connect(InetSocketAddress(targetAddress, device.tcpPort), 10000)
                plainSocket.soTimeout = 10000

                // 1. Send unencrypted identity line
                val myIdentity = DeskConnectPacket.createFullIdentity(
                    deviceId = security.deviceId,
                    deviceName = customDeviceName,
                    deviceType = "tablet",
                    tcpPort = boundPort
                )
                val json = myIdentity.toJson() + "\n"
                val out = plainSocket.getOutputStream()
                out.write(json.toByteArray(StandardCharsets.UTF_8))
                out.flush()

                Logger.d("DeskConnectServer") { "Sent pre-TLS identity to ${device.deviceId}, upgrading to SSL (as SSL Server)..." }

                // 2. Upgrade to SSL (In KDE Connect rules: TCP Client acts as SSL Server)
                var peerCert: X509Certificate? = null
                val sslSocket = security.convertToSslSocket(
                    socket = plainSocket,
                    clientMode = false,
                    onPeerCertReceived = { cert -> peerCert = cert }
                )

                sslSocket.startHandshake()

                val certs = sslSocket.session.peerCertificates
                if (!certs.isNullOrEmpty() && certs[0] is X509Certificate) {
                    peerCert = certs[0] as X509Certificate
                }

                sslSocket.soTimeout = 0
                Logger.d("DeskConnectServer") { "SSL Handshake successful (as Server) with ${device.deviceId}" }

                onConnected(sslSocket, peerCert)
            } catch (e: Exception) {
                Logger.e("DeskConnectServer") { "Failed outgoing connection to ${device.deviceId} at ${device.ipAddress}:${device.tcpPort}: ${e.message}" }
                onError(e)
            }
        }.start()
    }
}
