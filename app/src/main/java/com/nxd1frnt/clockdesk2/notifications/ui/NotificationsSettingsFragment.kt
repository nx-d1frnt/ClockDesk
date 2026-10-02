package com.nxd1frnt.clockdesk2.notifications.ui

import android.content.Intent
import android.os.Bundle
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ui.NotificationChipSettingsActivity
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem

class NotificationsSettingsFragment : BaseM3SettingsFragment() {

    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.pref_cat_floating_banners)) {
            switch(
                key = "desk_notification_popup_enabled",
                title = getString(R.string.desk_notification_popup_title),
                summary = getString(R.string.desk_notification_popup_summary),
                iconRes = R.drawable.ic_notifications,
                defaultValue = true
            )

            val popupEnabled = prefs.getBoolean("desk_notification_popup_enabled", true)

            slider(
                key = "desk_notification_duration",
                title = getString(R.string.desk_notification_duration_title),
                summary = getString(R.string.desk_notification_duration_summary),
                min = 2f,
                max = 15f,
                step = 1f,
                defaultValue = 5f,
                valueFormatter = { "${it.toInt()} s" },
                isEnabled = { popupEnabled }
            )

            switch(
                key = "desk_notification_hide_sensitive",
                title = getString(R.string.desk_notification_hide_sensitive_title),
                summary = getString(R.string.desk_notification_hide_sensitive_summary),
                defaultValue = false,
                isEnabled = { popupEnabled }
            )
        }

        category(getString(R.string.pref_cat_phone_calls)) {
            switch(
                key = "desk_call_overlay_enabled",
                title = getString(R.string.desk_call_overlay_title),
                summary = getString(R.string.desk_call_overlay_summary),
                defaultValue = true
            )

            switch(
                key = "desk_call_missed_notification",
                title = getString(R.string.desk_call_missed_notification_title),
                summary = getString(R.string.desk_call_missed_notification_summary),
                defaultValue = true
            )
        }

        category(getString(R.string.pref_cat_smart_chip)) {
            action(
                key = "pref_key_notification_chip_settings",
                title = getString(R.string.pref_notification_chip_settings_title),
                summary = getString(R.string.pref_notification_chip_settings_summary),
                showChevron = true
            ) {
                val intent = Intent(requireContext(), NotificationChipSettingsActivity::class.java)
                startActivity(intent)
            }

            action(
                key = "pref_key_open_notification_shade",
                title = getString(R.string.pref_notification_shade_shortcut_title),
                summary = getString(R.string.pref_notification_shade_shortcut_summary),
                showChevron = true
            ) {
                NotificationShadeBottomSheet.show(requireContext())
            }
        }
    }
}
