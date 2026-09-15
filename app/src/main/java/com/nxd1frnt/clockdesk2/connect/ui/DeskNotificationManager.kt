package com.nxd1frnt.clockdesk2.connect.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import java.util.LinkedList

class DeskNotificationManager(
    private val context: Context,
    private val container: ViewGroup,
    private val deskConnectManager: DeskConnectManager
) {
    data class NotificationItem(
        val deviceId: String,
        val notificationId: String,
        val appName: String,
        val title: String,
        val text: String,
        val iconBytes: ByteArray?,
        val timestamp: Long,
        val isClearable: Boolean
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = LinkedList<NotificationItem>()
    private var currentView: View? = null
    private var currentItem: NotificationItem? = null
    private var dismissRunnable: Runnable? = null

    private val notificationListener = object : DeskConnectManager.NotificationListener {
        override fun onNotificationReceived(
            deviceId: String,
            notificationId: String,
            appName: String,
            title: String,
            text: String,
            timestamp: Long,
            iconBytes: ByteArray?,
            isClearable: Boolean
        ) {
            val item = NotificationItem(
                deviceId = deviceId,
                notificationId = notificationId,
                appName = appName,
                title = title,
                text = text,
                iconBytes = iconBytes,
                timestamp = timestamp,
                isClearable = isClearable
            )
            enqueueNotification(item)
        }

        override fun onNotificationDismissed(deviceId: String, notificationId: String) {
            if (currentItem?.deviceId == deviceId && currentItem?.notificationId == notificationId) {
                dismissCurrent(notifyManager = false)
            } else {
                queue.removeAll { it.deviceId == deviceId && it.notificationId == notificationId }
            }
        }
    }

    init {
        deskConnectManager.notificationListeners.add(notificationListener)
    }

    private fun enqueueNotification(item: NotificationItem) {
        // Replace existing notification in queue if same id
        queue.removeAll { it.deviceId == item.deviceId && it.notificationId == item.notificationId }
        queue.add(item)

        if (currentView == null) {
            showNext()
        }
    }

    private fun showNext() {
        if (queue.isEmpty()) return

        val item = queue.poll() ?: return
        currentItem = item

        val view = LayoutInflater.from(context).inflate(R.layout.view_ambient_notification, container, false)
        currentView = view

        val iconView = view.findViewById<ImageView>(R.id.notification_app_icon)
        val appNameView = view.findViewById<TextView>(R.id.notification_app_name)
        val titleView = view.findViewById<TextView>(R.id.notification_title)
        val bodyView = view.findViewById<TextView>(R.id.notification_body)
        val dismissBtn = view.findViewById<ImageButton>(R.id.btn_dismiss_notification)

        val dev = deskConnectManager.discoveredDevices[item.deviceId]
        val deviceName = dev?.getDisplayName()

        appNameView.text = if (!deviceName.isNullOrEmpty()) {
            "${item.appName.uppercase()} • $deviceName"
        } else {
            item.appName.uppercase()
        }
        titleView.text = item.title
        bodyView.text = item.text

        if (item.iconBytes != null && item.iconBytes.isNotEmpty()) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(item.iconBytes, 0, item.iconBytes.size)
                if (bitmap != null) {
                    iconView.setImageBitmap(bitmap)
                    iconView.imageTintList = null
                } else {
                    iconView.setImageResource(R.drawable.ic_widgets_outline)
                }
            } catch (e: Exception) {
                iconView.setImageResource(R.drawable.ic_widgets_outline)
            }
        } else {
            iconView.setImageResource(R.drawable.ic_widgets_outline)
        }

        dismissBtn.visibility = if (item.isClearable) View.VISIBLE else View.GONE
        dismissBtn.setOnClickListener {
            dismissCurrent(notifyManager = true)
        }

        // Animate entrance: slide down from top + fade in
        view.translationY = -120f
        view.alpha = 0f
        container.addView(view)

        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()

        // Auto dismiss after 6 seconds
        dismissRunnable = Runnable {
            dismissCurrent(notifyManager = false)
        }
        mainHandler.postDelayed(dismissRunnable!!, 6000)
    }

    private fun dismissCurrent(notifyManager: Boolean) {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null

        val viewToDismiss = currentView ?: return
        val itemToDismiss = currentItem

        if (notifyManager && itemToDismiss != null) {
            deskConnectManager.dismissNotification(itemToDismiss.deviceId, itemToDismiss.notificationId)
        }

        currentView = null
        currentItem = null

        viewToDismiss.animate()
            .translationY(-100f)
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    container.removeView(viewToDismiss)
                    showNext()
                }
            })
            .start()
    }

    fun destroy() {
        deskConnectManager.notificationListeners.remove(notificationListener)
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        currentView?.let { container.removeView(it) }
        currentView = null
        currentItem = null
        queue.clear()
    }
}
