package com.nxd1frnt.clockdesk2.smartchips.ui

import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem

class NotificationChipSettingsFragment : BaseM3SettingsFragment() {

    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.notification_chip_display_category)) {
            choice(
                key = "notification_chip_single_format",
                title = getString(R.string.notification_chip_single_format_title),
                entries = resources.getStringArray(R.array.notification_chip_single_format_entries),
                entryValues = resources.getStringArray(R.array.notification_chip_single_format_values),
                defaultValue = "title"
            )

            choice(
                key = "notification_chip_multi_format",
                title = getString(R.string.notification_chip_multi_format_title),
                entries = resources.getStringArray(R.array.notification_chip_multi_format_entries),
                entryValues = resources.getStringArray(R.array.notification_chip_multi_format_values),
                defaultValue = "compact"
            )

            switch(
                key = "notification_chip_use_app_icon",
                title = getString(R.string.notification_chip_use_app_icon_title),
                summary = getString(R.string.notification_chip_use_app_icon_summary),
                defaultValue = true
            )
        }

        category(getString(R.string.notification_chip_behavior_category)) {
            choice(
                key = "notification_chip_tap_action",
                title = getString(R.string.notification_chip_tap_action_title),
                entries = resources.getStringArray(R.array.notification_chip_tap_action_entries),
                entryValues = resources.getStringArray(R.array.notification_chip_tap_action_values),
                defaultValue = "shade"
            )
        }
    }
}
