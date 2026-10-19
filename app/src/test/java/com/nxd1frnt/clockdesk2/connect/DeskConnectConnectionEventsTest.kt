package com.nxd1frnt.clockdesk2.connect

import android.content.Context
import android.os.Looper
import androidx.preference.PreferenceManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectPacket
import com.nxd1frnt.clockdesk2.connect.transport.DeskConnectConnection
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Socket
import java.time.Duration
import java.util.concurrent.CountDownLatch

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class DeskConnectConnectionEventsTest {
    private lateinit var manager: DeskConnectManager
    private val events = mutableListOf<String>()
    private val connections = mutableListOf<DeskConnectConnection>()
    private val device = DeskConnectDevice("phone", "Phone")

    @Before fun setUp() {
        val context = RuntimeEnvironment.getApplication() as Context
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("deskconnect_enabled", false).commit()
        manager = DeskConnectManager::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }.newInstance(context)
        manager.discoveredDevices[device.deviceId] = device
        manager.deviceListeners.add(object : DeskConnectManager.DeviceListener {
            override fun onDeviceDiscovered(device: DeskConnectDevice) {}
            override fun onDeviceConnected(device: DeskConnectDevice) { events.add("connected") }
            override fun onDeviceDisconnected(device: DeskConnectDevice) { events.add("disconnected") }
            override fun onPairingRequested(device: DeskConnectDevice, verificationKey: String) {}
            override fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean) {}
        })
    }

    @After fun tearDown() {
        connections.forEach { it.close() }
        manager.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
    }

    @Test fun identitiesReceivedAfterChipDismissalDoNotAnnounceAnotherConnection() {
        val connection = connect()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        repeat(3) { identity(connection) }
        assertEquals(listOf("connected"), events)
    }

    @Test fun socketReplacementAndLateCloseOfOldSocketDoNotAnnounceConnectionChanges() {
        val old = connect()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        connect()
        old.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf("connected"), events)
    }

    @Test fun reconnectDuringGracePeriodPreservesLogicalConnection() {
        connect().close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        connect()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf("connected"), events)
    }

    @Test fun reconnectAfterGracePeriodAnnouncesDisconnectAndNewConnection() {
        connect().close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1001))
        connect()
        assertEquals(listOf("connected", "disconnected", "connected"), events)
    }

    private fun connect(): DeskConnectConnection {
        val connection = DeskConnectConnection(
            TestSocket(), device, manager.security,
            onPacketReceived = { _, _, _ -> },
            onDisconnected = { disconnected ->
                DeskConnectManager::class.java.getDeclaredMethod(
                    "handleDisconnected", DeskConnectConnection::class.java
                ).apply { isAccessible = true }.invoke(manager, disconnected)
            }
        )
        connections.add(connection)
        manager.activeConnections[device.deviceId] = connection
        identity(connection)
        return connection
    }

    private fun identity(connection: DeskConnectConnection) {
        val packet = DeskConnectPacket(
            type = DeskConnectPacket.TYPE_IDENTITY,
            body = JSONObject().put("deviceId", device.deviceId).put("deviceName", device.deviceName)
        )
        DeskConnectManager::class.java.getDeclaredMethod(
            "handlePacket", DeskConnectConnection::class.java,
            DeskConnectPacket::class.java, ByteArray::class.java
        ).apply { isAccessible = true }.invoke(manager, connection, packet, null)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Keep the reader blocked until close, without opening any real network connection. */
    private class TestSocket : Socket() {
        private val closed = CountDownLatch(1)
        private val output = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = object : InputStream() {
            override fun read(): Int { closed.await(); return -1 }
        }
        override fun getOutputStream() = output
        override fun isClosed(): Boolean = closed.count == 0L
        override fun close() { closed.countDown() }
    }
}
