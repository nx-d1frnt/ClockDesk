package com.nxd1frnt.clockdesk2.connect.repo

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskNotification
import com.nxd1frnt.clockdesk2.connect.model.NotificationOrigin
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Shared active notifications for banners, the shade, desks, and notification chips. */
class DeskNotificationRepository private constructor(context: Context) {
    fun interface OnNotificationsChangedListener {
        fun onNotificationsChanged(notifications: List<DeskNotification>)
    }

    interface EventListener {
        fun onPosted(item: DeskNotification, alert: Boolean) {}
        fun onRemoved(item: DeskNotification, acknowledged: Boolean) {}
    }

    private val deskConnectManager = DeskConnectManager.getInstance(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeNotifications = ConcurrentHashMap<String, DeskNotification>()
    private val presentedBanners = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val listeners = CopyOnWriteArrayList<OnNotificationsChangedListener>()
    private val eventListeners = CopyOnWriteArrayList<EventListener>()

    private fun remoteKey(deviceId: String, notificationId: String) = "REMOTE:$deviceId:$notificationId"

    private val notificationListener = object : DeskConnectManager.NotificationListener {
        override fun onNotificationReceived(
            deviceId: String, notificationId: String, appName: String, title: String,
            text: String, timestamp: Long, iconBytes: ByteArray?, isClearable: Boolean
        ) {
            val item = DeskNotification(deviceId, notificationId, appName, title, text, timestamp, iconBytes, isClearable)
            val old = activeNotifications[item.key]
            if (old != null && old.copy(timestamp = timestamp) == item) return
            postNotification(item)
        }

        override fun onNotificationDismissed(deviceId: String, notificationId: String) {
            onMain { activeNotifications[remoteKey(deviceId, notificationId)]?.let { remove(it, false) } }
        }
    }

    private val deviceListener = object : DeskConnectManager.DeviceListener {
        override fun onDeviceDiscovered(device: DeskConnectDevice) {}
        override fun onDeviceConnected(device: DeskConnectDevice) {}
        override fun onDeviceDisconnected(device: DeskConnectDevice) = clearDeviceNotifications(device.deviceId)
        override fun onPairingRequested(device: DeskConnectDevice, verificationKey: String) {}
        override fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean) {
            if (!isPaired) clearDeviceNotifications(device.deviceId)
        }
    }

    init {
        deskConnectManager.notificationListeners.add(notificationListener)
        deskConnectManager.deviceListeners.add(deviceListener)
    }

    fun addListener(listener: OnNotificationsChangedListener) {
        listeners.addIfAbsent(listener)
        listener.onNotificationsChanged(getNotifications())
    }
    fun removeListener(listener: OnNotificationsChangedListener) { listeners.remove(listener) }
    fun addEventListener(listener: EventListener) { eventListeners.addIfAbsent(listener) }
    fun removeEventListener(listener: EventListener) { eventListeners.remove(listener) }

    fun getNotifications(): List<DeskNotification> = activeNotifications.values
        .sortedWith(compareByDescending<DeskNotification> { it.priority }.thenByDescending { it.timestamp })

    fun wasBannerPresented(item: DeskNotification): Boolean = item.key in presentedBanners
    fun markBannerPresented(item: DeskNotification) { presentedBanners.add(item.key) }

    /** alert=false updates the active list/current banner without showing another popup. */
    fun postNotification(item: DeskNotification, alert: Boolean = true) = onMain {
        if (activeNotifications.put(item.key, item) != item) {
            if (alert) presentedBanners.remove(item.key)
            notifyListeners()
            eventListeners.forEach { it.onPosted(item, alert) }
        }
    }

    fun dismissNotification(item: DeskNotification) = onMain {
        val current = activeNotifications[item.key] ?: return@onMain
        if (current.isClearable) remove(current, true)
    }

    // Legacy remote API; transport removals may also remove ongoing notifications.
    fun dismissNotification(deviceId: String, notificationId: String) = onMain {
        activeNotifications[remoteKey(deviceId, notificationId)]?.let { remove(it, true) }
    }

    /** Resolving a condition does not run its user acknowledgement action. */
    fun resolveNotification(item: DeskNotification) = onMain { remove(item, false) }

    fun clearAllNotifications() = onMain {
        getNotifications().filter { it.isClearable }.forEach { remove(it, true) }
    }

    private fun remove(item: DeskNotification, acknowledged: Boolean) {
        val removed = activeNotifications.remove(item.key) ?: return
        presentedBanners.remove(item.key)
        if (acknowledged && removed.origin == NotificationOrigin.REMOTE) {
            deskConnectManager.dismissNotification(removed.deviceId, removed.notificationId)
        }
        notifyListeners()
        eventListeners.forEach { it.onRemoved(removed, acknowledged) }
    }

    private fun clearDeviceNotifications(deviceId: String) = onMain {
        getNotifications().filter {
            (it.origin == NotificationOrigin.REMOTE && it.deviceId == deviceId) || it.relatedDeviceId == deviceId
        }.forEach { remove(it, false) }
    }

    private fun notifyListeners() {
        val list = getNotifications()
        listeners.forEach { it.onNotificationsChanged(list) }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post { action() }
    }

    companion object {
        @Volatile private var instance: DeskNotificationRepository? = null
        fun getInstance(context: Context): DeskNotificationRepository = instance ?: synchronized(this) {
            instance ?: DeskNotificationRepository(context.applicationContext).also { instance = it }
        }
    }
}
