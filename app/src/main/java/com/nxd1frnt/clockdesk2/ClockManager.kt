package com.nxd1frnt.clockdesk2

import android.os.Handler
import android.util.Log
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class ClockManager (private val timeText: TextView, private val dateText: TextView, private val handler: Handler, private val debugCallback: (String, String, String) -> Unit ) {
    private var isDebugMode = false
    private val debugCycleInterval = 5L // 500ms per 30 minutes
    private var simulatedTime: Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
    private val clockUpdateRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, if (isDebugMode) debugCycleInterval else 1000)
            Log.d("ClockUpdate", "Clock updated at ${System.currentTimeMillis()}")
        }
    }

    fun startUpdates() {
        handler.removeCallbacks(clockUpdateRunnable)
        handler.post(clockUpdateRunnable)
    }

    fun stopUpdates() {
        handler.removeCallbacks(clockUpdateRunnable)
    }

    fun toggleDebugMode(enabled: Boolean) {
        isDebugMode = enabled
        if (isDebugMode) {
            simulatedTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
        startUpdates()
    }

    private fun updateClock() {
        val currentTime = if (isDebugMode) {
            simulatedTime.add(Calendar.MINUTE, 1)
            if (simulatedTime.get(Calendar.HOUR_OF_DAY) >= 24) {
                simulatedTime.set(Calendar.HOUR_OF_DAY, 0)
                simulatedTime.set(Calendar.MINUTE, 0)
            }
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(simulatedTime.time)
            debugCallback(timeStr, "06:00", "20:05") // Placeholder, updated via SunTimeApi
            Log.d("DebugMode", "Simulated time: $timeStr")
            simulatedTime.time
        } else {
            Calendar.getInstance().time
        }
        timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime)
        dateText.text = SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(currentTime)
    }

}