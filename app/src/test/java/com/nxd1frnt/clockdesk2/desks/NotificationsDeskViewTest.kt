package com.nxd1frnt.clockdesk2.desks

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.model.DeskNotification
import com.nxd1frnt.clockdesk2.connect.repo.DeskNotificationRepository
import com.nxd1frnt.clockdesk2.desks.ui.NotificationsDeskView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class NotificationsDeskViewTest {
    @Test fun liveUpdatesAndClearAllPreserveOngoingNotifications() {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("deskconnect_enabled", false).commit()
        val controller = Robolectric.buildActivity(Activity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        controller.setup()
        val repository = DeskNotificationRepository.getInstance(context)
        val desk = NotificationsDeskView(activity)
        activity.setContentView(desk)
        controller.visible()
        val recycler = desk.findViewById<RecyclerView>(R.id.shade_recycler_view)
        val empty = desk.findViewById<View>(R.id.shade_empty_state)
        val clear = desk.findViewById<MaterialButton>(R.id.btn_clear_all_notifications)
        try {
            assertEquals(View.VISIBLE, empty.visibility)
            assertFalse(clear.isEnabled)
            repository.postNotification(notification("message", true))
            repository.postNotification(notification("ongoing", false))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(2, recycler.adapter!!.itemCount)
            assertEquals("2", desk.findViewById<TextView>(R.id.shade_count_badge).text.toString())
            assertEquals(View.GONE, empty.visibility)
            assertTrue(clear.isEnabled)

            clear.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("ongoing"), repository.getNotifications().map { it.notificationId })
            assertEquals(1, recycler.adapter!!.itemCount)
            assertFalse(clear.isEnabled)

            repository.dismissNotification("test-device", "ongoing")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(View.VISIBLE, empty.visibility)

            activity.setContentView(View(activity))
            repository.postNotification(notification("detached", true))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, recycler.adapter!!.itemCount)
            activity.setContentView(desk)
            assertEquals(1, recycler.adapter!!.itemCount)
        } finally {
            controller.pause().stop().destroy()
            repository.getNotifications().forEach {
                repository.dismissNotification(it.deviceId, it.notificationId)
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun notification(id: String, clearable: Boolean) = DeskNotification(
        "test-device", id, "Messages", "Hello", "Notification body", 1L, null, clearable
    )
}
