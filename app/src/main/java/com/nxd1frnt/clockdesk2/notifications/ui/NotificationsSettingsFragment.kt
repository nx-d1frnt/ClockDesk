package com.nxd1frnt.clockdesk2.notifications.ui

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.ui.DeskNotificationHelper
import com.nxd1frnt.clockdesk2.smartchips.ui.NotificationChipSettingsActivity
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem

class NotificationsSettingsFragment : BaseM3SettingsFragment() {

    private val addFontLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && isAdded) {
            val ctx = requireContext()
            val newFontId = DeskNotificationHelper.addCustomFont(ctx, uri)
            if (newFontId != null) {
                val fonts = DeskNotificationHelper.getFontsList(ctx)
                val newIdx = fonts.indexOfFirst {
                    DeskNotificationHelper.getFontIdentifier(ctx, it) == newFontId
                }.takeIf { it >= 1 } ?: 1
                prefs.edit()
                    .putString(DeskNotificationHelper.KEY_FONT_ID, newFontId)
                    .putInt(DeskNotificationHelper.KEY_FONT_INDEX, newIdx)
                    .apply()
                rebuildSettings()
            }
        }
    }

    override fun buildSettings(): List<SettingsItem> = settings {
        val popupEnabled = prefs.getBoolean("desk_notification_popup_enabled", true)

        category(getString(R.string.pref_cat_floating_banners)) {
            switch(
                key = "desk_notification_popup_enabled",
                title = getString(R.string.desk_notification_popup_title),
                summary = getString(R.string.desk_notification_popup_summary),
                iconRes = R.drawable.ic_notifications,
                defaultValue = true
            )

            notificationPreview(
                isEnabled = { popupEnabled }
            )

            notificationAppearance(
                isEnabled = { popupEnabled },
                onAddFontRequested = {
                    addFontLauncher.launch(
                        arrayOf(
                            "font/ttf",
                            "font/otf",
                            "font/*",
                            "application/x-font-ttf",
                            "application/x-font-otf",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                }
            )

            choice(
                key = DeskNotificationHelper.KEY_STYLE,
                title = getString(R.string.desk_notification_style_title),
                summary = getString(R.string.desk_notification_style_summary),
                entries = resources.getStringArray(R.array.desk_notification_style_entries),
                entryValues = resources.getStringArray(R.array.desk_notification_style_values),
                defaultValue = "standard",
                isEnabled = { popupEnabled }
            )

            choice(
                key = DeskNotificationHelper.KEY_POSITION,
                title = getString(R.string.desk_notification_position_title),
                summary = getString(R.string.desk_notification_position_summary),
                entries = resources.getStringArray(R.array.desk_notification_position_entries),
                entryValues = resources.getStringArray(R.array.desk_notification_position_values),
                defaultValue = "top_center",
                isEnabled = { popupEnabled }
            )

            slider(
                key = DeskNotificationHelper.KEY_SCALE,
                title = getString(R.string.desk_notification_scale_title),
                summary = getString(R.string.desk_notification_scale_summary),
                min = 80f,
                max = 140f,
                step = 5f,
                defaultValue = 100f,
                valueFormatter = { "${it.toInt()}%" },
                isEnabled = { popupEnabled }
            )

            slider(
                key = DeskNotificationHelper.KEY_BG_OPACITY,
                title = getString(R.string.desk_notification_bg_opacity_title),
                summary = getString(R.string.desk_notification_bg_opacity_summary),
                min = 40f,
                max = 100f,
                step = 5f,
                defaultValue = 100f,
                valueFormatter = { "${it.toInt()}%" },
                isEnabled = { popupEnabled }
            )

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
                key = DeskNotificationHelper.KEY_MONOCHROME_ICON,
                title = getString(R.string.desk_notification_monochrome_icon_title),
                summary = getString(R.string.desk_notification_monochrome_icon_summary),
                defaultValue = false,
                isEnabled = { popupEnabled }
            )

            switch(
                key = DeskNotificationHelper.KEY_SHOW_DEVICE,
                title = getString(R.string.desk_notification_show_device_title),
                summary = getString(R.string.desk_notification_show_device_summary),
                defaultValue = true,
                isEnabled = { popupEnabled }
            )

            switch(
                key = DeskNotificationHelper.KEY_SHOW_DISMISS,
                title = getString(R.string.desk_notification_show_dismiss_title),
                summary = getString(R.string.desk_notification_show_dismiss_summary),
                defaultValue = true,
                isEnabled = { popupEnabled }
            )

            switch(
                key = DeskNotificationHelper.KEY_HIDE_SENSITIVE,
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
