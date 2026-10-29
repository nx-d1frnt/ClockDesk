package com.nxd1frnt.clockdesk2.notifications

import android.content.Context
import android.content.SharedPreferences
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.AppAlertType
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskNotification
import com.nxd1frnt.clockdesk2.connect.model.NotificationOrigin
import com.nxd1frnt.clockdesk2.connect.repo.DeskNotificationRepository
import com.nxd1frnt.clockdesk2.timer.TimerManager
import com.nxd1frnt.clockdesk2.timer.TimerState

/** App-lifetime producers; monitoring does not depend on a visible desk or smart chip. */
class AppNotificationManager internal constructor(private val context: Context) {
    private val repository = DeskNotificationRepository.getInstance(context)
    private val connect = DeskConnectManager.getInstance(context)
    private val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
    private val batteryPolicy = BatteryAlertPolicy()

    private val timerListener = object : TimerManager.TimerListener {
        override fun onTimerTick(remainingMillis: Long, totalDurationMillis: Long) {}
        override fun onTimerStateChanged(state: TimerState) {
            if (state == TimerState.FINISHED) {
                repository.postNotification(DeskNotification(
                    deviceId = "", notificationId = TIMER_ID,
                    appName = context.getString(R.string.desk_type_timer),
                    title = context.getString(R.string.timer_time_up),
                    text = context.getString(R.string.timer_dismiss_to_stop),
                    timestamp = System.currentTimeMillis(), iconBytes = null, isClearable = true,
                    origin = NotificationOrigin.APP, alertType = AppAlertType.TIMER_FINISHED,
                    priority = 100, iconRes = R.drawable.ic_timer
                ))
            } else {
                resolve(AppAlertType.TIMER_FINISHED)
            }
        }
    }

    private val events = object : DeskNotificationRepository.EventListener {
        override fun onRemoved(item: DeskNotification, acknowledged: Boolean) {
            if (acknowledged && item.origin == NotificationOrigin.APP &&
                item.alertType == AppAlertType.TIMER_FINISHED && TimerManager.state == TimerState.FINISHED) {
                TimerManager.reset()
            }
        }
    }

    private val batteryListener = object : DeskConnectManager.BatteryListener {
        override fun onBatteryUpdated(deviceId: String, currentCharge: Int, isCharging: Boolean, thresholdEvent: Int) {
            updateBattery(deviceId, currentCharge, isCharging)
        }
    }

    private val deviceListener = object : DeskConnectManager.DeviceListener {
        override fun onDeviceDiscovered(device: DeskConnectDevice) {}
        override fun onDeviceConnected(device: DeskConnectDevice) {
            // Wait for a fresh battery packet; cached values may be stale after reconnecting.
        }
        override fun onDeviceDisconnected(device: DeskConnectDevice) {
            resolve(AppAlertType.DEVICE_BATTERY_LOW, device.deviceId)
        }
        override fun onPairingRequested(device: DeskConnectDevice, verificationKey: String) {}
        override fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean) {
            if (!isPaired) {
                batteryPolicy.forget(device.deviceId)
                resolve(AppAlertType.DEVICE_BATTERY_LOW, device.deviceId)
            }
        }
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == BATTERY_ENABLED || key == BATTERY_THRESHOLD) {
            batteryPolicy.reset()
            resolve(AppAlertType.DEVICE_BATTERY_LOW)
            if (prefs.getBoolean(BATTERY_ENABLED, true)) {
                connect.discoveredDevices.values.filter { it.isConnected && it.isPaired }.forEach {
                    updateBattery(it.deviceId, it.batteryLevel, it.isCharging)
                }
            }
        }
    }

    init {
        repository.addEventListener(events)
        TimerManager.addListener(timerListener)
        connect.batteryListeners.add(batteryListener)
        connect.deviceListeners.add(deviceListener)
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    internal fun close() {
        repository.removeEventListener(events)
        TimerManager.removeListener(timerListener)
        connect.batteryListeners.remove(batteryListener)
        connect.deviceListeners.remove(deviceListener)
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun updateBattery(deviceId: String, charge: Int, charging: Boolean) {
        if (!prefs.getBoolean(BATTERY_ENABLED, true) || charge !in 0..100) return
        val device = connect.discoveredDevices[deviceId] ?: return
        if (!device.isPaired || !device.isConnected) return
        val threshold = prefs.getInt(BATTERY_THRESHOLD, 20).coerceIn(5, 50)
        val decision = batteryPolicy.update(deviceId, charge, charging, threshold)
        val existing = repository.getNotifications().firstOrNull {
            it.origin == NotificationOrigin.APP && it.alertType == AppAlertType.DEVICE_BATTERY_LOW &&
                it.relatedDeviceId == deviceId
        }
        if (!decision.low) {
            existing?.let(repository::resolveNotification)
            return
        }
        if (!decision.newEpisode && existing == null) return
        repository.postNotification(DeskNotification(
            deviceId = "", notificationId = "battery-low:$deviceId",
            appName = context.getString(R.string.app_name),
            title = context.getString(R.string.app_alert_device_battery_low, device.getDisplayName()),
            text = context.getString(R.string.app_alert_battery_remaining, charge),
            timestamp = existing?.timestamp ?: System.currentTimeMillis(), iconBytes = null, isClearable = true,
            origin = NotificationOrigin.APP, alertType = AppAlertType.DEVICE_BATTERY_LOW,
            relatedDeviceId = deviceId, priority = 10, iconRes = R.drawable.ic_battery_alert
        ), alert = decision.newEpisode)
    }

    private fun resolve(type: AppAlertType, deviceId: String? = null) {
        repository.getNotifications().filter {
            it.origin == NotificationOrigin.APP && it.alertType == type &&
                (deviceId == null || it.relatedDeviceId == deviceId)
        }.forEach(repository::resolveNotification)
    }

    companion object {
        const val TIMER_ID = "timer:main:finished"
        const val TIMER_BANNER = "app_alert_timer_banner"
        const val BATTERY_ENABLED = "app_alert_battery_enabled"
        const val BATTERY_BANNER = "app_alert_battery_banner"
        const val BATTERY_THRESHOLD = "app_alert_battery_threshold"
        @Volatile private var instance: AppNotificationManager? = null
        fun getInstance(context: Context): AppNotificationManager = instance ?: synchronized(this) {
            instance ?: AppNotificationManager(context.applicationContext).also { instance = it }
        }
    }
}
