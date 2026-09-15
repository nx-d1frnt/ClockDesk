package com.nxd1frnt.clockdesk2.connect.model

import java.net.InetAddress
import java.security.cert.X509Certificate

data class DeskConnectDevice(
    val deviceId: String,
    var deviceName: String,
    var deviceType: String = "phone",
    var ipAddress: InetAddress? = null,
    var tcpPort: Int = 1716,
    var protocolVersion: Int = 7,
    var incomingCapabilities: Set<String> = emptySet(),
    var outgoingCapabilities: Set<String> = emptySet(),
    var isPaired: Boolean = false,
    var isConnected: Boolean = false,
    var lastSeenTimestamp: Long = System.currentTimeMillis(),
    var certificate: X509Certificate? = null
) {
    fun getDisplayName(): String {
        return if (deviceName.isNotBlank()) deviceName else deviceId
    }
}
