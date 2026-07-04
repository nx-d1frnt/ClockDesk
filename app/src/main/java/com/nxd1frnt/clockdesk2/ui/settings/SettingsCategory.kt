package com.nxd1frnt.clockdesk2.ui.settings

import android.content.Context
import androidx.fragment.app.Fragment
import com.nxd1frnt.clockdesk2.R

data class SettingsCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconResId: Int,
    val fragmentClass: Class<out Fragment>
)

object SettingsCategoryProvider {
    fun getCategories(context: Context): List<SettingsCategory> {
        return listOf(
            SettingsCategory(
                id = "general",
                title = context.getString(R.string.location_weather_settings_title),
                subtitle = context.getString(R.string.location_weather_settings_subtitle),
                iconResId = R.drawable.ic_sun_clock_outline,
                fragmentClass = GeneralSettingsFragment::class.java
            ),
            SettingsCategory(
                id = "music",
                title = context.getString(R.string.music_settings_title),
                subtitle = context.getString(R.string.music_settings_subtitle),
                iconResId = R.drawable.ic_music_icon,
                fragmentClass = MusicSettingsFragment::class.java
            ),
            SettingsCategory(
                id = "display",
                title = context.getString(R.string.display_settings_title),
                subtitle = context.getString(R.string.display_settings_subtitle),
                iconResId = R.drawable.ic_blur_on,
                fragmentClass = DisplaySettingsFragment::class.java
            ),
            SettingsCategory(
                id = "battery",
                title = context.getString(R.string.battery_saver_settings_title),
                subtitle = context.getString(R.string.battery_saver_settings_subtitle),
                iconResId = R.drawable.ic_battery_saver,
                fragmentClass = BatterySettingsFragment::class.java
            ),
            SettingsCategory(
                id = "performance",
                title = context.getString(R.string.performance_settings_title),
                subtitle = context.getString(R.string.performance_settings_subtitle),
                iconResId = R.drawable.ic_speedometer,
                fragmentClass = PerformanceSettingsFragment::class.java
            ),
            SettingsCategory(
                id = "smart_chips",
                title = context.getString(R.string.smart_chips_settings_title),
                subtitle = context.getString(R.string.smart_chips_settings_subtitle),
                iconResId = R.drawable.ic_widgets_outline,
                fragmentClass = com.nxd1frnt.clockdesk2.smartchips.ui.SmartChipsPluginsFragment::class.java
            ),
            SettingsCategory(
                id = "backup",
                title = context.getString(R.string.backup_restore_title),
                subtitle = context.getString(R.string.backup_restore_subtitle),
                iconResId = R.drawable.ic_backup_restore,
                fragmentClass = BackupSettingsFragment::class.java
            ),
            SettingsCategory(
                id = "about",
                title = context.getString(R.string.about_title),
                subtitle = context.getString(R.string.about_subtitle),
                iconResId = R.drawable.ic_info_outline,
                fragmentClass = AboutFragment::class.java
            )
        )
    }
}
