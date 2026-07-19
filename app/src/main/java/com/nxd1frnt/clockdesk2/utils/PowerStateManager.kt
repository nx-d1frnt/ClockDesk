package com.nxd1frnt.clockdesk2.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.PowerManager
import java.lang.ref.WeakReference

class PowerStateManager(private val context: Context) {

    private var isPowerSavingMode = false

    private val observers = mutableListOf<WeakReference<PowerSaveObserver>>()
    private val prefs: SharedPreferences = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_BATTERY_CHANGED || action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                checkPowerSaveStatus()
            }
        }
    }

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "power_saver_manual" || key == "automatic_battery_saver_mode" || key == "battery_saver_trigger" || key == "power_saver_sync_system") {
            checkPowerSaveStatus()
        } else if (key == "power_saver_disable_animations" || key == "power_saver_disable_weather" ||
            key == "power_saver_lock_brightness" || key == "power_saver_limit_clock" ||
            key == "power_saver_disable_light_sensor" || key == "power_saver_enable_smart_pixels" ||
            key == "power_saver_dim_background" || key == "power_saver_brightness_level" ||
            key == "power_saver_dim_level" || key == "power_saver_limit_fps" ||
            key == "power_saver_fps_limit_value"
        ) {
            if (isPowerSavingMode) {
                notifyObservers(true)
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val intent = context.registerReceiver(batteryReceiver, filter)
        checkBatteryState(intent)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
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

    fun checkPowerSaveStatus() {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        checkBatteryState(intent)
    }

    private fun checkBatteryState(intent: Intent?) {
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (scale > 0) (level.toFloat() / scale.toFloat() * 100).toInt() else 100

        val threshold = prefs.getInt("battery_saver_trigger", 15)
        val isManualOverride = prefs.getBoolean("power_saver_manual", false)
        val isAutoEnabled = prefs.getBoolean("automatic_battery_saver_mode", false)
        val isSyncSystemEnabled = prefs.getBoolean("power_saver_sync_system", true)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isSystemPowerSave = isSyncSystemEnabled && powerManager?.isPowerSaveMode == true

        val shouldBeEnabled = isManualOverride || (isAutoEnabled && batteryPct <= threshold && !isCharging) || isSystemPowerSave

        if (isPowerSavingMode != shouldBeEnabled) {
            setPowerSaveMode(shouldBeEnabled)
        }
    }

    private fun setPowerSaveMode(enabled: Boolean) {
        if (isPowerSavingMode == enabled) return

        isPowerSavingMode = enabled
        Logger.d("PowerManager"){"Power Save Mode changed to: $enabled"}

        notifyObservers(enabled)
    }

    private fun notifyObservers(enabled: Boolean) {
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
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}