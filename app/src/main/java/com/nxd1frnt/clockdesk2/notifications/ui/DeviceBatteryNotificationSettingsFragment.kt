package com.nxd1frnt.clockdesk2.notifications.ui

import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.notifications.AppNotificationManager
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem

class DeviceBatteryNotificationSettingsFragment : BaseM3SettingsFragment() {
    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.notification_device_battery_title)) {
            switch(
                key = AppNotificationManager.BATTERY_ENABLED,
                title = getString(R.string.notification_low_battery_alerts),
                summary = getString(R.string.notification_low_battery_summary),
                defaultValue = true
            )
            if (prefs.getBoolean(AppNotificationManager.BATTERY_ENABLED, true)) {
                slider(
                    key = AppNotificationManager.BATTERY_THRESHOLD,
                    title = getString(R.string.notification_battery_notify_at),
                    min = 5f, max = 50f, step = 5f, defaultValue = 20f,
                    valueFormatter = { getString(R.string.notification_battery_level, it.toInt()) }
                )
                switch(
                    key = AppNotificationManager.BATTERY_BANNER,
                    title = getString(R.string.notification_show_banner),
                    summary = getString(R.string.notification_battery_banner_summary),
                    defaultValue = true
                )
            }
        }
    }
}
