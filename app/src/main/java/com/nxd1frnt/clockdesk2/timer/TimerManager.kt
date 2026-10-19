package com.nxd1frnt.clockdesk2.timer

import com.nxd1frnt.clockdesk2.utils.PowerStateManager
import com.nxd1frnt.clockdesk2.utils.PowerSaveObserver
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.CopyOnWriteArrayList

enum class TimerState {
    IDLE,
    RUNNING,
    PAUSED,
    FINISHED
}

object TimerManager : PowerSaveObserver {

    private var isPowerSaving = false

    override fun onPowerSaveModeChanged(isEnabled: Boolean) {
        if (isPowerSaving == isEnabled) return
        isPowerSaving = isEnabled
        if (state == TimerState.RUNNING) {
            handler.removeCallbacks(tickRunnable)
            handler.post(tickRunnable)
        }
    }

    interface TimerListener {
        fun onTimerTick(remainingMillis: Long, totalDurationMillis: Long)
        fun onTimerStateChanged(state: TimerState)
        fun onTimerSilentChanged(isSilent: Boolean) {}
    }

    private val listeners = CopyOnWriteArrayList<TimerListener>()
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    var state: TimerState = TimerState.IDLE
        private set

    var totalDurationMillis: Long = 5 * 60 * 1000L
        private set

    var remainingMillis: Long = totalDurationMillis
        private set

    private var targetEndTime: Long = 0L

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isAlarmActive = false

    var isSilent: Boolean = false
        private set

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (state != TimerState.RUNNING) return
            val now = SystemClock.elapsedRealtime()
            val remaining = (targetEndTime - now).coerceAtLeast(0L)
            remainingMillis = remaining
            notifyTick(remainingMillis, totalDurationMillis)

            if (remaining <= 0L) {
                onFinished()
            } else {
                handler.postDelayed(this, minOf(if (isPowerSaving) 1000L else 100L, remaining))
            }
        }
    }

    private val autoStopAlarmRunnable = Runnable {
        stopAlarm()
    }

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            isSilent = appContext?.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
                ?.getBoolean("timer_is_silent", false) ?: false
            PowerStateManager.getInstance(context).registerObserver(this)
        }
    }

    fun setSilent(silent: Boolean) {
        if (isSilent == silent) return
        isSilent = silent
        appContext?.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
            ?.edit()?.putBoolean("timer_is_silent", silent)?.apply()
        notifySilentChanged()
    }

    fun addListener(listener: TimerListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
        listener.onTimerTick(remainingMillis, totalDurationMillis)
        listener.onTimerStateChanged(state)
        listener.onTimerSilentChanged(isSilent)
    }

    fun removeListener(listener: TimerListener) {
        listeners.remove(listener)
    }

    private fun notifyTick(remaining: Long, total: Long) {
        listeners.forEach { it.onTimerTick(remaining, total) }
    }

    private fun notifyStateChanged() {
        listeners.forEach { it.onTimerStateChanged(state) }
    }

    private fun notifySilentChanged() {
        listeners.forEach { it.onTimerSilentChanged(isSilent) }
    }

    fun setDuration(durationMillis: Long) {
        if (state == TimerState.RUNNING) return
        stopAlarm()
        totalDurationMillis = durationMillis.coerceAtLeast(1000L)
        remainingMillis = totalDurationMillis
        state = TimerState.IDLE
        notifyTick(remainingMillis, totalDurationMillis)
        notifyStateChanged()
    }

    fun addTime(additionalMillis: Long) {
        stopAlarm()
        if (state == TimerState.RUNNING) {
            targetEndTime += additionalMillis
            remainingMillis += additionalMillis
            totalDurationMillis += additionalMillis
            notifyTick(remainingMillis, totalDurationMillis)
        } else {
            totalDurationMillis += additionalMillis
            remainingMillis += additionalMillis
            notifyTick(remainingMillis, totalDurationMillis)
        }
    }

    fun start(durationMillis: Long? = null) {
        stopAlarm()
        if (durationMillis != null && durationMillis > 0) {
            totalDurationMillis = durationMillis
            remainingMillis = durationMillis
        } else if (remainingMillis <= 0L) {
            remainingMillis = totalDurationMillis
        }
        targetEndTime = SystemClock.elapsedRealtime() + remainingMillis
        state = TimerState.RUNNING
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
        notifyStateChanged()
    }

    fun pause() {
        if (state != TimerState.RUNNING) return
        handler.removeCallbacks(tickRunnable)
        val now = SystemClock.elapsedRealtime()
        remainingMillis = (targetEndTime - now).coerceAtLeast(0L)
        state = TimerState.PAUSED
        notifyTick(remainingMillis, totalDurationMillis)
        notifyStateChanged()
    }

    fun resume() {
        if (state != TimerState.PAUSED) return
        if (remainingMillis <= 0L) {
            remainingMillis = totalDurationMillis
        }
        targetEndTime = SystemClock.elapsedRealtime() + remainingMillis
        state = TimerState.RUNNING
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
        notifyStateChanged()
    }

    fun reset() {
        handler.removeCallbacks(tickRunnable)
        stopAlarm()
        remainingMillis = totalDurationMillis
        state = TimerState.IDLE
        notifyTick(remainingMillis, totalDurationMillis)
        notifyStateChanged()
    }

    private fun onFinished() {
        handler.removeCallbacks(tickRunnable)
        remainingMillis = 0L
        state = TimerState.FINISHED
        notifyTick(0L, totalDurationMillis)
        notifyStateChanged()
        appContext?.let {
            triggerAlarm(it)
        }
    }

    fun triggerAlarm(context: Context) {
        if (isAlarmActive) return
        isAlarmActive = true

        if (!isSilent) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    play()
                }
            } catch (e: Exception) {
                // ignore audio failure
            }
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (isSilent) {
                // In silent mode: a subtle notification pulse (double buzz, no loop)
                val silentPattern = longArrayOf(0, 250, 150, 250)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(silentPattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(silentPattern, -1)
                }
            } else {
                val pattern = longArrayOf(0, 500, 300, 500, 300, 800)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            // ignore vibration failure
        }

        handler.removeCallbacks(autoStopAlarmRunnable)
        handler.postDelayed(autoStopAlarmRunnable, 60_000L)
    }

    fun stopAlarm() {
        isAlarmActive = false
        handler.removeCallbacks(autoStopAlarmRunnable)
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {}
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {}
    }

    fun formatTime(millis: Long): String {
        val totalSec = (millis + 999) / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
