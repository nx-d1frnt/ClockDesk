package com.nxd1frnt.clockdesk2.connect.model

data class DeskNotification(
    val deviceId: String,
    val notificationId: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val iconBytes: ByteArray?,
    val isClearable: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeskNotification

        if (deviceId != other.deviceId) return false
        if (notificationId != other.notificationId) return false
        if (appName != other.appName) return false
        if (title != other.title) return false
        if (text != other.text) return false
        if (timestamp != other.timestamp) return false
        if (isClearable != other.isClearable) return false
        if (iconBytes != null) {
            if (other.iconBytes == null) return false
            if (!iconBytes.contentEquals(other.iconBytes)) return false
        } else if (other.iconBytes != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + notificationId.hashCode()
        result = 31 * result + appName.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (iconBytes?.contentHashCode() ?: 0)
        result = 31 * result + isClearable.hashCode()
        return result
    }
}
