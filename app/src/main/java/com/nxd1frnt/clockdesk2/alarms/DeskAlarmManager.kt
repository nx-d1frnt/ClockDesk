package com.nxd1frnt.clockdesk2.alarms

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList

object DeskAlarmManager {
    internal var timeSource: () -> Long = System::currentTimeMillis
    private const val PREFS_NAME = "ClockDeskPrefs"
    private const val KEY_ALARMS_JSON = "desk_alarms_json"
    private const val TICK_INTERVAL_MS = 1000L
    private const val AUTO_SNOOZE_TIMEOUT_MS = 10 * 60_000L
    internal const val MAX_LATENESS_MS = 10 * 60_000L

    interface AlarmListener {
        fun onAlarmsChanged(alarms: List<DeskAlarm>) {}
        fun onSunriseChanged(progress: Float, alarm: DeskAlarm?) {}
        fun onAlarmRinging(alarm: DeskAlarm?) {}
    }

    private val listeners = CopyOnWriteArrayList<AlarmListener>()
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private val alarmsList = mutableListOf<DeskAlarm>()
    private var soundPlayer: AlarmSoundPlayer? = null
    private var vibrator: Vibrator? = null
    var ringingAlarm: DeskAlarm? = null
        private set
    var sunriseAlarm: DeskAlarm? = null
        private set
    var sunriseProgress: Float = 0f
        private set
    val isSunriseActive: Boolean
        get() = sunriseProgress > 0f

