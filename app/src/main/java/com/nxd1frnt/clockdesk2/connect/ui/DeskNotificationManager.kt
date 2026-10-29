package com.nxd1frnt.clockdesk2.connect.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.DeskConnectManager
import com.nxd1frnt.clockdesk2.connect.model.DeskNotification
import com.nxd1frnt.clockdesk2.connect.model.AppAlertType
import com.nxd1frnt.clockdesk2.connect.model.NotificationOrigin
import com.nxd1frnt.clockdesk2.connect.repo.DeskNotificationRepository
import com.nxd1frnt.clockdesk2.notifications.AppNotificationManager
import com.nxd1frnt.clockdesk2.desks.DeskType
import com.nxd1frnt.clockdesk2.ui.MainActivity
import com.nxd1frnt.clockdesk2.utils.Logger
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.sqrt

class DeskNotificationManager(
    private val context: Context,
    private val container: ViewGroup,
    private val deskConnectManager: DeskConnectManager
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = LinkedList<DeskNotification>()
    private var currentView: View? = null
    private var currentItem: DeskNotification? = null
    private var dismissRunnable: Runnable? = null
    private var safetyWatchdogRunnable: Runnable? = null
    private var isDismissing = false
    private var destroyed = false

    private val repository = DeskNotificationRepository.getInstance(context)
    private val notificationListener = object : DeskNotificationRepository.EventListener {
        override fun onPosted(item: DeskNotification, alert: Boolean) {
            if (currentItem?.key == item.key) {
                currentItem = item
                currentView?.let { updateViewContent(it, item) }
            } else if (alert) {
                enqueueNotification(item)
            } else {
                val index = queue.indexOfFirst { it.key == item.key }
                if (index >= 0) queue[index] = item
            }
        }

        override fun onRemoved(item: DeskNotification, acknowledged: Boolean) {
            queue.removeAll { it.key == item.key }
            if (currentItem?.key == item.key) dismissCurrent(notifyManager = false)
        }
    }

    init {
        repository.addEventListener(notificationListener)
        // Restore unfinished timer alerts, but do not replay a battery episode on every rotation.
        repository.getNotifications().filter {
            it.origin == NotificationOrigin.APP &&
                (it.alertType == AppAlertType.TIMER_FINISHED || !repository.wasBannerPresented(it))
        }.forEach(::enqueueNotification)
    }
    private fun getPrefs() = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

    private fun enqueueNotification(item: DeskNotification) {
        if (destroyed) return
        val bannerEnabled = when (item.alertType) {
            AppAlertType.TIMER_FINISHED -> getPrefs().getBoolean(AppNotificationManager.TIMER_BANNER, true)
            AppAlertType.DEVICE_BATTERY_LOW -> getPrefs().getBoolean(AppNotificationManager.BATTERY_BANNER, true)
            null -> getPrefs().getBoolean("desk_notification_popup_enabled", true)
        }
        if (!bannerEnabled) return
        // Replace existing notification in queue if same id
        queue.removeAll { it.key == item.key }

        // Highest priority first. A timer can interrupt a routine banner immediately.
        val position = queue.indexOfFirst { it.priority < item.priority }
        if (position < 0) queue.add(item) else queue.add(position, item)
        while (queue.size > 3) queue.removeLast()
        if (item.priority > (currentItem?.priority ?: Int.MAX_VALUE)) {
            dismissCurrent(notifyManager = false)
        }
        if (currentView == null && !isDismissing) {
            showNext()
        }
    }

    private fun updateViewContent(view: View, item: DeskNotification) {
        val deviceName = if (item.origin == NotificationOrigin.APP && item.relatedDeviceId == null) null else {
            val dev = deskConnectManager.discoveredDevices[item.relatedDeviceId ?: item.deviceId]
            dev?.getDisplayName()
        }

        DeskNotificationHelper.applyBannerCustomization(
            context = context,
            view = view,
            prefs = getPrefs(),
            appName = item.appName,
            deviceName = deviceName,
            title = item.title,
            text = item.text,
            iconBytes = item.iconBytes,
            notificationId = item.notificationId,
            isClearable = item.isClearable,
            iconRes = item.iconRes,
            isPreview = false
        )
    }

    private fun showNext() {
        if (isDismissing || destroyed) return

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

        val prefs = getPrefs()
        val isBottom = DeskNotificationHelper.isBottomPosition(prefs)
        val density = context.resources.displayMetrics.density

        // Position container at top or bottom of parent ConstraintLayout
        (container.layoutParams as? ConstraintLayout.LayoutParams)?.let { clp ->
            if (isBottom) {
                clp.topToTop = ConstraintLayout.LayoutParams.UNSET
                clp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            } else {
                clp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                clp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            }
            container.layoutParams = clp
        }

        val view = LayoutInflater.from(context).inflate(R.layout.view_ambient_notification, container, false)
        currentView = view

        (view.layoutParams as? FrameLayout.LayoutParams)?.let { flp ->
            flp.gravity = DeskNotificationHelper.getBannerGravity(prefs)
            val marginPx = (16f * density).toInt()
            val sideMarginPx = (24f * density).toInt()
            flp.marginStart = sideMarginPx
            flp.marginEnd = sideMarginPx
            if (isBottom) {
                flp.topMargin = 0
                flp.bottomMargin = marginPx
            } else {
                flp.topMargin = marginPx
                flp.bottomMargin = 0
            }
            view.layoutParams = flp
        }

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

        val isTimer = item.alertType == AppAlertType.TIMER_FINISHED
        view.setOnClickListener {
            when (item.alertType) {
                AppAlertType.TIMER_FINISHED -> {
                    repository.dismissNotification(item)
                    MainActivity.openDeskType(DeskType.TIMER)
                }
                AppAlertType.DEVICE_BATTERY_LOW -> MainActivity.openDeskType(DeskType.DEVICES)
                null -> Unit
            }
        }

        dismissBtn.setOnClickListener { dismissCurrent(notifyManager = true) }
        setupSwipeToDismiss(view, isBottom)

        // Animate entrance: slide from top or bottom + fade in
        view.translationX = 0f
        view.translationY = if (isBottom) 120f else -120f
        view.alpha = 0f
        container.addView(view)
        repository.markBannerPresented(item)

        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()

        // Auto dismiss based on user preference: duration if single, 3 seconds if backlog exists (timers stay 60s)
        val baseDurationSec = prefs.getInt("desk_notification_duration", 5)
        val defaultDisplayMs = if (isTimer) 60_000L else (baseDurationSec * 1000L).coerceAtLeast(2000L)
        val displayTimeMs = if (queue.isNotEmpty() && !isTimer) 3000L else defaultDisplayMs
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
            val isBottom = DeskNotificationHelper.isBottomPosition(getPrefs())
            dismissCurrent(
                notifyManager = false,
                exitTranslationX = 0f,
                exitTranslationY = if (isBottom) 100f else -100f
            )
        }
        mainHandler.postDelayed(dismissRunnable!!, delayMs)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeToDismiss(view: View, isBottom: Boolean) {
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
                        v.translationY = if (isBottom) {
                            if (deltaY > 0) deltaY else deltaY * 0.25f
                        } else {
                            if (deltaY < 0) deltaY else deltaY * 0.25f
                        }

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

                        val isVerticalDismiss = if (isBottom) {
                            (deltaY > heightThreshold || vy > minFlingVelocity) && abs(deltaX) < abs(deltaY) * 1.5f
                        } else {
                            (deltaY < -heightThreshold || vy < -minFlingVelocity) && abs(deltaX) < abs(deltaY) * 1.5f
                        }
                        val isSwipeRight = (deltaX > widthThreshold || vx > minFlingVelocity) && deltaX > 0
                        val isSwipeLeft = (deltaX < -widthThreshold || vx < -minFlingVelocity) && deltaX < 0

                        if (isVerticalDismiss) {
                            dismissCurrent(
                                notifyManager = true,
                                exitTranslationX = v.translationX,
                                exitTranslationY = if (isBottom) v.height.toFloat() + 150f else -v.height.toFloat() - 150f
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
        exitTranslationY: Float = if (DeskNotificationHelper.isBottomPosition(getPrefs())) 100f else -100f
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
            repository.dismissNotification(itemToDismiss)
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
        destroyed = true
        repository.removeEventListener(notificationListener)
        currentView?.animate()?.setListener(null)?.cancel()
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
