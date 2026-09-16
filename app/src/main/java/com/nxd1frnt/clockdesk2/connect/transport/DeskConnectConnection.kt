package com.nxd1frnt.clockdesk2.connect.transport

import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectPacket
import com.nxd1frnt.clockdesk2.connect.security.DeskConnectSecurity
import com.nxd1frnt.clockdesk2.utils.Logger
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DeskConnectConnection(
    val socket: Socket,
    var device: DeskConnectDevice,
    private val security: DeskConnectSecurity,
    private val onPacketReceived: (DeskConnectConnection, DeskConnectPacket, ByteArray?) -> Unit,
    private val onDisconnected: (DeskConnectConnection) -> Unit
) {
    private val isRunning = AtomicBoolean(true)
    @Volatile
    var isReplaced: Boolean = false

    fun isAlive(): Boolean = isRunning.get() && !socket.isClosed

    private var readerThread: Thread? = null
    private var outputStream: OutputStream? = null
    private val sendExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DeskConnect-Sender-${device.deviceId.take(6)}")
    }
    private val payloadExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "DeskConnect-Payload-${device.deviceId.take(6)}")
    }

    init {
        try {
            socket.keepAlive = true
            socket.tcpNoDelay = true
            outputStream = socket.getOutputStream()
        } catch (e: Exception) {
            Logger.e("DeskConnectConnection") { "Error initializing socket options: ${e.message}" }
        }

        readerThread = Thread {
            readLoop()
        }.apply {
            name = "DeskConnect-Reader-${device.deviceId.take(6)}"
            start()
        }
    }

    private fun readLoop() {
        try {
            val inputStream = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))

            while (isRunning.get() && !socket.isClosed) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                val packet = DeskConnectPacket.fromJson(line)
                if (packet != null) {
                    processIncomingPacket(packet)
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                Logger.i("DeskConnect/Connection") { "Connection to ${device.deviceName} (${device.deviceId}) terminated: ${e.message ?: e.javaClass.simpleName}" }
            }
        } finally {
            close()
        }
    }

    private fun processIncomingPacket(packet: DeskConnectPacket) {
        val payloadPort = packet.payloadTransferInfo?.optInt("port", -1) ?: -1
        if (packet.payloadSize > 0 && payloadPort > 0) {
            payloadExecutor.execute {
                var payloadBytes: ByteArray? = null
                try {
                    val remoteAddress = (socket.remoteSocketAddress as? InetSocketAddress)?.address
                        ?: device.ipAddress
                        ?: socket.inetAddress

                    val plainSocket = Socket()
                    plainSocket.connect(InetSocketAddress(remoteAddress, payloadPort), 8000)
                    plainSocket.soTimeout = 8000

                    val sslSocket = security.convertToSslSocket(plainSocket, clientMode = true)
                    sslSocket.startHandshake()

                    val inStream = sslSocket.getInputStream()
                    val baos = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var totalRead = 0L
                    val targetSize = packet.payloadSize

                    while (totalRead < targetSize) {
                        val toRead = minOf(buffer.size.toLong(), targetSize - totalRead).toInt()
                        val bytesRead = inStream.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        baos.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                    payloadBytes = baos.toByteArray()
                    sslSocket.close()
                    Logger.d("DeskConnectConnection") { "Successfully fetched payload for ${packet.type} (${payloadBytes.size} bytes) from ${device.deviceId}" }
                } catch (e: Exception) {
                    Logger.w("DeskConnectConnection") { "Failed to fetch payload on port $payloadPort for ${packet.type}: ${e.message}" }
                }

                if (isRunning.get()) {
                    onPacketReceived(this, packet, payloadBytes)
                }
            }
        } else {
            onPacketReceived(this, packet, null)
        }
    }

    fun sendPacket(packet: DeskConnectPacket, onComplete: ((Boolean) -> Unit)? = null) {
        if (!isRunning.get() || socket.isClosed) {
            onComplete?.invoke(false)
            return
        }

        sendExecutor.execute {
            try {
                val json = packet.toJson() + "\n"
                val data = json.toByteArray(StandardCharsets.UTF_8)
                synchronized(this) {
                    outputStream?.write(data)
                    outputStream?.flush()
                }
                onComplete?.invoke(true)
            } catch (e: Exception) {
                Logger.e("DeskConnectConnection") { "Error sending packet to ${device.deviceId}: ${e.message}" }
                close()
                onComplete?.invoke(false)
            }
        }
    }

    fun close() {
        if (!isRunning.getAndSet(false)) return
        Logger.d("DeskConnect/Connection") { "Closing socket for ${device.deviceName} (${device.deviceId}) [replaced=$isReplaced]" }
        try {
            sendExecutor.shutdownNow()
        } catch (e: Exception) {
            // Ignored
        }
        try {
            payloadExecutor.shutdownNow()
        } catch (e: Exception) {
            // Ignored
        }
        try {
            socket.close()
        } catch (e: Exception) {
            // Ignored
        }
        onDisconnected(this)
    }
}
