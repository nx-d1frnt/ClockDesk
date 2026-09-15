package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip
import java.util.concurrent.ConcurrentHashMap

class CompanionBatteryChipPlugin(private val context: Context) : ISmartChip {

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

    override fun setOnStateChangeListener(listener: () -> Unit) {
        this.stateChangeListener = listener
    }

    override fun startListening() {
        if (isListening) return
        deskConnectManager.batteryListeners.add(batteryListener)
        isListening = true
    }

    override fun stopListening() {
        if (!isListening) return
        deskConnectManager.batteryListeners.remove(batteryListener)
        isListening = false
    }

    override fun createView(context: Context): View {
        return LayoutInflater.from(context).inflate(R.layout.smart_chip_layout, null, false)
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        val isEnabled = sharedPreferences.getBoolean(preferenceKey, true)
        if (!isEnabled) return false

        // Only show if a paired device has low battery (<= 20% and not charging, or thresholdEvent == 1)
        val lowBatteryEntry = deviceBatteries.values
            .filter { it.charge in 0..20 && !it.isCharging }
            .minByOrNull { it.charge }

        if (lowBatteryEntry == null) {
            return false
        }

        val dev = deskConnectManager.discoveredDevices[lowBatteryEntry.deviceId]
        val deviceName = dev?.getDisplayName() ?: "Remote"

        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        iconView.setImageResource(R.drawable.ic_battery_alert)
        textView.text = "$deviceName ${lowBatteryEntry.charge}%"
        return true
    }
}
