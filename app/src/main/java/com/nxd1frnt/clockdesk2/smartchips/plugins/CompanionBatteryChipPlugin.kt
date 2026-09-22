package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.SharedPreferences
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.smartchips.IMultiSmartChip
import com.nxd1frnt.clockdesk2.smartchips.MultiChipItem
import java.util.concurrent.ConcurrentHashMap

class CompanionBatteryChipPlugin(private val context: Context) : IMultiSmartChip {

    override val preferenceKey: String = "show_companion_battery"

    private var stateChangeListener: (() -> Unit)? = null
    private var isListening = false
    private val deskConnectManager = DeskConnectManager.getInstance(context)

    private data class DeviceBattery(
        val deviceId: String,
        val charge: Int,
        val isCharging: Boolean,
        val thresholdEvent: Int,
        val updatedTime: Long
    )

    private val deviceBatteries = ConcurrentHashMap<String, DeviceBattery>()

    private val batteryListener = object : DeskConnectManager.BatteryListener {
        override fun onBatteryUpdated(deviceId: String, currentCharge: Int, isCharging: Boolean, thresholdEvent: Int) {
            val old = deviceBatteries[deviceId]
            if (old != null && old.charge == currentCharge && old.isCharging == isCharging && old.thresholdEvent == thresholdEvent) {
                return // Exact duplicate, do not trigger redundant chip updates or marquee stutter
            }
            deviceBatteries[deviceId] = DeviceBattery(
                deviceId = deviceId,
                charge = currentCharge,
                isCharging = isCharging,
                thresholdEvent = thresholdEvent,
                updatedTime = System.currentTimeMillis()
            )
            stateChangeListener?.invoke()
        }
    }

    private val deviceListener = object : DeskConnectManager.DeviceListener {
        override fun onDeviceDiscovered(device: DeskConnectDevice) {}
        override fun onDeviceConnected(device: DeskConnectDevice) {}
        override fun onDeviceDisconnected(device: DeskConnectDevice) {
            if (deviceBatteries.remove(device.deviceId) != null) {
                stateChangeListener?.invoke()
            }
        }
        override fun onPairingRequested(device: DeskConnectDevice, verificationKey: String) {}
        override fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean) {
            if (!isPaired && deviceBatteries.remove(device.deviceId) != null) {
                stateChangeListener?.invoke()
            }
        }
    }

    override fun setOnStateChangeListener(listener: () -> Unit) {
        this.stateChangeListener = listener
    }

    override fun startListening() {
        if (isListening) return
        deskConnectManager.batteryListeners.add(batteryListener)
        deskConnectManager.deviceListeners.add(deviceListener)
        isListening = true
    }

    override fun stopListening() {
        if (!isListening) return
        deskConnectManager.batteryListeners.remove(batteryListener)
        deskConnectManager.deviceListeners.remove(deviceListener)
        isListening = false
    }

    override fun getChips(sharedPreferences: SharedPreferences): List<MultiChipItem> {
        val isEnabled = sharedPreferences.getBoolean(preferenceKey, true)
        if (!isEnabled) return emptyList()

        // Filter all paired devices with low battery (<= 20% and not charging, or threshold event active)
        val lowBatteries = deviceBatteries.values
            .filter { entry ->
                ((entry.charge in 0..30) || entry.thresholdEvent == 1 || entry.thresholdEvent == 2) && !entry.isCharging
            }
            .sortedBy { it.charge }

        return lowBatteries.mapNotNull { entry ->
            val dev = deskConnectManager.discoveredDevices[entry.deviceId]
            val isPaired = dev?.isPaired == true || deskConnectManager.security.isDevicePaired(entry.deviceId)
            if (!isPaired) return@mapNotNull null
            if (dev != null && !dev.isConnected) return@mapNotNull null

            val deviceName = dev?.getDisplayName() ?: "Remote"
            val iconRes = dev?.getDeviceTypeIconRes() ?: R.drawable.ic_devices

            MultiChipItem(
                chipId = "companion_battery_${entry.deviceId}",
                text = "$deviceName ${entry.charge}%",
                iconRes = iconRes,
                isVisible = true
            )
        }
    }
}
