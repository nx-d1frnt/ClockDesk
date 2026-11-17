package com.nxd1frnt.clockdesk2.notifications.ui

import android.view.View
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.alarms.DeskAlarm
import com.nxd1frnt.clockdesk2.alarms.DeskAlarmManager
import com.nxd1frnt.clockdesk2.connect.model.AppAlertType
import com.nxd1frnt.clockdesk2.connect.model.DeskNotification
import com.nxd1frnt.clockdesk2.connect.model.NotificationOrigin
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

/** Actions are tied to an occurrence, so an old/recycled view cannot stop a different alarm. */
internal object AlarmNotificationActions {
    private fun active(item: DeskNotification): DeskAlarm? {
        if (item.origin != NotificationOrigin.APP || item.alertType != AppAlertType.ALARM_RINGING) return null
        return DeskAlarmManager.ringingAlarm?.takeIf {
            it.id == item.relatedAlarmId && it.scheduledTriggerMillis == item.alarmTriggerMillis
        }
    }

    fun snooze(item: DeskNotification) { if (active(item) != null) DeskAlarmManager.snoozeActive() }
    fun dismiss(item: DeskNotification) { if (active(item) != null) DeskAlarmManager.dismissActive() }

    fun bind(container: LinearLayout, item: DeskNotification) {
        container.removeAllViews()
        val alarm = active(item)
        container.visibility = if (alarm == null) View.GONE else View.VISIBLE
        if (alarm == null) return
        val context = container.context
        val palette = UiMonetThemeManager.getPalette(context)
        fun button(label: String, tagName: String, action: () -> Unit) {
            container.addView(MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                tag = tagName
                minHeight = (48 * resources.displayMetrics.density).toInt()
                setTextColor(palette.primary)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        button(context.getString(R.string.alarm_snooze_duration, alarm.snoozeMinutes), "alarm_notification_snooze") { snooze(item) }
        button(context.getString(R.string.alarm_dismiss), "alarm_notification_dismiss") { dismiss(item) }
    }
}
