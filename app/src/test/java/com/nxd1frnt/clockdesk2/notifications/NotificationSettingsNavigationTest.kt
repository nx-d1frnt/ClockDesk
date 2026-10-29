package com.nxd1frnt.clockdesk2.notifications

import android.app.Application
import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.notifications.ui.DeviceBatteryNotificationSettingsFragment
import com.nxd1frnt.clockdesk2.notifications.ui.NotificationBannerSettingsFragment
import com.nxd1frnt.clockdesk2.notifications.ui.NotificationsSettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class NotificationSettingsNavigationTest {
    @Test fun returningFromBatterySettingsRefreshesOverviewWithoutChangingOtherPreferences() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        controller.setup()
        activity.setContentView(FrameLayout(activity).apply { id = R.id.settings_container })
        val prefs = activity.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AppNotificationManager.BATTERY_ENABLED, true)
            .putInt(AppNotificationManager.BATTERY_THRESHOLD, 35)
            .putBoolean(AppNotificationManager.TIMER_BANNER, false)
            .putBoolean("desk_notification_popup_enabled", false).commit()
        val overview = NotificationsSettingsFragment()
        activity.supportFragmentManager.beginTransaction().replace(R.id.settings_container, overview).commitNow()
        fun batteryRow() = overview.buildSettings().filterIsInstance<SettingsItem.Clickable>()
            .single { it.key == "notification_battery_settings" }
        try {
            assertTrue(batteryRow().summary!!.contains("35%"))
            batteryRow().onClick()
            activity.supportFragmentManager.executePendingTransactions()
            val detail = activity.supportFragmentManager.findFragmentById(R.id.settings_container)
                as DeviceBatteryNotificationSettingsFragment
            prefs.edit().putBoolean(AppNotificationManager.BATTERY_ENABLED, false).commit()
            // Turning the category off removes controls that no longer apply.
            assertTrue(detail.buildSettings().none { it is SettingsItem.Slider })
            activity.supportFragmentManager.popBackStackImmediate()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(activity.getString(R.string.notification_alerts_off), batteryRow().summary)
            assertEquals(35, prefs.getInt(AppNotificationManager.BATTERY_THRESHOLD, 0))
            assertFalse(prefs.getBoolean(AppNotificationManager.TIMER_BANNER, true))
            assertFalse(prefs.getBoolean("desk_notification_popup_enabled", true))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun appearancePreviewRemainsAvailableWhenAllBannerTypesAreOff() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        controller.setup()
        activity.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean(AppNotificationManager.TIMER_BANNER, false)
            .putBoolean(AppNotificationManager.BATTERY_BANNER, false)
            .putBoolean("desk_notification_popup_enabled", false).commit()
        val appearance = NotificationBannerSettingsFragment()
        activity.supportFragmentManager.beginTransaction().add(appearance, "appearance").commitNow()
        try {
            val items = appearance.buildSettings()
            assertTrue(items.filterIsInstance<SettingsItem.NotificationPreview>().single().isEnabled())
            assertTrue(items.filterIsInstance<SettingsItem.Entry>().all { it.isEnabled() })
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
