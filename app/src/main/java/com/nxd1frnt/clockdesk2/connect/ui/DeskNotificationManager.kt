package com.nxd1frnt.clockdesk2.connect.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PorterDuff
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice
import com.nxd1frnt.clockdesk2.utils.Logger
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.sqrt

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
    private var safetyWatchdogRunnable: Runnable? = null
    private var isDismissing = false

    private val deviceListener = object : DeskConnectManager.DeviceListener {
        override fun onDeviceDiscovered(device: DeskConnectDevice) {}
        override fun onDeviceConnected(device: DeskConnectDevice) {}
        override fun onDeviceDisconnected(device: DeskConnectDevice) {
            mainHandler.post {
                queue.removeAll { it.deviceId == device.deviceId }
                if (currentItem?.deviceId == device.deviceId) {
                    dismissCurrent(notifyManager = false)
                }
            }
        }
        override fun onPairingRequested(device: DeskConnectDevice, verificationKey: String) {}
        override fun onPairingStateChanged(device: DeskConnectDevice, isPaired: Boolean) {
            if (!isPaired) {
                mainHandler.post {
                    queue.removeAll { it.deviceId == device.deviceId }
                    if (currentItem?.deviceId == device.deviceId) {
                        dismissCurrent(notifyManager = false)
                    }
                }
            }
        }
    }

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
        deskConnectManager.deviceListeners.add(deviceListener)
    }

    private fun getPrefs() = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

    private fun enqueueNotification(item: NotificationItem) {
        if (!getPrefs().getBoolean("desk_notification_popup_enabled", true)) {
            return
        }

        // If this notification is currently displayed, update it in-place instead of queueing duplicate
        if (currentItem?.deviceId == item.deviceId && currentItem?.notificationId == item.notificationId) {
            currentItem = item
            currentView?.let { view ->
                updateViewContent(view, item)
                // Refresh auto dismiss timer
                val baseDurationSec = getPrefs().getInt("desk_notification_duration", 5)
                val defaultDisplayMs = (baseDurationSec * 1000L).coerceAtLeast(2000L)
                scheduleAutoDismiss(if (queue.isNotEmpty()) 3000L else defaultDisplayMs)
            }
            return
        }

        // Replace existing notification in queue if same id
        queue.removeAll { it.deviceId == item.deviceId && it.notificationId == item.notificationId }

        // Cap queue to 3 notifications max to prevent backlog
        while (queue.size >= 3) {
            queue.poll()
        }
        queue.add(item)

        if (currentView == null && !isDismissing) {
            showNext()
        }
    }

    private fun updateViewContent(view: View, item: NotificationItem) {
        val appNameView = view.findViewById<TextView>(R.id.notification_app_name)
        val titleView = view.findViewById<TextView>(R.id.notification_title)
        val bodyView = view.findViewById<TextView>(R.id.notification_body)
        val iconView = view.findViewById<ImageView>(R.id.notification_app_icon)

        val dev = deskConnectManager.discoveredDevices[item.deviceId]
        val deviceName = dev?.getDisplayName()

        appNameView.text = if (!deviceName.isNullOrEmpty()) {
            "${item.appName.uppercase()} • $deviceName"
        } else {
            item.appName.uppercase()
        }

        val hideSensitive = getPrefs().getBoolean("desk_notification_hide_sensitive", false)
        if (hideSensitive) {
            titleView.text = item.appName
            bodyView.text = context.getString(R.string.notification_hidden_content)
        } else {
            titleView.text = item.title
            bodyView.text = item.text
        }

        applyIcon(iconView, item)
    }

    private fun applyIcon(iconView: ImageView, item: NotificationItem) {
        DeskNotificationHelper.applyIcon(context, iconView, item.iconBytes, item.notificationId)
    }

    private fun showNext() {
        if (isDismissing) return

        if (queue.isEmpty()) {
            currentView = null
            currentItem = null
            container.removeAllViews()
            return
        }

        val item = queue.poll() ?: return
        currentItem = item

        // Clean up any residual views in container before adding new notification
        container.removeAllViews()

        val view = LayoutInflater.from(context).inflate(R.layout.view_ambient_notification, container, false)
        currentView = view

        val iconView = view.findViewById<ImageView>(R.id.notification_app_icon)
        val dismissBtn = view.findViewById<ImageButton>(R.id.btn_dismiss_notification)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            iconView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) {
                    val radius = v.context.resources.displayMetrics.density * 8f
                    outline.setRoundRect(0, 0, v.width, v.height, radius)
                }
            }
            iconView.clipToOutline = true
        }

        updateViewContent(view, item)

        dismissBtn.visibility = if (item.isClearable) View.VISIBLE else View.GONE
        dismissBtn.setOnClickListener {
            dismissCurrent(notifyManager = true)
        }

        setupSwipeToDismiss(view)

        // Animate entrance: slide down from top + fade in
        view.translationX = 0f
        view.translationY = -120f
        view.alpha = 0f
        container.addView(view)

        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()

        // Auto dismiss based on user preference: duration if single, 3 seconds if backlog exists
        val baseDurationSec = getPrefs().getInt("desk_notification_duration", 5)
        val defaultDisplayMs = (baseDurationSec * 1000L).coerceAtLeast(2000L)
        val displayTimeMs = if (queue.isNotEmpty()) 3000L else defaultDisplayMs
        scheduleAutoDismiss(displayTimeMs)

        // Absolute safety watchdog: force-dismiss after timeout no matter what
        safetyWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        safetyWatchdogRunnable = Runnable {
            if (currentView != null) {
                Logger.w("DeskNotificationManager") { "Safety watchdog fired for stuck notification, force clearing" }
                dismissCurrent(notifyManager = false)
            }
        }
        val watchdogDelayMs = (displayTimeMs + 5000L).coerceAtLeast(10000L)
        mainHandler.postDelayed(safetyWatchdogRunnable!!, watchdogDelayMs)
    }

    private fun scheduleAutoDismiss(delayMs: Long = 5000L) {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = Runnable {
            dismissCurrent(notifyManager = false)
        }
        mainHandler.postDelayed(dismissRunnable!!, delayMs)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeToDismiss(view: View) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var isDragging = false
        var velocityTracker: VelocityTracker? = null

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    isDragging = false
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().apply {
                        addMovement(event)
                    }
                    dismissRunnable?.let { mainHandler.removeCallbacks(it) }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY

                    if (!isDragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        isDragging = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    if (isDragging) {
                        v.translationX = deltaX
                        v.translationY = if (deltaY < 0) deltaY else deltaY * 0.25f

                        val maxDragDist = v.width * 0.75f
                        val currentDist = sqrt((deltaX * deltaX + v.translationY * v.translationY).toDouble()).toFloat()
                        val alphaFactor = (1f - (currentDist / maxDragDist)).coerceIn(0.2f, 1f)
                        v.alpha = alphaFactor
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)

                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val vy = velocityTracker?.yVelocity ?: 0f

                    velocityTracker?.recycle()
                    velocityTracker = null

                    if (isDragging) {
                        val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 1.5f
                        val widthThreshold = v.width * 0.35f
                        val heightThreshold = 50f * context.resources.displayMetrics.density

                        val isSwipeUp = (deltaY < -heightThreshold || vy < -minFlingVelocity) && abs(deltaX) < abs(deltaY) * 1.5f
                        val isSwipeRight = (deltaX > widthThreshold || vx > minFlingVelocity) && deltaX > 0
                        val isSwipeLeft = (deltaX < -widthThreshold || vx < -minFlingVelocity) && deltaX < 0

                        if (isSwipeUp) {
                            dismissCurrent(
                                notifyManager = true,
                                exitTranslationX = v.translationX,
                                exitTranslationY = -v.height.toFloat() - 150f
                            )
                        } else if (isSwipeRight) {
                            dismissCurrent(
                                notifyManager = true,
                                exitTranslationX = v.width.toFloat() + 200f,
                                exitTranslationY = v.translationY
                            )
                        } else if (isSwipeLeft) {
                            dismissCurrent(
                                notifyManager = true,
                                exitTranslationX = -v.width.toFloat() - 200f,
                                exitTranslationY = v.translationY
                            )
                        } else {
                            v.animate()
                                .translationX(0f)
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(250)
                                .setInterpolator(DecelerateInterpolator())
                                .start()

                            scheduleAutoDismiss(delayMs = 4000L)
                        }
                        true
                    } else {
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                        scheduleAutoDismiss(delayMs = 4000L)
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun dismissCurrent(
        notifyManager: Boolean,
        exitTranslationX: Float = 0f,
        exitTranslationY: Float = -100f
    ) {
        if (isDismissing) return

        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        safetyWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        safetyWatchdogRunnable = null

        val viewToDismiss = currentView
        val itemToDismiss = currentItem

        if (viewToDismiss == null) {
            container.removeAllViews()
            showNext()
            return
        }

        isDismissing = true

        if (notifyManager && itemToDismiss != null && itemToDismiss.isClearable) {
            deskConnectManager.dismissNotification(itemToDismiss.deviceId, itemToDismiss.notificationId)
        }

        viewToDismiss.animate()
            .translationX(exitTranslationX)
            .translationY(exitTranslationY)
            .alpha(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                private var finished = false

                override fun onAnimationCancel(animation: Animator) {
                    finish()
                }

                override fun onAnimationEnd(animation: Animator) {
                    finish()
                }

                private fun finish() {
                    if (finished) return
                    finished = true
                    container.removeView(viewToDismiss)
                    currentView = null
                    currentItem = null
                    isDismissing = false
                    showNext()
                }
            })
            .start()
    }

    fun destroy() {
        deskConnectManager.notificationListeners.remove(notificationListener)
        deskConnectManager.deviceListeners.remove(deviceListener)
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        safetyWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        safetyWatchdogRunnable = null
        container.removeAllViews()
        currentView = null
        currentItem = null
        queue.clear()
        isDismissing = false
    }
}
