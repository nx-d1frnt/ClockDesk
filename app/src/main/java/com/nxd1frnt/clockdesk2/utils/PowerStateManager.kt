package com.nxd1frnt.clockdesk2.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.util.Log
import java.lang.ref.WeakReference

class PowerStateManager(private val context: Context) {

    private var isPowerSavingMode = false
    private var isAutoPowerSavingActive = false

    private val observers = mutableListOf<WeakReference<PowerSaveObserver>>()
    private val prefs: SharedPreferences = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                checkBatteryState(intent)
            }
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
    }

    fun registerObserver(observer: PowerSaveObserver) {
        observers.add(WeakReference(observer))
        observer.onPowerSaveModeChanged(isPowerSavingMode)
    }

    fun unregisterObserver(observer: PowerSaveObserver) {
        val iterator = observers.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            if (ref.get() == observer || ref.get() == null) {
                iterator.remove()
            }
        }
    }

    private fun checkBatteryState(intent: Intent) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = (level.toFloat() / scale.toFloat() * 100).toInt()

        val threshold = prefs.getInt("battery_saver_trigger", 15)
        val isManualOverride = prefs.getBoolean("power_saver_manual", false)

        val shouldBeEnabled = isManualOverride || (batteryPct <= threshold && !isCharging)

        if (isPowerSavingMode != shouldBeEnabled) {
            setPowerSaveMode(shouldBeEnabled)
        }
    }

    private fun setPowerSaveMode(enabled: Boolean) {
        if (isPowerSavingMode == enabled) return

        isPowerSavingMode = enabled
        Logger.d("PowerManager"){"Power Save Mode changed to: $enabled"}


        val iterator = observers.iterator()
        while (iterator.hasNext()) {
            val observer = iterator.next().get()
            if (observer != null) {
                observer.onPowerSaveModeChanged(enabled)
            } else {
                iterator.remove()
            }
        }
    }

    fun isPowerSaveEnabled() = isPowerSavingMode

    fun destroy() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}