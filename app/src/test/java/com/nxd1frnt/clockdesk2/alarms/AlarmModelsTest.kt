package com.nxd1frnt.clockdesk2.alarms

import android.app.Application
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AlarmModelsTest {

    private fun timeAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun oneShotAlarmSchedulesTodayOrTomorrow() {
        val alarm = DeskAlarm(id = "a1", hour = 7, minute = 30, enabled = true)
        val before = timeAt(2026, Calendar.NOVEMBER, 13, 7, 0)
        val after = timeAt(2026, Calendar.NOVEMBER, 13, 8, 0)

        assertEquals(timeAt(2026, Calendar.NOVEMBER, 13, 7, 30), AlarmScheduler.nextTriggerTimeMillis(alarm, before))
        assertEquals(timeAt(2026, Calendar.NOVEMBER, 13, 7, 30), AlarmScheduler.nextTriggerTimeMillis(alarm, timeAt(2026, Calendar.NOVEMBER, 13, 7, 30) + 500L))
        assertEquals(timeAt(2026, Calendar.NOVEMBER, 14, 7, 30), AlarmScheduler.nextTriggerTimeMillis(alarm, after))
    }

    @Test
    fun disabledAlarmReturnsNullTriggerAndZeroSunrise() {
        val alarm = DeskAlarm(id = "a2", hour = 7, minute = 0, enabled = false, sunriseEnabled = true, sunriseDurationMinutes = 15)
        val now = timeAt(2026, Calendar.NOVEMBER, 13, 6, 55)

        assertNull(AlarmScheduler.nextTriggerTimeMillis(alarm, now))
        assertEquals(0f, AlarmScheduler.sunriseProgressFor(alarm, now), 0.0001f)
    }

    @Test
    fun repeatingAlarmSkipsNonMatchingDays() {
        // 2026-11-13 is Friday
        val alarm = DeskAlarm(
            id = "a3",
            hour = 7,
            minute = 0,
            enabled = true,
            daysOfWeek = setOf(Calendar.MONDAY)
        )
        val fridayMorning = timeAt(2026, Calendar.NOVEMBER, 13, 6, 0)
        val expectedMonday = timeAt(2026, Calendar.NOVEMBER, 16, 7, 0)

        assertEquals(expectedMonday, AlarmScheduler.nextTriggerTimeMillis(alarm, fridayMorning))
    }

    @Test
    fun sunriseProgressRampsFromZeroToOne() {
        val alarm = DeskAlarm(
            id = "a4",
            hour = 7,
            minute = 0,
            enabled = true,
            sunriseEnabled = true,
            sunriseDurationMinutes = 20
        )

        val beforeWindow = timeAt(2026, Calendar.NOVEMBER, 13, 6, 30)
        val halfway = timeAt(2026, Calendar.NOVEMBER, 13, 6, 50)
        val atTrigger = timeAt(2026, Calendar.NOVEMBER, 13, 7, 0)

        assertEquals(0f, AlarmScheduler.sunriseProgressFor(alarm, beforeWindow), 0.0001f)
        assertEquals(0.5f, AlarmScheduler.sunriseProgressFor(alarm, halfway), 0.0001f)
        assertEquals(1.0f, AlarmScheduler.sunriseProgressFor(alarm, atTrigger), 0.0001f)

        assertEquals(Color.TRANSPARENT, AlarmScheduler.sunriseOverlayColor(0f))
        assertTrue(Color.alpha(AlarmScheduler.sunriseOverlayColor(0.5f)) > 0)
        assertTrue(Color.alpha(AlarmScheduler.sunriseOverlayColor(1f)) > Color.alpha(AlarmScheduler.sunriseOverlayColor(0.5f)))
    }

    @Test
    fun jsonRoundTripPreservesAllFields() {
        val original = listOf(
            DeskAlarm(
                id = "id-1",
                hour = 6,
                minute = 45,
                enabled = true,
                daysOfWeek = setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY),
                label = "Morning",
                sunriseEnabled = true,
                sunriseDurationMinutes = 20,
                soundEnabled = false,
                snoozeUntilMillis = 123456789L
            )
        )
        val restored = AlarmScheduler.fromJson(AlarmScheduler.toJson(original))
        assertEquals(1, restored.size)
        val item = restored.first()
        assertNotNull(item)
        assertEquals(original.first(), item)
    }

    @Test
    fun sunriseTransitionsIntoRingingAtAlarmTime() {
        val alarm = DeskAlarm(
            id = "sunrise-ring",
            hour = 7,
            minute = 30,
            enabled = true,
            sunriseEnabled = true,
            sunriseDurationMinutes = 10,
            soundEnabled = false
        )
        val trigger = timeAt(2026, Calendar.NOVEMBER, 13, 7, 30)
        DeskAlarmManager.timeSource = { trigger - 60_000L }
        DeskAlarmManager.addOrUpdateAlarm(alarm)
        try {
            DeskAlarmManager.evaluateAlarms(trigger - 500L)
            assertNull(DeskAlarmManager.ringingAlarm)
            assertTrue(DeskAlarmManager.sunriseProgress > 0.99f)

            DeskAlarmManager.evaluateAlarms(trigger + 500L)
            assertEquals("sunrise-ring", DeskAlarmManager.ringingAlarm?.id)
            assertEquals(1.0f, DeskAlarmManager.sunriseProgress, 0.0001f)
        } finally {
            DeskAlarmManager.dismissActive()
            DeskAlarmManager.deleteAlarm("sunrise-ring")
            DeskAlarmManager.timeSource = System::currentTimeMillis
        }
    }
}
