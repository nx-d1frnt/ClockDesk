package com.nxd1frnt.clockdesk2.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import java.util.Random

class BurnInProtectionManager(
    initialViews: List<View>,
    private var maxShiftPx: Int = 10,
    private val intervalMs: Long = 60000L //1 minute
) {
    private val views = mutableListOf<View>()

    init {
        updateViews(initialViews)
    }

    fun updateViews(newViews: List<View>) {
        views.clear()
        newViews.forEach { rawView ->
            val target = resolveTargetView(rawView)
            if (rawView !== target) {
                rawView.translationX = 0f
                rawView.translationY = 0f
            }
            if (!views.contains(target)) {
                views.add(target)
            }
        }
    }

    private fun resolveTargetView(view: View): View {
        if (view.id == com.nxd1frnt.clockdesk2.R.id.smart_chip_container) {
            val parent = view.parent as? View
            if (parent != null && parent.id == com.nxd1frnt.clockdesk2.R.id.smart_chip_scrollview) {
                return parent
            }
        }
        return view
    }

    fun setMaxShiftPx(px: Int) {
        maxShiftPx = px.coerceAtLeast(1)
    }

    fun setShiftDistanceDp(context: Context, dp: Float) {
        val density = context.resources.displayMetrics.density
        setMaxShiftPx((dp * density).toInt())
    }

    companion object {
        const val PREF_KEY_SHIFT_DP = "burn_in_shift_distance_dp"
        const val DEFAULT_SHIFT_DP = 10
    }
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()
    private var isRunning = false

    var currentShiftX: Float = 0f
        private set
    var currentShiftY: Float = 0f
        private set

    private val shiftRunnable = object : Runnable {
        override fun run() {
            shiftViews()
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        if (!isRunning) {
            isRunning = true
            handler.post(shiftRunnable)
        }
    }

    fun stop() {
        if (isRunning) {
            isRunning = false
            handler.removeCallbacks(shiftRunnable)
            resetPositions()
        }
    }

    fun getBasePosition(view: View): Pair<Float, Float> {
        val targetView = resolveTargetView(view)
        val context = targetView.context ?: return Pair(0f, 0f)
        val prefs = context.getSharedPreferences("WidgetPositions", Context.MODE_PRIVATE)
        val idName = try {
            context.resources.getResourceEntryName(targetView.id)
        } catch (e: Exception) {
            "ID:${targetView.id}"
        }
        val isFree = prefs.getBoolean("${idName}_individual_free_mode", false) ||
                (targetView.id == com.nxd1frnt.clockdesk2.R.id.smart_chip_scrollview && prefs.getBoolean("smart_chip_container_individual_free_mode", false))
        return if (isFree) {
            val fallback = if (targetView.id == com.nxd1frnt.clockdesk2.R.id.smart_chip_scrollview) "smart_chip_container" else idName
            val savedX = prefs.getFloat("${idName}_x", prefs.getFloat("${fallback}_x", 0f))
            val savedY = prefs.getFloat("${idName}_y", prefs.getFloat("${fallback}_y", 0f))
            Pair(savedX, savedY)
        } else {
            Pair(0f, 0f)
        }
    }

    private fun clampShift(view: View, targetX: Float, targetY: Float): Pair<Float, Float> {
        val parent = view.parent as? View ?: return Pair(targetX, targetY)
        if (view.width <= 0 || view.height <= 0 || parent.width <= 0 || parent.height <= 0) {
            return Pair(targetX, targetY)
        }

        val minX = -view.left.toFloat()
        val maxX = (parent.width - view.right).toFloat()
        val minY = -view.top.toFloat()
        val maxY = (parent.height - view.bottom).toFloat()

        val clampedX = if (minX <= maxX) targetX.coerceIn(minX, maxX) else targetX
        val clampedY = if (minY <= maxY) targetY.coerceIn(minY, maxY) else targetY

        return Pair(clampedX, clampedY)
    }

    private fun shiftViews() {
        val dx = (random.nextInt(maxShiftPx * 2 + 1) - maxShiftPx).toFloat()
        val dy = (random.nextInt(maxShiftPx * 2 + 1) - maxShiftPx).toFloat()

        currentShiftX = dx
        currentShiftY = dy

        views.forEach { rawView ->
            val view = resolveTargetView(rawView)
            if (rawView !== view) {
                rawView.translationX = 0f
                rawView.translationY = 0f
            }
            val (baseX, baseY) = getBasePosition(view)
            val (targetX, targetY) = clampShift(view, baseX + dx, baseY + dy)
            view.animate()
                .translationX(targetX)
                .translationY(targetY)
                .setDuration(1000)
                .start()
        }
    }

    fun resetPositions() {
        currentShiftX = 0f
        currentShiftY = 0f
        views.forEach { rawView ->
            val view = resolveTargetView(rawView)
            if (rawView !== view) {
                rawView.translationX = 0f
                rawView.translationY = 0f
            }
            val (baseX, baseY) = getBasePosition(view)
            view.animate()
                .translationX(baseX)
                .translationY(baseY)
                .setDuration(500)
                .start()
        }
    }
}