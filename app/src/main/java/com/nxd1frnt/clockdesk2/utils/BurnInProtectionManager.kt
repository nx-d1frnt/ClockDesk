package com.nxd1frnt.clockdesk2.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import java.util.Random

class BurnInProtectionManager(
    private val views: List<View>,
    private val maxShiftPx: Int = 10,
    private val intervalMs: Long = 60000L //1 minute
) {
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
        val context = view.context ?: return Pair(0f, 0f)
        val prefs = context.getSharedPreferences("WidgetPositions", Context.MODE_PRIVATE)
        val idName = try {
            context.resources.getResourceEntryName(view.id)
        } catch (e: Exception) {
            "ID:${view.id}"
        }
        val isFree = prefs.getBoolean("${idName}_individual_free_mode", false)
        return if (isFree) {
            val savedX = prefs.getFloat("${idName}_x", 0f)
            val savedY = prefs.getFloat("${idName}_y", 0f)
            Pair(savedX, savedY)
        } else {
            Pair(0f, 0f)
        }
    }

    private fun shiftViews() {
        val dx = (random.nextInt(maxShiftPx * 2 + 1) - maxShiftPx).toFloat()
        val dy = (random.nextInt(maxShiftPx * 2 + 1) - maxShiftPx).toFloat()

        currentShiftX = dx
        currentShiftY = dy

        views.forEach { view ->
            val (baseX, baseY) = getBasePosition(view)
            view.animate()
                .translationX(baseX + dx)
                .translationY(baseY + dy)
                .setDuration(1000)
                .start()
        }
    }

    fun resetPositions() {
        currentShiftX = 0f
        currentShiftY = 0f
        views.forEach { view ->
            val (baseX, baseY) = getBasePosition(view)
            view.animate()
                .translationX(baseX)
                .translationY(baseY)
                .setDuration(500)
                .start()
        }
    }
}