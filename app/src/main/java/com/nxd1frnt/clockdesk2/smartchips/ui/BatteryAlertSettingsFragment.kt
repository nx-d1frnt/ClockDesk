package com.nxd1frnt.clockdesk2.smartchips.ui

import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem

class BatteryAlertSettingsFragment : BaseM3SettingsFragment() {

    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.battery_alert_behavior_category)) {
            switch(
                key = "battery_alert_show_charging",
                title = getString(R.string.battery_alert_show_charging_title),
                summary = getString(R.string.battery_alert_show_charging_summary),
                iconRes = R.drawable.ic_battery_saver,
                defaultValue = true
            )

            switch(
                key = "battery_alert_show_full",
                title = getString(R.string.battery_alert_show_full_title),
                summary = getString(R.string.battery_alert_show_full_summary),
                defaultValue = true
            )

            switch(
                key = "battery_alert_show_saver",
                title = getString(R.string.battery_alert_show_saver_title),
                summary = getString(R.string.battery_alert_show_saver_summary),
                defaultValue = true
            )

            switch(
                key = "battery_alert_show_low",
                title = getString(R.string.battery_alert_show_low_title),
                summary = getString(R.string.battery_alert_show_low_summary),
                defaultValue = true
            )

            slider(
                key = "battery_alert_low_threshold",
                title = getString(R.string.battery_alert_low_threshold_title),
                summary = getString(R.string.battery_alert_low_threshold_summary),
                min = 5f,
                max = 50f,
                step = 1f,
                defaultValue = 20f,
                valueFormatter = { "${it.toInt()}%" },
                isEnabled = { prefs.getBoolean("battery_alert_show_low", true) }
            )
        }
    }
}
