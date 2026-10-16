package com.nxd1frnt.clockdesk2.connect.repo

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskNotification
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class DeskNotificationRepository private constructor(private val context: Context) {

    fun interface OnNotificationsChangedListener {
        fun onNotificationsChanged(notifications: List<DeskNotification>)
    }

    private val deskConnectManager = DeskConnectManager.getInstance(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeNotifications = ConcurrentHashMap<String, DeskNotification>()
    private val listeners = CopyOnWriteArrayList<OnNotificationsChangedListener>()

    private fun key(deviceId: String, notificationId: String): String = "$deviceId:$notificationId"

    private val notificationListener = object : DeskConnectManager.NotificationListener {
        override fun onNotificationReceived(
            deviceId: String,
            notificationId: String,
            appName: String,
            title: String,
            text: String,
            timestamp: Long,
            iconBytes: ByteArray?,
            isClearable: Boolean
        ) {
            val k = key(deviceId, notificationId)
            val old = activeNotifications[k]
            if (old != null &&
                old.appName == appName &&
                old.title == title &&
                old.text == text &&
                old.isClearable == isClearable &&
                (if (old.iconBytes == null && iconBytes == null) true
                 else if (old.iconBytes != null && iconBytes != null) old.iconBytes.contentEquals(iconBytes)
                 else false)
            ) {
                return // Exact duplicate, do not trigger redundant re-layout or reset marquee
            }

            val item = DeskNotification(
                deviceId = deviceId,
                notificationId = notificationId,
                appName = appName,
                title = title,
                text = text,
                timestamp = timestamp,
                iconBytes = iconBytes,
                isClearable = isClearable
            )
            activeNotifications[k] = item
            notifyListeners()
        }

        override fun onNotificationDismissed(deviceId: String, notificationId: String) {
            if (activeNotifications.remove(key(deviceId, notificationId)) != null) {
                notifyListeners()
            }
        }
    }

    private val deviceListener = object : DeskConnectManager.DeviceListener {
        override fun onDeviceDiscovered(device: DeskConnectDevice) {}
        override fun onDeviceConnected(device: DeskConnectDevice) {}
        override fun onDeviceDisconnected(device: DeskConnectDevice) {
            clearDeviceNotifications(device.deviceId)
        }
        override fun onPairingRequested(device: DeskConnectDevice, verificationKey: String) {}
        override fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean) {
            if (!isPaired) {
                clearDeviceNotifications(device.deviceId)
            }
        }
    }

    init {
        deskConnectManager.notificationListeners.add(notificationListener)
        deskConnectManager.deviceListeners.add(deviceListener)
    }

    fun addListener(listener: OnNotificationsChangedListener) {
        listeners.add(listener)
        listener.onNotificationsChanged(getNotifications())
    }

    fun removeListener(listener: OnNotificationsChangedListener) {
        listeners.remove(listener)
    }

    fun getNotifications(): List<DeskNotification> {
        return activeNotifications.values.sortedByDescending { it.timestamp }
    }

    fun postNotification(item: DeskNotification) {
        activeNotifications[key(item.deviceId, item.notificationId)] = item
        notifyListeners()
    }

    fun dismissNotification(deviceId: String, notificationId: String) {
        activeNotifications.remove(key(deviceId, notificationId))
        deskConnectManager.dismissNotification(deviceId, notificationId)
        notifyListeners()
    }

    fun clearAllNotifications() {
        val toDismiss = activeNotifications.values.toList()
        activeNotifications.clear()
        toDismiss.forEach { item ->
            if (item.isClearable) {
                deskConnectManager.dismissNotification(item.deviceId, item.notificationId)
            }
        }
        notifyListeners()
    }

    private fun clearDeviceNotifications(deviceId: String) {
        var changed = false
        val it = activeNotifications.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.value.deviceId == deviceId) {
                it.remove()
                changed = true
            }
        }
        if (changed) {
            notifyListeners()
        }
    }

    private fun notifyListeners() {
        val list = getNotifications()
        mainHandler.post {
            listeners.forEach { it.onNotificationsChanged(list) }
        }
    }

    companion object {
        @Volatile
        private var instance: DeskNotificationRepository? = null

        fun getInstance(context: Context): DeskNotificationRepository {
            return instance ?: synchronized(this) {
                instance ?: DeskNotificationRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
