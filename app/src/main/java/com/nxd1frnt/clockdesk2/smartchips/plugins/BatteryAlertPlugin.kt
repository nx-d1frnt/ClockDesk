package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip
import com.nxd1frnt.clockdesk2.smartchips.setTextWithFade

class BatteryAlertPlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "show_battery_alert"

    private var stateChangeListener: (() -> Unit)? = null
    private var isListening = false

    private var wasSaverActive = false
    private var saverActivatedTimestamp = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val hideSaverTextRunnable = Runnable {
        stateChangeListener?.invoke()
    }

    private var lastReportedStatus = -1
    private var lastReportedPct = -1
    private var lastReportedPowerSave = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                val pm = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isPowerSave = pm?.isPowerSaveMode == true
                if (isPowerSave != lastReportedPowerSave) {
                    lastReportedPowerSave = isPowerSave
                    stateChangeListener?.invoke()
                }
            } else if (action == Intent.ACTION_BATTERY_CHANGED) {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (scale > 0) (level.toFloat() / scale.toFloat() * 100).toInt() else -1
                if (status != lastReportedStatus || pct != lastReportedPct) {
                    lastReportedStatus = status
                    lastReportedPct = pct
                    stateChangeListener?.invoke()
                }
            }
        }
    }

    override fun setOnStateChangeListener(listener: () -> Unit) {
        this.stateChangeListener = listener
    }

    override fun startListening() {
        if (isListening) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val stickyIntent = context.registerReceiver(batteryReceiver, filter)
        if (stickyIntent != null) {
            lastReportedStatus = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            lastReportedPct = if (scale > 0) (level.toFloat() / scale.toFloat() * 100).toInt() else -1
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        lastReportedPowerSave = pm?.isPowerSaveMode == true
        isListening = true
    }

    override fun stopListening() {
        if (!isListening) return
        handler.removeCallbacks(hideSaverTextRunnable)
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
        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        // Fetch battery status from sticky intent
        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (scale > 0) (level.toFloat() / scale.toFloat() * 100).toInt() else -1

        // Preferences
        val showSaver = sharedPreferences.getBoolean("battery_alert_show_saver", true)
        val showFull = sharedPreferences.getBoolean("battery_alert_show_full", true)
        val showCharging = sharedPreferences.getBoolean("battery_alert_show_charging", true)
        val showLow = sharedPreferences.getBoolean("battery_alert_show_low", true)

        val manualSaverOn = sharedPreferences.getBoolean("power_saver_manual", false)
        val autoSaverEnabled = sharedPreferences.getBoolean("automatic_battery_saver_mode", false)
        val saverThreshold = sharedPreferences.getInt("battery_saver_trigger", 15)
        val autoSaverOn = autoSaverEnabled && batteryPct in 1..saverThreshold && !isCharging && !isFull
        val syncSystemSaver = sharedPreferences.getBoolean("power_saver_sync_system", true)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val systemSaverOn = syncSystemSaver && powerManager?.isPowerSaveMode == true

        val isBatterySaverActive = manualSaverOn || autoSaverOn || systemSaverOn

        if (isBatterySaverActive && !wasSaverActive) {
            wasSaverActive = true
            saverActivatedTimestamp = System.currentTimeMillis()
            handler.removeCallbacks(hideSaverTextRunnable)
            handler.postDelayed(hideSaverTextRunnable, 5000L)
        } else if (!isBatterySaverActive && wasSaverActive) {
            wasSaverActive = false
            saverActivatedTimestamp = 0L
            handler.removeCallbacks(hideSaverTextRunnable)
        }

        val targetIconRes: Int
        val targetText: String

        if (showSaver && isBatterySaverActive) {
            targetIconRes = R.drawable.ic_battery_saver
            val elapsed = System.currentTimeMillis() - saverActivatedTimestamp
            targetText = if (elapsed in 0 until 5000L) {
                context.getString(R.string.battery_saver_on)
            } else {
                if (batteryPct > 0) "$batteryPct%" else context.getString(R.string.battery_saver_on)
            }
        } else if (showFull && (isFull || batteryPct >= 100)) {
            targetIconRes = R.drawable.ic_battery_full
            targetText = if (batteryPct > 0) "$batteryPct%" else "100%"
        } else if (showCharging && isCharging) {
            targetIconRes = R.drawable.ic_battery_charging
            targetText = if (batteryPct > 0) "$batteryPct%" else "Chg"
        } else if (showLow) {
            val threshold = sharedPreferences.getInt("battery_alert_low_threshold", 20)
            if (batteryPct in 1..threshold && !isCharging && !isFull) {
                targetIconRes = R.drawable.ic_battery_alert
                targetText = "$batteryPct%"
            } else {
                return false
            }
        } else {
            return false
        }

        if (iconView != null && iconView.tag != targetIconRes) {
            iconView.setImageResource(targetIconRes)
            iconView.tag = targetIconRes
        }
        if (textView != null) {
            textView.setTextWithFade(targetText)
        }
        return true
    }
}