package com.nxd1frnt.clockdesk2.alarms

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.desks.ui.AlarmsDeskView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AlarmSettingsTest {
    private var now = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 13, 6, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setup() {
        DeskAlarmManager.getAlarms().forEach { DeskAlarmManager.deleteAlarm(it.id) }
        ReflectionHelpers.setStaticField(DeskAlarmManager::class.java, "appContext", null)
        context.getSharedPreferences("ClockDeskPrefs", 0).edit().clear().commit()
        DeskAlarmManager.timeSource = { now }
        DeskAlarmManager.init(context)
    }
    @After fun cleanup() {
        DeskAlarmManager.getAlarms().forEach { DeskAlarmManager.deleteAlarm(it.id) }
        DeskAlarmManager.timeSource = System::currentTimeMillis
    }

    @Test fun settingsRoundTripAndOldAlarmsKeepTheirBehavior() {
        val alarm = DeskAlarm(hour = 7, minute = 0, soundUri = "content://media/internal/audio/media/12",
            volumePercent = 65, fadeInSeconds = 45, vibrationEnabled = false, snoozeMinutes = 12,
            sunriseEnabled = true, sunriseDurationMinutes = 25, sunriseBrightnessPercent = 40,
            sunriseTone = SunriseTone.DAYLIGHT, sunriseDismissedUntilMillis = 42L)
        assertEquals(alarm, AlarmScheduler.fromJson(AlarmScheduler.toJson(listOf(alarm))).single())
        val old = AlarmScheduler.fromJson("""[{"hour":7,"minute":30,"soundEnabled":false}]""").single()
        assertFalse(old.soundEnabled)
        assertTrue(old.vibrationEnabled)
        assertEquals(5, old.snoozeMinutes)
        assertEquals(100, old.volumePercent)
        assertEquals(0, old.fadeInSeconds)
        assertEquals(100, old.sunriseBrightnessPercent)
        assertEquals(SunriseTone.WARM, old.sunriseTone)
    }

    @Test fun malformedSettingsAreBounded() {
        val alarm = AlarmScheduler.fromJson("""[{"volumePercent":999,"fadeInSeconds":-5,"snoozeMinutes":0,"sunriseBrightnessPercent":-20,"sunriseTone":"unknown"}]""").single()
        assertEquals(100, alarm.volumePercent)
        assertEquals(0, alarm.fadeInSeconds)
        assertEquals(1, alarm.snoozeMinutes)
        assertEquals(5, alarm.sunriseBrightnessPercent)
        assertEquals(SunriseTone.WARM, alarm.sunriseTone)
    }

    @Test fun fadeAndLightRespectIndividualLimits() {
        val alarm = DeskAlarm(hour = 7, minute = 0, volumePercent = 60, fadeInSeconds = 20,
            sunriseBrightnessPercent = 35)
        assertEquals(0.03f, AlarmScheduler.playbackVolume(alarm, 0), 0.001f)
        assertEquals(0.315f, AlarmScheduler.playbackVolume(alarm, 10_000), 0.001f)
        assertEquals(0.6f, AlarmScheduler.playbackVolume(alarm, 50_000), 0.001f)
        assertEquals(0.35f, AlarmScheduler.sunriseBrightness(1f, alarm), 0.001f)
        assertNotEquals(AlarmScheduler.sunriseOverlayColor(0.8f, SunriseTone.AMBER),
            AlarmScheduler.sunriseOverlayColor(0.8f, SunriseTone.DAYLIGHT))
    }

    @Test fun stoppingSunriseKeepsSoundScheduledAndOnlySuppressesThisOccurrence() {
        DeskAlarmManager.addOrUpdateAlarm(DeskAlarm(id = "light", hour = 7, minute = 0,
            daysOfWeek = (1..7).toSet(), sunriseEnabled = true, soundEnabled = false))
        val trigger = DeskAlarmManager.getNextAlarm()!!.second
        now = trigger - 60_000
        DeskAlarmManager.evaluateAlarms(now)
        assertTrue(DeskAlarmManager.isSunriseActive)
        DeskAlarmManager.dismissSunrise()
        assertFalse(DeskAlarmManager.isSunriseActive)
        assertEquals(trigger, DeskAlarmManager.getNextAlarm()!!.second)
        now = trigger
        DeskAlarmManager.deliver("light", trigger)
        assertNotNull(DeskAlarmManager.ringingAlarm)
        assertFalse(DeskAlarmManager.isSunriseActive)
        DeskAlarmManager.dismissActive()
        now = DeskAlarmManager.getNextAlarm()!!.second - 60_000
        DeskAlarmManager.evaluateAlarms(now)
        assertTrue(DeskAlarmManager.isSunriseActive)
    }

    @Test fun vibrationCanBeDisabledIndependently() {
        DeskAlarmManager.addOrUpdateAlarm(DeskAlarm(id = "silent", hour = 7, minute = 0,
            soundEnabled = false, vibrationEnabled = false))
        val trigger = DeskAlarmManager.getNextAlarm()!!.second
        now = trigger
        DeskAlarmManager.deliver("silent", trigger)
        assertNotNull(DeskAlarmManager.ringingAlarm)
        assertFalse(shadowOf(context.getSystemService(android.os.Vibrator::class.java)).isVibrating)
        DeskAlarmManager.dismissActive()
        DeskAlarmManager.addOrUpdateAlarm(DeskAlarm(id = "vibrate", hour = 8, minute = 0,
            soundEnabled = false, vibrationEnabled = true))
        val next = DeskAlarmManager.getNextAlarm()!!.second
        now = next
        DeskAlarmManager.deliver("vibrate", next)
        assertTrue(shadowOf(context.getSystemService(android.os.Vibrator::class.java)).isVibrating)
        DeskAlarmManager.dismissActive()
        assertFalse(shadowOf(context.getSystemService(android.os.Vibrator::class.java)).isVibrating)
    }

    @Test fun configuredSnoozeIsUsedAndEditingSettingsKeepsItsTime() {
        DeskAlarmManager.addOrUpdateAlarm(DeskAlarm(id = "snooze", hour = 7, minute = 0, snoozeMinutes = 12, soundEnabled = false))
        val trigger = DeskAlarmManager.getNextAlarm()!!.second
        now = trigger
        DeskAlarmManager.deliver("snooze", trigger)
        DeskAlarmManager.snoozeActive()
        val snoozed = DeskAlarmManager.getNextAlarm()!!
        assertEquals(trigger + 12 * 60_000, snoozed.second)
        DeskAlarmManager.addOrUpdateAlarm(snoozed.first.copy(volumePercent = 45))
        assertEquals(snoozed.second, DeskAlarmManager.getNextAlarm()!!.second)
        assertEquals(45, DeskAlarmManager.getNextAlarm()!!.first.volumePercent)
    }

    @Test fun skipCanBeUndoneButCannotOverwriteLaterChanges() {
        DeskAlarmManager.addOrUpdateAlarm(DeskAlarm(hour = 7, minute = 0))
        val before = DeskAlarmManager.getNextAlarm()!!.first
        DeskAlarmManager.skipNext(before.id)
        val skipped = DeskAlarmManager.getAlarms().single()
        DeskAlarmManager.restoreSkippedOccurrence(before, skipped)
        assertEquals(before, DeskAlarmManager.getNextAlarm()!!.first)
        DeskAlarmManager.skipNext(before.id)
        DeskAlarmManager.addOrUpdateAlarm(skipped.copy(label = "Changed"))
        DeskAlarmManager.restoreSkippedOccurrence(before, skipped)
        assertEquals("Changed", DeskAlarmManager.getAlarms().single().label)
        assertFalse(DeskAlarmManager.getAlarms().single().enabled)
    }

    @Test fun sunrisePreviewIsBoundedAndDoesNotChangeTheActivityBrightness() {
        val controller = Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        controller.setup()
        val brightness = activity.window.attributes.screenBrightness
        val panel = com.nxd1frnt.clockdesk2.desks.ui.AlarmSettingsPanel(activity,
            DeskAlarm(hour = 7, minute = 0, sunriseEnabled = true, sunriseBrightnessPercent = 35))
        try {
            panel.findViewWithTag<android.view.View>("alarm_preview_sunrise").performClick()
            val preview = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
            assertTrue(preview.window!!.attributes.screenBrightness in 0.05f..0.35f)
            assertEquals(brightness, activity.window.attributes.screenBrightness, 0.0001f)
            panel.dispose()
            assertFalse(preview.isShowing)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(12))
            assertFalse(preview.isShowing)
            assertEquals(brightness, activity.window.attributes.screenBrightness, 0.0001f)
        } finally { panel.dispose(); controller.pause().stop().destroy() }
    }

    @Test fun actualAlarmClosesTheEditorAndItsTimePicker() {
        DeskAlarmManager.addOrUpdateAlarm(DeskAlarm(id = "interrupt", hour = 7, minute = 0, soundEnabled = false))
        val controller = Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        controller.setup()
        try {
            val view = AlarmsDeskView(activity)
            activity.setContentView(view)
            shadowOf(Looper.getMainLooper()).idle()
            view.findViewById<android.view.View>(R.id.btn_alarm_edit_next).performClick()
            val editor = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            editor.findViewById<android.view.View>(android.R.id.content)!!.findViewWithTag<android.view.View>("alarm_time").performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val picker = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue(picker.isShowing)
            val trigger = DeskAlarmManager.getNextAlarm()!!.second
            now = trigger
            DeskAlarmManager.deliver("interrupt", trigger)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(editor.isShowing)
            assertFalse(picker.isShowing)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun editorSavesIndependentSettingsAndCancellationDoesNotChangeThem() {
        DeskAlarmManager.addOrUpdateAlarm(DeskAlarm(id = "edit", hour = 7, minute = 0, soundEnabled = false))
        val controller = Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        controller.setup()
        try {
            val view = AlarmsDeskView(activity)
            activity.setContentView(view)
            shadowOf(Looper.getMainLooper()).idle()
            view.findViewById<android.view.View>(R.id.btn_alarm_edit_next).performClick()
            var dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
            dialog.findViewById<android.view.View>(android.R.id.content)!!
                .findViewWithTag<Slider>("alarm_snooze_interval").value = 15f
            dialog.findViewById<android.view.View>(android.R.id.content)!!
                .findViewWithTag<MaterialSwitch>("alarm_vibration").isChecked = false
            val content = dialog.findViewById<android.view.View>(android.R.id.content)!!
            content.findViewWithTag<MaterialSwitch>("alarm_sound").isChecked = true
            content.findViewWithTag<Slider>("alarm_volume").value = 65f
            content.findViewWithTag<Slider>("alarm_fade").value = 30f
            content.findViewWithTag<MaterialSwitch>("alarm_sunrise").isChecked = true
            content.findViewWithTag<Slider>("alarm_sunrise_brightness").value = 30f
            content.findViewWithTag<Slider>("alarm_sunrise_duration").value = 25f
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val saved = DeskAlarmManager.getAlarms().single()
            assertTrue(saved.soundEnabled)
            assertEquals(65, saved.volumePercent)
            assertEquals(30, saved.fadeInSeconds)
            assertTrue(saved.sunriseEnabled)
            assertEquals(30, saved.sunriseBrightnessPercent)
            assertEquals(25, saved.sunriseDurationMinutes)
            assertEquals(15, DeskAlarmManager.getAlarms().single().snoozeMinutes)
            assertFalse(DeskAlarmManager.getAlarms().single().vibrationEnabled)
            shadowOf(Looper.getMainLooper()).idle()
            view.findViewById<android.view.View>(R.id.btn_alarm_edit_next).performClick()
            dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
            dialog.findViewById<android.view.View>(android.R.id.content)!!
                .findViewWithTag<Slider>("alarm_snooze_interval").value = 2f
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(15, DeskAlarmManager.getAlarms().single().snoozeMinutes)
        } finally { controller.pause().stop().destroy() }
    }
}
