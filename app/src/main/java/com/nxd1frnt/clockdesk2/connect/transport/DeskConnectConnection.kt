package com.nxd1frnt.clockdesk2.connect.transport

import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectPacket
import com.nxd1frnt.clockdesk2.utils.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DeskConnectConnection(
    val socket: Socket,
    var device: DeskConnectDevice,
    private val onPacketReceived: (DeskConnectConnection, DeskConnectPacket, ByteArray?) -> Unit,
    private val onDisconnected: (DeskConnectConnection) -> Unit
) {
    private val isRunning = AtomicBoolean(true)
    private var readerThread: Thread? = null
    private var outputStream: OutputStream? = null
    private val sendExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DeskConnect-Sender-${device.deviceId.take(6)}")
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
                    onPacketReceived(this, packet, null)
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                Logger.d("DeskConnectConnection") { "Connection terminated for ${device.deviceId}: ${e.message}" }
            }
        } finally {
            close()
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
        try {
            sendExecutor.shutdownNow()
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
