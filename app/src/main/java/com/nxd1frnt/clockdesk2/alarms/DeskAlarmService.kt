package com.nxd1frnt.clockdesk2.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.MainActivity

/** Keeps playback alive even when there is no desk activity. */
class DeskAlarmService : Service() {
    companion object {
        private const val CHANNEL = "desk_alarm_ringing"
        private const val QUIET_CHANNEL = "desk_alarm_playback"
        private const val NOTIFICATION_ID = 2401
        private const val ACTION_DISMISS = "com.nxd1frnt.clockdesk2.alarms.DISMISS"
        private const val ACTION_SNOOZE = "com.nxd1frnt.clockdesk2.alarms.SNOOZE"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val presentationListener: () -> Unit = {
        DeskAlarmManager.ringingAlarm?.let { publishNotification(it, allowFullScreen = false, updating = true) }
    }

    override fun onCreate() {
        super.onCreate()
        AlarmPresentation.addListener(presentationListener)
    }
    private val listener = object : DeskAlarmManager.AlarmListener {
        override fun onAlarmRinging(alarm: DeskAlarm?) {
            if (alarm == null) {
                ServiceCompat.stopForeground(this@DeskAlarmService, STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> DeskAlarmManager.dismissActive()
            ACTION_SNOOZE -> DeskAlarmManager.snoozeActive()
        }
        val alarm = DeskAlarmManager.ringingAlarm
        if (alarm == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        publishNotification(alarm, allowFullScreen = !AlarmPresentation.isDeskVisible)
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ClockDesk:alarm").apply {
                    acquire(11 * 60_000L)
                }
        }
        DeskAlarmManager.addListener(listener)
        return START_NOT_STICKY
    }

    private fun publishNotification(alarm: DeskAlarm, allowFullScreen: Boolean, updating: Boolean = false) {
        val inApp = AlarmPresentation.isDeskVisible
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL,
                getString(R.string.alarm_ringing_title), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(QUIET_CHANNEL,
                getString(R.string.alarm_playback_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            })
        }
        val open = PendingIntent.getActivity(this, 2401, Intent(this, MainActivity::class.java).apply {
            putExtra("show_ringing_alarm", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(action: String, code: Int) = PendingIntent.getService(this, code,
            Intent(this, DeskAlarmService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, if (inApp) QUIET_CHANNEL else CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(alarm.label.ifBlank { getString(R.string.alarm_ringing_title) })
            .setContentText(android.text.format.DateFormat.getTimeFormat(this)
                .format(java.util.Date(alarm.scheduledTriggerMillis ?: System.currentTimeMillis())))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(if (inApp) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setSilent(inApp)
            .setOnlyAlertOnce(inApp || updating)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.alarm_snooze_duration, alarm.snoozeMinutes), action(ACTION_SNOOZE, 1))
            .addAction(0, getString(R.string.alarm_dismiss), action(ACTION_DISMISS, 2))
        if (allowFullScreen && !inApp) builder.setFullScreenIntent(open, true)
        val notification = builder.build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
    }

    override fun onDestroy() {
        AlarmPresentation.removeListener(presentationListener)
        DeskAlarmManager.removeListener(listener)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
