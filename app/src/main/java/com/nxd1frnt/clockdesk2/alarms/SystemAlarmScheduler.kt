package com.nxd1frnt.clockdesk2.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.nxd1frnt.clockdesk2.ui.MainActivity

/** The persisted occurrence is also the identity of a delivery; stale broadcasts cannot ring edits. */
object SystemAlarmScheduler {
    const val ACTION_FIRE = "com.nxd1frnt.clockdesk2.alarms.FIRE"
    const val EXTRA_ID = "alarm_id"
    const val EXTRA_TRIGGER = "alarm_trigger"

    fun canSchedule(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    fun schedule(context: Context, alarm: DeskAlarm) {
        cancel(context, alarm.id)
        val trigger = alarm.scheduledTriggerMillis ?: return
        if (!alarm.enabled || !canSchedule(context)) return
        val operation = PendingIntent.getBroadcast(context, 0,
            deliveryIntent(context, alarm.id).putExtra(EXTRA_TRIGGER, trigger),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            context.getSystemService(AlarmManager::class.java)
                .setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), operation)
        } catch (e: SecurityException) {
            // Access can be revoked between the check and the call. The desk UI shows missing access.
            Log.w("DeskAlarms", "Exact alarm access was revoked", e)
        }
    }

    fun cancel(context: Context, id: String) {
        val operation = PendingIntent.getBroadcast(context, 0, deliveryIntent(context, id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(operation)
        operation.cancel()
    }

    private fun deliveryIntent(context: Context, id: String) =
        Intent(context, DeskAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.Builder().scheme("clockdesk").authority("alarm").appendPath(id).build()
            putExtra(EXTRA_ID, id)
        }
}
