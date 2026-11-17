package com.nxd1frnt.clockdesk2.alarms

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DeskAlarmDeliveryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 13, 6, 0, 0) // Friday
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private var currentTime = now

    @Before fun prepare() {
        AlarmPresentation.setDeskVisible(false)
        DeskAlarmManager.timeSource = { currentTime }
        clearAlarms()
        // Robolectric replaces Application and system services between tests; a Kotlin object survives.
        org.robolectric.util.ReflectionHelpers.setStaticField(DeskAlarmManager::class.java, "appContext", null)
        context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        DeskAlarmManager.init(context)
    }

    @After fun cleanup() {
        clearAlarms()
        DeskAlarmManager.timeSource = System::currentTimeMillis
    }

    private fun clearAlarms() {
        DeskAlarmManager.getAlarms().forEach { DeskAlarmManager.deleteAlarm(it.id) }
    }

    private fun add(id: String = "alarm", days: Set<Int> = emptySet()): Pair<DeskAlarm, Long> {
        DeskAlarmManager.addOrUpdateAlarm(DeskAlarm(id = id, hour = 7, minute = 0, daysOfWeek = days, soundEnabled = false))
        return DeskAlarmManager.getNextAlarm()!!
    }

    @Test fun schedulesPersistedOccurrenceAndReregistersAfterReboot() {
        val (alarm, trigger) = add()
        val os = context.getSystemService(AlarmManager::class.java)
        assertEquals(trigger, os.nextAlarmClock.triggerTime)
        val saved = AlarmScheduler.fromJson(context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
            .getString("desk_alarms_json", null)).single()
        assertEquals(trigger, saved.scheduledTriggerMillis)
        SystemAlarmScheduler.cancel(context, alarm.id)
        assertNull(os.nextAlarmClock)
        DeskAlarmReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(trigger, os.nextAlarmClock.triggerTime)
        assertNull(DeskAlarmManager.ringingAlarm)
    }

    @Test fun delayedDeliveryRingsOnceAndDisablesOneShot() {
        val (alarm, trigger) = add()
        currentTime = trigger + 90_000L
        DeskAlarmManager.deliver(alarm.id, trigger)
        assertEquals(alarm.id, DeskAlarmManager.ringingAlarm?.id)
        assertFalse(DeskAlarmManager.getAlarms().single().enabled)
        DeskAlarmManager.dismissActive()
        DeskAlarmManager.deliver(alarm.id, trigger)
        assertNull(DeskAlarmManager.ringingAlarm)
        assertNull(DeskAlarmManager.getNextAlarm())
    }

    @Test fun deliveryForEditedOrDeletedAlarmIsIgnored() {
        val (alarm, trigger) = add()
        DeskAlarmManager.addOrUpdateAlarm(alarm.copy(hour = 8))
        currentTime = trigger
        DeskAlarmManager.deliver(alarm.id, trigger)
        assertNull(DeskAlarmManager.ringingAlarm)
        val updated = DeskAlarmManager.getNextAlarm()!!
        DeskAlarmManager.deleteAlarm(alarm.id)
        DeskAlarmManager.deliver(alarm.id, updated.second, updated.second)
        assertNull(DeskAlarmManager.ringingAlarm)
    }

    @Test fun skipWeeklyOccurrenceCanScheduleBeyondSevenDays() {
        val days = setOf(Calendar.THURSDAY)
        val (alarm, trigger) = add(days = days)
        DeskAlarmManager.skipNext(alarm.id)
        val next = DeskAlarmManager.getNextAlarm()!!
        assertEquals(trigger + 7 * 24 * 60 * 60_000L, next.second)
        assertEquals(days, next.first.daysOfWeek)
        assertTrue(next.first.enabled)
    }

    @Test fun skippingOneShotDisablesIt() {
        val (alarm, _) = add()
        DeskAlarmManager.skipNext(alarm.id)
        assertNull(DeskAlarmManager.getNextAlarm())
        assertNull(context.getSystemService(AlarmManager::class.java).nextAlarmClock)
    }

    @Test fun snoozeUsesActualTimeAndCancellationPreservesRecurrence() {
        val days = (Calendar.SUNDAY..Calendar.SATURDAY).toSet()
        val (alarm, trigger) = add(days = days)
        currentTime = trigger
        DeskAlarmManager.deliver(alarm.id, trigger)
        DeskAlarmManager.snoozeActive(5)
        val snoozed = DeskAlarmManager.getNextAlarm()!!
        assertEquals(trigger + 5 * 60_000L, snoozed.second)
        assertEquals(snoozed.second, snoozed.first.snoozeUntilMillis)
        assertEquals(snoozed.second, context.getSystemService(AlarmManager::class.java).nextAlarmClock.triggerTime)
        DeskAlarmManager.skipNext(alarm.id)
        val next = DeskAlarmManager.getNextAlarm()!!
        assertNull(next.first.snoozeUntilMillis)
        assertEquals(trigger + 24 * 60 * 60_000L, next.second)
        assertEquals(days, next.first.daysOfWeek)
    }

    @Test fun overlappingAlarmsAreDeliveredAfterDismissal() {
        val (first, trigger) = add("first")
        add("second")
        currentTime = trigger
        DeskAlarmManager.deliver(first.id, trigger)
        DeskAlarmManager.deliver("second", trigger)
        assertEquals("first", DeskAlarmManager.ringingAlarm?.id)
        DeskAlarmManager.dismissActive()
        assertEquals("second", DeskAlarmManager.ringingAlarm?.id)
    }

    @Test fun staleOccurrenceDoesNotRingHoursLater() {
        val (_, trigger) = add()
        currentTime = trigger + DeskAlarmManager.MAX_LATENESS_MS + 1
        DeskAlarmManager.refreshSchedule()
        assertNull(DeskAlarmManager.ringingAlarm)
        assertNull(DeskAlarmManager.getNextAlarm())
    }

    @Test
    @Config(sdk = [31])
    fun exactAccessGrantRegistersPreviouslySavedAlarm() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val (_, trigger) = add()
        val os = context.getSystemService(AlarmManager::class.java)
        assertNull(os.nextAlarmClock)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        DeskAlarmReceiver().onReceive(context, Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
        assertEquals(trigger, os.nextAlarmClock.triggerTime)
    }

    @Test fun ringingServiceExposesDismissActionAndStopsPlayback() {
        val (alarm, trigger) = add()
        currentTime = trigger
        DeskAlarmManager.deliver(alarm.id, trigger)
        val controller = org.robolectric.Robolectric.buildService(DeskAlarmService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(Intent(), 0, 1)
            val notification = org.robolectric.Shadows.shadowOf(service).lastForegroundNotification
            assertEquals(android.app.Notification.CATEGORY_ALARM, notification.category)
            assertNotNull(notification.fullScreenIntent)
            val dismiss = org.robolectric.Shadows.shadowOf(notification.actions[1].actionIntent).savedIntent
            service.onStartCommand(dismiss, 0, 2)
            assertNull(DeskAlarmManager.ringingAlarm)
            assertTrue(org.robolectric.Shadows.shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
        }
    }

    @Test fun visibleDeskUsesQuietServiceNotificationAndTransitionsWithoutAnotherPopup() {
        val (alarm, trigger) = add()
        currentTime = trigger
        AlarmPresentation.setDeskVisible(true)
        DeskAlarmManager.deliver(alarm.id, trigger)
        val controller = org.robolectric.Robolectric.buildService(DeskAlarmService::class.java).create()
        val service = controller.get()
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        try {
            service.onStartCommand(Intent(), 0, 1)
            fun notification() = org.robolectric.Shadows.shadowOf(service).lastForegroundNotification
            assertEquals("desk_alarm_playback", notification().channelId)
            assertEquals(android.app.NotificationManager.IMPORTANCE_LOW,
                manager.getNotificationChannel(notification().channelId).importance)
            assertNull(notification().fullScreenIntent)
            assertEquals(android.app.Notification.PRIORITY_LOW, notification().priority)
            assertTrue(notification().flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE != 0)

            AlarmPresentation.setDeskVisible(false)
            assertEquals("desk_alarm_ringing", notification().channelId)
            assertNull(notification().fullScreenIntent) // Leaving the app must not reopen it.
            assertEquals(alarm.id, DeskAlarmManager.ringingAlarm?.id)
            assertEquals(1, manager.activeNotifications.size)

            AlarmPresentation.setDeskVisible(true)
            assertEquals("desk_alarm_playback", notification().channelId)
            assertNull(notification().fullScreenIntent)
            assertEquals(1, manager.activeNotifications.size)
        } finally {
            controller.destroy()
            AlarmPresentation.setDeskVisible(false)
        }
    }

    @Test fun returningToDeskSilencesTheExistingBackgroundAlarmNotification() {
        val (alarm, trigger) = add()
        currentTime = trigger
        DeskAlarmManager.deliver(alarm.id, trigger)
        val controller = org.robolectric.Robolectric.buildService(DeskAlarmService::class.java).create()
        val service = controller.get()
        try {
            service.onStartCommand(Intent(), 0, 1)
            assertNotNull(org.robolectric.Shadows.shadowOf(service).lastForegroundNotification.fullScreenIntent)
            AlarmPresentation.setDeskVisible(true)
            val quiet = org.robolectric.Shadows.shadowOf(service).lastForegroundNotification
            assertEquals("desk_alarm_playback", quiet.channelId)
            assertNull(quiet.fullScreenIntent)
            assertEquals(alarm.id, DeskAlarmManager.ringingAlarm?.id)
        } finally {
            controller.destroy()
            AlarmPresentation.setDeskVisible(false)
        }
    }

    @Test fun summaryShowsActualSnoozeTimeAndCanCancelIt() {
        val (alarm, trigger) = add()
        currentTime = trigger
        DeskAlarmManager.deliver(alarm.id, trigger)
        DeskAlarmManager.snoozeActive(5)
        val activityController = org.robolectric.Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java)
        val activity = activityController.get()
        activity.setTheme(com.nxd1frnt.clockdesk2.R.style.Theme_ClockDesk)
        activityController.setup()
        try {
            val view = com.nxd1frnt.clockdesk2.desks.ui.AlarmsDeskView(activity)
            activity.setContentView(view)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val time = view.findViewById<android.widget.TextView>(com.nxd1frnt.clockdesk2.R.id.txt_alarm_next_time)
            assertEquals(android.text.format.DateFormat.getTimeFormat(activity).format(java.util.Date(trigger + 5 * 60_000L)), time.text.toString())
            view.findViewById<android.view.View>(com.nxd1frnt.clockdesk2.R.id.btn_alarm_skip_next).performClick()
            assertNull(DeskAlarmManager.getNextAlarm())
        } finally {
            activityController.pause().stop().destroy()
        }
    }

    @Test fun timezoneChangeRecomputesWallClockButKeepsSnoozeInstant() {
        val original = TimeZone.getDefault()
        try {
            val (alarm, _) = add()
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+02:00"))
            DeskAlarmReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
            val next = DeskAlarmManager.getNextAlarm()!!
            assertEquals(7, Calendar.getInstance().apply { timeInMillis = next.second }.get(Calendar.HOUR_OF_DAY))
            currentTime = next.second
            DeskAlarmManager.deliver(alarm.id, next.second)
            DeskAlarmManager.snoozeActive(5)
            val snooze = DeskAlarmManager.getNextAlarm()!!.second
            TimeZone.setDefault(TimeZone.getTimeZone("GMT-05:00"))
            DeskAlarmReceiver().onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
            assertEquals(snooze, DeskAlarmManager.getNextAlarm()!!.second)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
