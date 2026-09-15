package com.nxd1frnt.clockdesk2.connect.model

import com.nxd1frnt.clockdesk2.R
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

    fun getDeviceTypeDisplayName(): String {
        return when (deviceType.lowercase()) {
            "desktop" -> "Desktop"
            "laptop" -> "Laptop"
            "tablet" -> "Tablet"
            "tv" -> "TV"
            "phone" -> "Phone"
            else -> deviceType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    fun getDeviceTypeIconRes(): Int {
        return when (deviceType.lowercase()) {
            "desktop" -> R.drawable.ic_desktop
            "laptop" -> R.drawable.ic_laptop
            "tablet" -> R.drawable.ic_tablet
            "tv" -> R.drawable.ic_tv
            "phone" -> R.drawable.ic_phone
            else -> R.drawable.ic_devices
        }
    }
}
