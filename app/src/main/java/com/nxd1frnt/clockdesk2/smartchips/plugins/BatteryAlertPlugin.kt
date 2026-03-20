package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.BroadcastReceiver
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

    private var stateChangeListener: (() -> Unit)? = null
    private var isListening = false

    // Реактивный слушатель: срабатывает только при реальном изменении заряда
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                // Уведомляем SmartChipManager, что пора обновить UI
                stateChangeListener?.invoke()
            }
        }
    }

    override fun setOnStateChangeListener(listener: () -> Unit) {
        this.stateChangeListener = listener
    }

    override fun startListening() {
        if (isListening) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
        isListening = true
    }

    override fun stopListening() {
        if (!isListening) return
        try {
            context.unregisterReceiver(batteryReceiver)
            isListening = false
        } catch (e: Exception) { /* Игнорируем, если уже отписан */ }
    }

    override fun createView(context: Context): View {
        return LayoutInflater.from(context)
            .inflate(R.layout.smart_chip_layout, null, false)
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        // Логика отрисовки остается вашей (она написана отлично).
        // Чтение "липкого" (sticky) бродкаста с null работает мгновенно:

        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        val manualSaverOn = sharedPreferences.getBoolean("battery_saver_mode", false)
        if (manualSaverOn) {
            iconView.setImageResource(R.drawable.ic_battery_saver)
            textView.text = context.getString(R.string.battery_saver_on)
            return true
        }

        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (scale > 0) (level.toFloat() / scale.toFloat() * 100).toInt() else -1

        val threshold = sharedPreferences.getInt("battery_saver_trigger", 15)

        if (batteryPct in 1..threshold && !isCharging) {
            iconView.setImageResource(R.drawable.ic_battery_saver)
            textView.text = "$batteryPct%"
            return true
        }

        return false
    }
}