    private val autoSnoozeRunnable = Runnable { snoozeActive() }
    private val tickRunnable = object : Runnable {
        override fun run() {
            // Exact delivery owns ringing when available. This avoids a race with the receiver
            // and a background service launch outside the exact-alarm exemption.
            val allowRinging = appContext?.let { !SystemAlarmScheduler.canSchedule(it) } ?: true
            evaluateAlarms(timeSource(), allowRinging)
            scheduleTickIfNeeded()
        }
    }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        alarmsList.clear()
        alarmsList.addAll(AlarmScheduler.fromJson(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ALARMS_JSON, null)))
        refreshSchedule()
    }

    fun getAlarms(): List<DeskAlarm> = alarmsList.sortedWith(compareBy({ it.hour }, { it.minute }))

    fun addListener(listener: AlarmListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
        listener.onAlarmsChanged(getAlarms())
        listener.onSunriseChanged(sunriseProgress, sunriseAlarm)
        listener.onAlarmRinging(ringingAlarm)
        scheduleTickIfNeeded()
    }

    fun removeListener(listener: AlarmListener) {
        listeners.remove(listener)
        scheduleTickIfNeeded()
    }

    fun addOrUpdateAlarm(alarm: DeskAlarm) {
        if (ringingAlarm?.id == alarm.id) stopRinging()
        val index = alarmsList.indexOfFirst { it.id == alarm.id }
        val previous = alarmsList.getOrNull(index)
        val sameSchedule = previous != null && previous.hour == alarm.hour && previous.minute == alarm.minute &&
            previous.daysOfWeek == alarm.daysOfWeek && previous.enabled == alarm.enabled
        val cleaned = if (sameSchedule && previous != null) alarm.copy(
            snoozeUntilMillis = previous.snoozeUntilMillis, scheduledTriggerMillis = previous.scheduledTriggerMillis,
            skipTriggerBeforeMillis = previous.skipTriggerBeforeMillis,
            sunriseDismissedUntilMillis = previous.sunriseDismissedUntilMillis
        ) else alarm.copy(snoozeUntilMillis = null, skipTriggerBeforeMillis = 0L,
            scheduledTriggerMillis = null, sunriseDismissedUntilMillis = 0L)
        if (index < 0) alarmsList.add(cleaned) else alarmsList[index] = cleaned
        changed()
    }

    fun setAlarmEnabled(id: String, enabled: Boolean) {
        val index = alarmsList.indexOfFirst { it.id == id }
        if (index < 0) return
        if (ringingAlarm?.id == id) stopRinging()
        alarmsList[index] = alarmsList[index].copy(enabled = enabled, snoozeUntilMillis = null,
            skipTriggerBeforeMillis = 0L, scheduledTriggerMillis = null, sunriseDismissedUntilMillis = 0L)
        changed()
    }

    fun deleteAlarm(id: String) {
        if (ringingAlarm?.id == id) stopRinging()
        if (alarmsList.removeAll { it.id == id }) {
            appContext?.let { SystemAlarmScheduler.cancel(it, id) }
            changed()
        }
    }

    fun dismissActive() {
        if (ringingAlarm == null) return
        // The occurrence was consumed before playback, so dismissal cannot move another occurrence.
        stopRinging()
        changed()
    }

    fun snoozeActive(minutes: Int? = null) {
        val active = ringingAlarm ?: return
        stopRinging()
        val index = alarmsList.indexOfFirst { it.id == active.id }
        if (index >= 0) {
            val until = timeSource() + (minutes ?: active.snoozeMinutes).coerceIn(1, 30) * 60_000L
            alarmsList[index] = alarmsList[index].copy(enabled = true, snoozeUntilMillis = until,
                scheduledTriggerMillis = until)
        }
        changed()
    }

    /** Skips only this occurrence; recurring alarms retain their selected days. */
    fun skipNext(id: String, nowMillis: Long = timeSource()) {
        if (ringingAlarm?.id == id) return
        val index = alarmsList.indexOfFirst { it.id == id }
        if (index < 0) return
        val alarm = alarmsList[index]
        val trigger = AlarmScheduler.nextTriggerTimeMillis(alarm, nowMillis) ?: return
        alarmsList[index] = consume(alarm, trigger)
        changed(nowMillis)
    }

    fun restoreSkippedOccurrence(before: DeskAlarm, expected: DeskAlarm) {
        val index = alarmsList.indexOfFirst { it.id == before.id }
        val trigger = before.scheduledTriggerMillis ?: return
        if (index < 0 || alarmsList[index] != expected || trigger <= timeSource()) return
        alarmsList[index] = before
        changed()
    }

    fun dismissSunrise() {
        val active = sunriseAlarm ?: return
        val trigger = ringingAlarm?.takeIf { it.id == active.id }?.scheduledTriggerMillis
            ?: AlarmScheduler.nextTriggerTimeMillis(active, timeSource()) ?: return
        val until = trigger + 60_000L
        val index = alarmsList.indexOfFirst { it.id == active.id }
        if (index >= 0) alarmsList[index] = alarmsList[index].copy(sunriseDismissedUntilMillis = until)
        if (ringingAlarm?.id == active.id) ringingAlarm = ringingAlarm?.copy(sunriseDismissedUntilMillis = until)
        changed()
    }

    fun getNextAlarm(nowMillis: Long = timeSource()): Pair<DeskAlarm, Long>? = alarmsList
        .filter { it.id != ringingAlarm?.id }
        .mapNotNull { alarm -> AlarmScheduler.nextTriggerTimeMillis(alarm, nowMillis)?.let { alarm to it } }
        .minByOrNull { it.second }

    fun refreshSchedule(recalculateWallClock: Boolean = false) {
        if (recalculateWallClock) {
            for (index in alarmsList.indices) {
                val alarm = alarmsList[index]
                if (alarm.snoozeUntilMillis == null) alarmsList[index] = alarm.copy(scheduledTriggerMillis = null)
            }
        }
        saveAndNotify(timeSource())
        evaluateAlarms(timeSource(), allowRinging = false)
        scheduleTickIfNeeded()
    }

    internal fun deliver(id: String, trigger: Long, nowMillis: Long = timeSource()) {
        val alarm = alarmsList.find { it.id == id && it.enabled && it.scheduledTriggerMillis == trigger } ?: return
        if (nowMillis < trigger) return
        if (nowMillis - trigger > MAX_LATENESS_MS) {
            val index = alarmsList.indexOf(alarm)
            alarmsList[index] = consume(alarm, trigger)
            changed(nowMillis)
            return
        }
        if (ringingAlarm == null) startRinging(alarm, trigger, nowMillis)
        // Simultaneous alarms remain due and are handled when the active alarm is dismissed.
    }

    private fun consume(alarm: DeskAlarm, trigger: Long) = alarm.copy(
        enabled = alarm.daysOfWeek.isNotEmpty(), snoozeUntilMillis = null,
        scheduledTriggerMillis = null, skipTriggerBeforeMillis = trigger + 60_000L, sunriseDismissedUntilMillis = 0L)

    private fun scheduleTickIfNeeded() {
        handler.removeCallbacks(tickRunnable)
        if (ringingAlarm != null || (listeners.isNotEmpty() && alarmsList.any { it.enabled })) {
            handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
        }
    }

    internal fun evaluateAlarms(nowMillis: Long, allowRinging: Boolean = true) {
        if (allowRinging && ringingAlarm == null) {
            for (index in alarmsList.indices) {
                val alarm = alarmsList[index]
                val trigger = AlarmScheduler.nextTriggerTimeMillis(alarm, nowMillis) ?: continue
                if (nowMillis < trigger) continue
                if (nowMillis - trigger > MAX_LATENESS_MS) {
                    alarmsList[index] = consume(alarm, trigger)
                    saveAndNotify(nowMillis)
                } else {
                    startRinging(alarm, trigger, nowMillis)
                    break
                }
            }
        }
        val ringing = ringingAlarm
        var bestProgress = if (ringing?.sunriseEnabled == true &&
            (ringing.scheduledTriggerMillis ?: nowMillis) > ringing.sunriseDismissedUntilMillis) 1f else 0f
        var bestAlarm = if (bestProgress > 0f) ringing else null
        if (ringing == null) {
            for (alarm in alarmsList) {
                val progress = AlarmScheduler.sunriseProgressFor(alarm, nowMillis)
                if (progress > bestProgress) {
                    bestProgress = progress
                    bestAlarm = alarm
                }
            }
        }
        if (bestProgress != sunriseProgress || bestAlarm != sunriseAlarm) {
            sunriseProgress = bestProgress
            sunriseAlarm = bestAlarm
            listeners.forEach { it.onSunriseChanged(sunriseProgress, sunriseAlarm) }
        }
    }

    private fun startRinging(alarm: DeskAlarm, trigger: Long, nowMillis: Long) {
        ringingAlarm = alarm
        val index = alarmsList.indexOfFirst { it.id == alarm.id }
        if (index >= 0) alarmsList[index] = consume(alarm, trigger)
        saveAndNotify(nowMillis)
        appContext?.let { context ->
            try {
                ContextCompat.startForegroundService(context, Intent(context, DeskAlarmService::class.java))
            } catch (e: IllegalStateException) {
                Log.w("DeskAlarms", "Cannot start alarm playback service", e)
            }
        }
        val context = appContext
        if (context != null) {
            soundPlayer = AlarmSoundPlayer(context).also { it.play(alarm) }
            if (alarm.vibrationEnabled) try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                val pattern = if (alarm.soundEnabled) longArrayOf(0, 500, 300, 500, 300, 800) else longArrayOf(0, 300, 400, 300)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (_: Exception) {
            }
        }


        handler.removeCallbacks(autoSnoozeRunnable)
        handler.postDelayed(autoSnoozeRunnable, AUTO_SNOOZE_TIMEOUT_MS)
        listeners.forEach { it.onAlarmRinging(ringingAlarm) }
        evaluateAlarms(nowMillis, allowRinging = false)
        scheduleTickIfNeeded()
    }

    private fun stopRinging() {
        handler.removeCallbacks(autoSnoozeRunnable)
        soundPlayer?.stop()
        soundPlayer = null
        vibrator?.cancel()
        vibrator = null
        ringingAlarm = null
        listeners.forEach { it.onAlarmRinging(null) }
    }

    private fun changed(nowMillis: Long = timeSource()) {
        saveAndNotify(nowMillis)
        evaluateAlarms(nowMillis, allowRinging = false)
        // Deliver any other due occurrence after dismissing or snoozing an overlapping alarm.
        if (ringingAlarm == null) {
            alarmsList.firstOrNull { it.enabled && it.scheduledTriggerMillis?.let { t -> t <= nowMillis } == true }
                ?.let { deliver(it.id, it.scheduledTriggerMillis!!, nowMillis) }
        }
        scheduleTickIfNeeded()
    }

    private fun saveAndNotify(nowMillis: Long) {
        for (index in alarmsList.indices) {
            var alarm = alarmsList[index]
            if (!alarm.enabled) {
                alarm = alarm.copy(scheduledTriggerMillis = null)
            } else {
                val pending = alarm.scheduledTriggerMillis
                if (pending != null && nowMillis - pending > MAX_LATENESS_MS) {
                    alarm = consume(alarm, pending)
                }
                if (alarm.enabled && alarm.scheduledTriggerMillis == null) {
                    alarm = alarm.copy(scheduledTriggerMillis = AlarmScheduler.nextTriggerTimeMillis(alarm, nowMillis))
                }
            }
            alarmsList[index] = alarm
        }
        // Commit before registering with the OS: a new process must see the same occurrence.
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_ALARMS_JSON, AlarmScheduler.toJson(alarmsList))?.commit()
        appContext?.let { context -> alarmsList.forEach { SystemAlarmScheduler.schedule(context, it) } }
        val snapshot = getAlarms()
        listeners.forEach { it.onAlarmsChanged(snapshot) }
    }
}
