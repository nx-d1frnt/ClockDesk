package com.nxd1frnt.clockdesk2.ui.settings

import android.content.Context
import androidx.fragment.app.Fragment
import com.nxd1frnt.clockdesk2.R

data class SettingsCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconResId: Int,
    val fragmentClass: Class<out Fragment>,
    val groupId: Int = 0
)

object SettingsCategoryProvider {
    fun getCategories(context: Context): List<SettingsCategory> {
        return listOf(
            // Group 0: Clock & Display Experience
            SettingsCategory(
                id = "general",
                title = context.getString(R.string.location_weather_settings_title),
                subtitle = context.getString(R.string.location_weather_settings_subtitle),
                iconResId = R.drawable.ic_sun_clock_outline,
                fragmentClass = GeneralSettingsFragment::class.java,
                groupId = 0
            ),
            SettingsCategory(
                id = "display",
                title = context.getString(R.string.display_settings_title),
                subtitle = context.getString(R.string.display_settings_subtitle),
                iconResId = R.drawable.ic_blur_on,
                fragmentClass = DisplaySettingsFragment::class.java,
                groupId = 0
            ),
            SettingsCategory(
                id = "smart_chips",
                title = context.getString(R.string.smart_chips_settings_title),
                subtitle = context.getString(R.string.smart_chips_settings_subtitle),
                iconResId = R.drawable.ic_widgets_outline,
                fragmentClass = com.nxd1frnt.clockdesk2.smartchips.ui.SmartChipsPluginsFragment::class.java,
                groupId = 0
            ),

            // Group 1: Media & Connectivity
            SettingsCategory(
                id = "music",
                title = context.getString(R.string.music_settings_title),
                subtitle = context.getString(R.string.music_settings_subtitle),
                iconResId = R.drawable.ic_music_icon,
                fragmentClass = MusicSettingsFragment::class.java,
                groupId = 1
            ),
            SettingsCategory(
                id = "notifications",
                title = context.getString(R.string.notifications_settings_title),
                subtitle = context.getString(R.string.notifications_settings_subtitle),
                iconResId = R.drawable.ic_notifications,
                fragmentClass = com.nxd1frnt.clockdesk2.notifications.ui.NotificationsSettingsFragment::class.java,
                groupId = 1
            ),
            SettingsCategory(
                id = "deskconnect",
                title = context.getString(R.string.deskconnect_settings_title),
                subtitle = context.getString(R.string.deskconnect_settings_subtitle),
                iconResId = R.drawable.ic_devices,
                fragmentClass = com.nxd1frnt.clockdesk2.connect.ui.DeskConnectSettingsFragment::class.java,
                groupId = 1
            ),

            // Group 2: System & Maintenance
            SettingsCategory(
                id = "battery",
                title = context.getString(R.string.battery_saver_settings_title),
                subtitle = context.getString(R.string.battery_saver_settings_subtitle),
                iconResId = R.drawable.ic_battery_saver,
                fragmentClass = BatterySettingsFragment::class.java,
                groupId = 2
            ),
            SettingsCategory(
                id = "performance",
                title = context.getString(R.string.performance_settings_title),
                subtitle = context.getString(R.string.performance_settings_subtitle),
                iconResId = R.drawable.ic_speedometer,
                fragmentClass = PerformanceSettingsFragment::class.java,
                groupId = 2
            ),
            SettingsCategory(
                id = "backup",
                title = context.getString(R.string.backup_restore_title),
                subtitle = context.getString(R.string.backup_restore_subtitle),
                iconResId = R.drawable.ic_backup_restore,
                fragmentClass = BackupSettingsFragment::class.java,
                groupId = 2
            ),

            // Group 3: Updates & Information
            SettingsCategory(
                id = "updates",
                title = context.getString(R.string.updates_settings_title),
                subtitle = context.getString(R.string.updates_settings_subtitle),
                iconResId = R.drawable.update,
                fragmentClass = UpdatesSettingsFragment::class.java,
                groupId = 3
            ),
            SettingsCategory(
                id = "about",
                title = context.getString(R.string.about_title),
                subtitle = context.getString(R.string.about_subtitle),
                iconResId = R.drawable.ic_info_outline,
                fragmentClass = AboutFragment::class.java,
                groupId = 3
            )
        )
    }
}
