package com.nxd1frnt.clockdesk2.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DeskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DeskAlarmManager.init(context)
        if (intent.action == SystemAlarmScheduler.ACTION_FIRE) {
            DeskAlarmManager.deliver(
                intent.getStringExtra(SystemAlarmScheduler.EXTRA_ID) ?: return,
                intent.getLongExtra(SystemAlarmScheduler.EXTRA_TRIGGER, -1L)
            )
        } else {
            // Reboot and package update only re-register delivery; they must not start a playback service.
            DeskAlarmManager.refreshSchedule(
                recalculateWallClock = intent.action == Intent.ACTION_TIME_CHANGED ||
                    intent.action == Intent.ACTION_TIMEZONE_CHANGED
            )
        }
    }
}
