package com.nxd1frnt.clockdesk2.notifications.ui

import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem

class RemoteNotificationSettingsFragment : BaseM3SettingsFragment() {
    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.notification_forwarded_category)) {
            switch(
                key = "desk_notification_popup_enabled",
                title = getString(R.string.notification_show_banner),
                summary = getString(R.string.notification_forwarded_banner_summary),
                defaultValue = true
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
    }
}
