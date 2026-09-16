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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            iconView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val radius = view.context.resources.displayMetrics.density * 8f
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            iconView.clipToOutline = true
        }

        var iconApplied = false
        if (item.iconBytes != null && item.iconBytes.isNotEmpty()) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(item.iconBytes, 0, item.iconBytes.size)
                if (bitmap != null) {
                    iconView.clearColorFilter()
                    ImageViewCompat.setImageTintList(iconView, null)
                    iconView.setImageBitmap(bitmap)
                    iconApplied = true
                }
            } catch (e: Exception) {
                Logger.w("DeskNotificationManager") { "Failed to decode notification icon: ${e.message}" }
            }
        }

        if (!iconApplied) {
            val pkg = if (item.notificationId.contains("|")) {
                item.notificationId.split("|").getOrNull(1)
            } else null

            if (!pkg.isNullOrEmpty()) {
                try {
                    val appIcon = context.packageManager.getApplicationIcon(pkg)
                    iconView.clearColorFilter()
                    ImageViewCompat.setImageTintList(iconView, null)
                    iconView.setImageDrawable(appIcon)
                    iconApplied = true
                } catch (ignored: Exception) {
                }
            }
        }

        if (!iconApplied) {
            iconView.setImageResource(R.drawable.ic_widgets_outline)
            iconView.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        }

        dismissBtn.visibility = if (item.isClearable) View.VISIBLE else View.GONE
        dismissBtn.setOnClickListener {
            dismissCurrent(notifyManager = true)
        }

        setupSwipeToDismiss(view)

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
        scheduleAutoDismiss(6000)
    }

    private fun scheduleAutoDismiss(delayMs: Long = 6000) {
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

                            scheduleAutoDismiss(delayMs = 4000)
                        }
                        true
                    } else {
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                        scheduleAutoDismiss(delayMs = 4000)
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
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null

        val viewToDismiss = currentView ?: return
        val itemToDismiss = currentItem

        if (notifyManager && itemToDismiss != null && itemToDismiss.isClearable) {
            deskConnectManager.dismissNotification(itemToDismiss.deviceId, itemToDismiss.notificationId)
        }

        currentView = null
        currentItem = null

        viewToDismiss.animate()
            .translationX(exitTranslationX)
            .translationY(exitTranslationY)
            .alpha(0f)
            .setDuration(250)
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
