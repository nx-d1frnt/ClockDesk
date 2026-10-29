package com.nxd1frnt.clockdesk2.notifications

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.AppAlertType
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.connect.model.DeskNotification
import com.nxd1frnt.clockdesk2.connect.repo.DeskNotificationRepository
import com.nxd1frnt.clockdesk2.connect.ui.DeskNotificationManager
import com.nxd1frnt.clockdesk2.timer.TimerManager
import com.nxd1frnt.clockdesk2.timer.TimerState
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AppNotificationManagerTest {
    private lateinit var repository: DeskNotificationRepository
    private lateinit var connect: DeskConnectManager
    private lateinit var alerts: AppNotificationManager
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun idle(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Before fun setup() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("deskconnect_enabled", false).commit()
        connect = DeskConnectManager.getInstance(context)
        repository = DeskNotificationRepository.getInstance(context)
        TimerManager.reset()
        repository.getNotifications().forEach(repository::resolveNotification)
        connect.discoveredDevices.clear()
        context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean(AppNotificationManager.TIMER_BANNER, true)
            .putBoolean(AppNotificationManager.BATTERY_BANNER, true)
            .putBoolean(AppNotificationManager.BATTERY_ENABLED, false).commit()
        idle()
        context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean(AppNotificationManager.BATTERY_ENABLED, true).commit()
        idle()
        alerts = AppNotificationManager(context)
    }

    @After fun cleanup() {
        TimerManager.reset()
        repository.getNotifications().forEach(repository::resolveNotification)
        connect.discoveredDevices.clear()
        alerts.close()
        idle()
    }

    @Test fun timerIsInSharedListAndAcknowledgementResetsIt() {
        TimerManager.start(100)
        idle(100)
        val item = repository.getNotifications().single()
        assertEquals(AppAlertType.TIMER_FINISHED, item.alertType)
        repository.dismissNotification(item)
        assertEquals(TimerState.IDLE, TimerManager.state)
        assertTrue(repository.getNotifications().isEmpty())
    }

    @Test fun bannerTimeoutAndActivityRecreationDoNotAcknowledgeTimer() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        controller.setup()
        val container = FrameLayout(activity)
        activity.setContentView(container)
        var banners = DeskNotificationManager(activity, container, connect)
        try {
            TimerManager.start(100)
            idle(100)
            assertEquals(1, container.childCount)
            idle(66_000)
            assertEquals(0, container.childCount)
            assertEquals(TimerState.FINISHED, TimerManager.state)
            assertEquals(1, repository.getNotifications().size)
            banners.destroy()
            banners = DeskNotificationManager(activity, container, connect)
            assertEquals(1, container.childCount)
            repository.clearAllNotifications()
            idle(300)
            assertEquals(TimerState.IDLE, TimerManager.state)
            assertEquals(0, container.childCount)
        } finally {
            banners.destroy()
            TimerManager.reset()
            controller.pause().stop().destroy()
        }
    }

    @Test fun batteryUpdatesStayLocalAndDismissalSuppressesUntilCharging() {
        val device = DeskConnectDevice("phone", "My phone", isPaired = true, isConnected = true)
        connect.discoveredDevices[device.deviceId] = device
        var remoteDismissals = 0
        val remoteListener = object : DeskConnectManager.NotificationListener {
            override fun onNotificationReceived(deviceId: String, notificationId: String, appName: String,
                title: String, text: String, timestamp: Long, iconBytes: ByteArray?, isClearable: Boolean) {}
            override fun onNotificationDismissed(deviceId: String, notificationId: String) { remoteDismissals++ }
        }
        connect.notificationListeners.add(remoteListener)
        val popups = mutableListOf<DeskNotification>()
        repository.addEventListener(object : DeskNotificationRepository.EventListener {
            override fun onPosted(item: DeskNotification, alert: Boolean) { if (alert) popups.add(item) }
        })
        battery("phone", 20)
        battery("phone", 19)
        assertEquals(1, repository.getNotifications().size)
        assertTrue(repository.getNotifications().single().text.contains("19%"))
        assertEquals(1, popups.size)
        repository.dismissNotification(repository.getNotifications().single())
        idle()
        assertEquals(0, remoteDismissals)
        battery("phone", 18)
        assertTrue(repository.getNotifications().isEmpty())
        battery("phone", 18, charging = true)
        battery("phone", 17)
        assertEquals(1, repository.getNotifications().size)
        assertEquals(2, popups.size)
        battery("phone", 25)
        assertTrue(repository.getNotifications().isEmpty())
    }

    @Test fun disconnectClearsWarningAndReconnectDoesNotRepeatSameEpisode() {
        val device = DeskConnectDevice("phone", "Phone", isPaired = true, isConnected = true)
        connect.discoveredDevices[device.deviceId] = device
        battery("phone", 10)
        assertEquals(1, repository.getNotifications().size)
        device.isConnected = false
        connect.deviceListeners.forEach { it.onDeviceDisconnected(device) }
        assertTrue(repository.getNotifications().isEmpty())
        device.isConnected = true
        connect.deviceListeners.forEach { it.onDeviceConnected(device) }
        battery("phone", 9)
        assertTrue(repository.getNotifications().isEmpty())
    }

    @Test fun batteryBannersAreNotReplayedOnActivityRecreation() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        controller.setup()
        val container = FrameLayout(activity)
        activity.setContentView(container)
        var banners = DeskNotificationManager(activity, container, connect)
        try {
            connect.discoveredDevices["phone"] = DeskConnectDevice("phone", "Phone", isPaired = true, isConnected = true)
            battery("phone", 10)
            assertEquals(1, container.childCount)
            banners.destroy()
            banners = DeskNotificationManager(activity, container, connect)
            assertEquals(0, container.childCount)
            assertEquals(1, repository.getNotifications().size)
        } finally {
            banners.destroy()
            controller.pause().stop().destroy()
        }
    }

    @Test fun batteryMonitoringAndTimerListWorkWithBannersDisabled() {
        context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean(AppNotificationManager.TIMER_BANNER, false)
            .putBoolean(AppNotificationManager.BATTERY_BANNER, false).commit()
        connect.discoveredDevices["phone"] = DeskConnectDevice("phone", "Phone", isPaired = true, isConnected = true)
        battery("phone", 0)
        TimerManager.start(100)
        idle(100)
        assertEquals(2, repository.getNotifications().size)
        assertEquals(AppAlertType.TIMER_FINISHED, repository.getNotifications().first().alertType)
        context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean(AppNotificationManager.BATTERY_ENABLED, false).commit()
        idle()
        assertEquals(listOf(AppAlertType.TIMER_FINISHED), repository.getNotifications().map { it.alertType })
    }

    @Test fun remoteDismissalsStillPropagateAndClearAllPreservesOngoingItems() {
        var dismissals = 0
        connect.notificationListeners.add(object : DeskConnectManager.NotificationListener {
            override fun onNotificationReceived(deviceId: String, notificationId: String, appName: String,
                title: String, text: String, timestamp: Long, iconBytes: ByteArray?, isClearable: Boolean) {}
            override fun onNotificationDismissed(deviceId: String, notificationId: String) { dismissals++ }
        })
        repository.postNotification(DeskNotification("phone", "message", "Messages", "Hello", "Hi", 1L, null, true))
        repository.postNotification(DeskNotification("phone", "ongoing", "Music", "Playing", "Track", 1L, null, false))
        TimerManager.start(100)
        idle(100)
        repository.clearAllNotifications()
        idle()
        assertEquals(1, dismissals)
        assertEquals(TimerState.IDLE, TimerManager.state)
        assertEquals(listOf("ongoing"), repository.getNotifications().map { it.notificationId })
        repository.dismissNotification(repository.getNotifications().single())
        assertEquals(1, repository.getNotifications().size)
    }

    private fun battery(deviceId: String, charge: Int, charging: Boolean = false) {
        connect.discoveredDevices[deviceId]?.apply { batteryLevel = charge; isCharging = charging }
        connect.batteryListeners.forEach { it.onBatteryUpdated(deviceId, charge, charging, 0) }
    }
}
