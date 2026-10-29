package com.nxd1frnt.clockdesk2.notifications.ui

import android.content.Intent
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.notifications.AppNotificationManager
import com.nxd1frnt.clockdesk2.smartchips.ui.NotificationChipSettingsActivity
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem

/** Overview: choose a notification type before changing its behavior. */
class NotificationsSettingsFragment : BaseM3SettingsFragment() {
    override fun onResume() {
        super.onResume()
        rebuildSettings()
    }

    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.notification_types_category)) {
            action(
                key = "notification_timer_settings",
                title = getString(R.string.notification_timers_title),
                summary = getString(R.string.notification_behavior_summary,
                    getString(if (prefs.getBoolean(AppNotificationManager.TIMER_BANNER, true))
                        R.string.notification_delivery_banner else R.string.notification_delivery_list),
                    getString(if (prefs.getBoolean("timer_is_silent", false))
                        R.string.notification_alarm_vibration else R.string.notification_alarm_sound)),
                iconRes = R.drawable.ic_timer,
                showChevron = true
            ) { openSubFragment(TimerNotificationSettingsFragment()) }

            val batterySummary = if (prefs.getBoolean(AppNotificationManager.BATTERY_ENABLED, true)) {
                getString(R.string.notification_battery_status,
                    prefs.getInt(AppNotificationManager.BATTERY_THRESHOLD, 20),
                    getString(if (prefs.getBoolean(AppNotificationManager.BATTERY_BANNER, true))
                        R.string.notification_delivery_banner else R.string.notification_delivery_list))
            } else getString(R.string.notification_alerts_off)
            action(
                key = "notification_battery_settings",
                title = getString(R.string.notification_device_battery_title),
                summary = batterySummary,
                iconRes = R.drawable.ic_battery_alert,
                showChevron = true
            ) { openSubFragment(DeviceBatteryNotificationSettingsFragment()) }

            action(
                key = "notification_remote_settings",
                title = getString(R.string.notification_remote_title),
                summary = getString(R.string.notification_remote_summary),
                iconRes = R.drawable.ic_devices,
                showChevron = true
            ) { openSubFragment(RemoteNotificationSettingsFragment()) }
        }

        category(getString(R.string.notification_customize_category)) {
            action(
                key = "notification_banner_settings",
                title = getString(R.string.notification_banner_appearance),
                summary = getString(R.string.notification_banner_appearance_summary),
                iconRes = R.drawable.ic_palette_swatch,
                showChevron = true
            ) { openSubFragment(NotificationBannerSettingsFragment()) }
            action(
                key = "pref_key_notification_chip_settings",
                title = getString(R.string.notification_chip_title),
                summary = getString(R.string.notification_chip_summary),
                iconRes = R.drawable.ic_notifications,
                showChevron = true
            ) { startActivity(Intent(requireContext(), NotificationChipSettingsActivity::class.java)) }
        }

        category(getString(R.string.notification_list_category)) {
            action(
                key = "pref_key_open_notification_shade",
                title = getString(R.string.notification_open_list),
                summary = getString(R.string.notification_open_list_summary),
                iconRes = R.drawable.ic_notifications
            ) { NotificationShadeBottomSheet.show(requireContext()) }
        }
    }
}
