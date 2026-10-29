package com.nxd1frnt.clockdesk2.notifications.ui

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.notifications.AppNotificationManager
import com.nxd1frnt.clockdesk2.timer.TimerManager
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

class TimerNotificationSettingsFragment : BaseM3SettingsFragment() {
    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.notification_timer_finished_category)) {
            switch(
                key = AppNotificationManager.TIMER_BANNER,
                title = getString(R.string.notification_show_banner),
                summary = getString(R.string.notification_timer_banner_summary),
                defaultValue = true
            )
            action(
                key = "notification_timer_alarm",
                title = getString(R.string.notification_timer_alarm),
                summary = getString(if (prefs.getBoolean("timer_is_silent", false))
                    R.string.notification_alarm_vibration else R.string.notification_alarm_sound),
                showChevron = true
            ) { showAlarmOptions() }
        }
    }

    private fun showAlarmOptions() {
        val options = arrayOf(getString(R.string.notification_alarm_sound), getString(R.string.notification_alarm_vibration))
        MaterialAlertDialogBuilder(UiMonetThemeManager.wrapContext(requireContext()))
            .setTitle(R.string.notification_timer_alarm)
            .setSingleChoiceItems(options, if (prefs.getBoolean("timer_is_silent", false)) 1 else 0) { dialog, index ->
                TimerManager.setSilent(index == 1)
                rebuildSettings()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
