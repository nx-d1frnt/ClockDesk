package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip

class BatteryAlertPlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "show_battery_alert"
    override val priority: Int = 100 // Highest priority

    override fun createView(context: Context): View {
        return LayoutInflater.from(context)
            .inflate(R.layout.smart_chip_layout, null, false)
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        // Check 1: Is the MANUAL app saver toggled on?
        val manualSaverOn = sharedPreferences.getBoolean("battery_saver_mode", false)
        if (manualSaverOn) {
            iconView.setImageResource(R.drawable.ic_battery_saver)
            textView.text = context.getString(R.string.battery_saver_on)
            return true // Show chip
        }

        // Check 2: Is the AUTOMATIC saver condition met?
        // Get battery status
        val batteryStatus: Intent? = context.registerReceiver(null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // Get battery level
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = (level.toFloat() / scale.toFloat() * 100).toInt()

        // Get user's custom threshold from preferences
        val threshold = sharedPreferences.getInt("battery_saver_trigger", 15)

        // Show if battery is below custom threshold AND not charging
        if (batteryPct <= threshold && !isCharging && batteryPct > 0) {
            iconView.setImageResource(R.drawable.ic_battery_saver)
            textView.text = "$batteryPct%" // Show the percentage
            return true // Show chip
        }

        return false // Hide chip
    }
}