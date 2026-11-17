package com.nxd1frnt.clockdesk2.notifications

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.alarms.DeskAlarm
import com.nxd1frnt.clockdesk2.alarms.DeskAlarmManager
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.AppAlertType
import com.nxd1frnt.clockdesk2.connect.model.DeskNotification
import com.nxd1frnt.clockdesk2.connect.repo.DeskNotificationRepository
import com.nxd1frnt.clockdesk2.connect.ui.DeskNotificationManager
import com.nxd1frnt.clockdesk2.notifications.ui.AlarmNotificationActions
import com.nxd1frnt.clockdesk2.notifications.ui.NotificationAdapter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AlarmNotificationTest {
    private lateinit var controller: ActivityController<AppCompatActivity>
    private lateinit var activity: AppCompatActivity
    private lateinit var repository: DeskNotificationRepository
    private lateinit var alerts: AppNotificationManager
    private lateinit var container: FrameLayout
    private lateinit var banners: DeskNotificationManager
    private var now = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 13, 6, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun idle(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Before fun setup() {
        controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        PreferenceManager.getDefaultSharedPreferences(activity).edit().putBoolean("deskconnect_enabled", false).commit()
        controller.setup()
        DeskAlarmManager.getAlarms().forEach { DeskAlarmManager.deleteAlarm(it.id) }
        ReflectionHelpers.setStaticField(DeskAlarmManager::class.java, "appContext", null)
        activity.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        DeskAlarmManager.timeSource = { now }
        DeskAlarmManager.init(activity)
        repository = DeskNotificationRepository.getInstance(activity)
        repository.getNotifications().forEach(repository::resolveNotification)
        alerts = AppNotificationManager(activity)
        container = FrameLayout(activity)
        activity.setContentView(container)
        banners = DeskNotificationManager(activity, container, DeskConnectManager.getInstance(activity))
    }

    @After fun cleanup() {
        banners.destroy()
        DeskAlarmManager.getAlarms().forEach { DeskAlarmManager.deleteAlarm(it.id) }
        alerts.close()
        repository.getNotifications().forEach(repository::resolveNotification)
        DeskAlarmManager.timeSource = System::currentTimeMillis
        controller.pause().stop().destroy()
        idle()
    }

    private fun ring(): DeskNotification {
        DeskAlarmManager.addOrUpdateAlarm(DeskAlarm(id = "morning", hour = 7, minute = 0,
            label = "Morning", snoozeMinutes = 9, soundEnabled = false, vibrationEnabled = false))
        val trigger = DeskAlarmManager.getNextAlarm()!!.second
        now = trigger
        DeskAlarmManager.deliver("morning", trigger)
        idle(300)
        return repository.getNotifications().single { it.alertType == AppAlertType.ALARM_RINGING }
    }

    @Test fun alarmBannerStaysUntilAnExplicitActionAndSurvivesRecreation() {
        ring()
        assertEquals(1, container.childCount)
        idle(70_000)
        assertEquals(1, container.childCount)
        assertNotNull(DeskAlarmManager.ringingAlarm)
        banners.destroy()
        banners = DeskNotificationManager(activity, container, DeskConnectManager.getInstance(activity))
        assertEquals(1, container.childCount)
        repository.clearAllNotifications()
        assertNotNull(DeskAlarmManager.ringingAlarm)
        val snooze = container.findViewWithTag<View>("alarm_notification_snooze")
        assertNotNull(snooze)
        snooze.performClick()
        idle(300)
        assertNull(DeskAlarmManager.ringingAlarm)
        assertEquals(now + 9 * 60_000L, DeskAlarmManager.getNextAlarm()!!.second)
        assertTrue(repository.getNotifications().isEmpty())
        assertEquals(0, container.childCount)
    }

    @Test fun alarmControlsRemainVisibleWhenRoutineBannersAreDisabled() {
        activity.getSharedPreferences("ClockDeskPrefs", 0).edit().putBoolean("desk_notification_popup_enabled", false).commit()
        ring()
        assertEquals(View.VISIBLE, container.findViewWithTag<View>("alarm_notification_dismiss").visibility)
        container.findViewWithTag<View>("alarm_notification_dismiss").performClick()
        idle(300)
        assertNull(DeskAlarmManager.ringingAlarm)
        assertTrue(repository.getNotifications().isEmpty())
    }

    @Test fun notificationListProvidesTheSameAlarmActions() {
        val item = ring()
        val adapter = NotificationAdapter(activity, DeskConnectManager.getInstance(activity)) { repository.dismissNotification(it) }
        adapter.submitList(listOf(item))
        val holder = adapter.onCreateViewHolder(container, 0)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(View.GONE, holder.itemView.findViewById<View>(R.id.btn_dismiss_shade_item).visibility)
        holder.itemView.findViewWithTag<View>("alarm_notification_dismiss").performClick()
        idle(300)
        assertNull(DeskAlarmManager.ringingAlarm)
        assertTrue(repository.getNotifications().isEmpty())
    }

    @Test fun staleNotificationActionsCannotAffectASnoozedOccurrence() {
        val old = ring()
        AlarmNotificationActions.snooze(old)
        val trigger = DeskAlarmManager.getNextAlarm()!!.second
        now = trigger
        DeskAlarmManager.deliver("morning", trigger)
        AlarmNotificationActions.dismiss(old)
        AlarmNotificationActions.snooze(old)
        assertEquals(trigger, DeskAlarmManager.ringingAlarm?.scheduledTriggerMillis)
        assertEquals(1, repository.getNotifications().size)
    }

    @Test fun alarmPreemptsRoutineBannerAndResolutionDoesNotStopOtherNotifications() {
        repository.postNotification(DeskNotification("remote", "message", "Chat", "Hi", "Hello", now, null, true))
        ring()
        idle(500)
        assertNotNull(container.findViewWithTag<View>("alarm_notification_dismiss"))
        container.findViewWithTag<View>("alarm_notification_dismiss").performClick()
        idle(300)
        assertEquals("message", repository.getNotifications().single().notificationId)
    }
}
