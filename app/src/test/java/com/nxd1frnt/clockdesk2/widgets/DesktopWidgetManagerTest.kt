package com.nxd1frnt.clockdesk2.widgets

import android.app.Activity
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.desks.DeskAppearancePreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DesktopWidgetManagerTest {
    @Test fun resetClearsPositionsAndPersistsDefaultsOnlyForCurrentDesk() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        try {
            for (key in listOf(DesktopWidgetManager.DEFAULT_LAYOUT_KEY, "secondary")) {
                val manager = DesktopWidgetManager(activity, ConstraintLayout(activity), layoutKey = key)
                val positions = activity.getSharedPreferences(
                    DesktopWidgetManager.positionPreferencesName(key), Context.MODE_PRIVATE)
                positions.edit()
                    .putBoolean("is_layout_initialized", true)
                    .putBoolean("time_text_individual_free_mode", true)
                    .putBoolean("time_text_has_user_saved_pos", true)
                    .putFloat("time_text_x", 321f)
                    .putFloat("time_text_y", 123f)
                    .putInt("time_text_align_h", 2)
                    .putInt("time_text_order_index", 0)
                    .apply()
                val other = activity.getSharedPreferences(
                    DesktopWidgetManager.positionPreferencesName("untouched"), Context.MODE_PRIVATE)
                other.edit().putFloat("time_text_x", 456f).apply()
                manager.getActiveInstances().toList().forEach { manager.removeWidget(it) }
                manager.addWidget(DesktopWidgetType.WEATHER)
                val appearance = DeskAppearancePreferences.profile(activity, key)
                appearance.edit().putString("clockStyle", "custom")
                    .putFloat("timeSize", 222f).putInt("dateColor", 123)
                    .putString("mediaStyle", "custom").apply()
                val otherAppearance = DeskAppearancePreferences.profile(activity, "untouched")
                otherAppearance.edit().putString("clockStyle", "keep").apply()
                val global = activity.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
                global.edit().putString("background_type", "keep").apply()
                var notifications = 0
                manager.onWidgetsChanged = { notifications++ }

                manager.resetToDefaults()

                assertEquals(1, notifications)
                listOf("clockStyle", "timeSize", "dateColor", "mediaStyle").forEach {
                    assertFalse("Appearance key $it must be reset", appearance.contains(it))
                }
                assertEquals("keep", otherAppearance.getString("clockStyle", null))
                assertEquals("keep", global.getString("background_type", null))
                if (key != DesktopWidgetManager.DEFAULT_LAYOUT_KEY) {
                    assertTrue(appearance.getBoolean("profile_initialized", false))
                    assertFalse(DeskAppearancePreferences.profile(activity, key).contains("clockStyle"))
                }
                assertTrue(positions.all.isEmpty())
                assertEquals(456f, other.getFloat("time_text_x", 0f), 0f)
                val expected = listOf(DesktopWidgetType.MEDIA, DesktopWidgetType.DATE,
                    DesktopWidgetType.TIME, DesktopWidgetType.SMART_CHIPS)
                val restored = DesktopWidgetManager(activity, ConstraintLayout(activity), layoutKey = key)
                assertEquals(expected, restored.getActiveInstances().map { it.type })
                restored.getActiveInstances().forEachIndexed { index, widget ->
                    assertEquals(index, widget.orderIndex)
                    assertFalse(widget.isFreeMode)
                    assertEquals(0f, widget.posX, 0f)
                    assertEquals(0f, widget.posY, 0f)
                    assertEquals(1, widget.alignH)
                    assertEquals(2, widget.alignV)
                }
            }
        } finally {
            activity.finish()
        }
    }
}
