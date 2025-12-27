package com.nxd1frnt.clockdesk2

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

    private fun shiftViews() {
        val dx = (random.nextInt(maxShiftPx * 2 + 1) - maxShiftPx).toFloat()
        val dy = (random.nextInt(maxShiftPx * 2 + 1) - maxShiftPx).toFloat()

        views.forEach { view ->
            view.animate()
                .translationX(dx)
                .translationY(dy)
                .setDuration(1000)
                .start()
        }
    }

    private fun resetPositions() {
        views.forEach { view ->
            view.animate()
                .translationX(0f)
                .translationY(0f)
                .setDuration(500)
                .start()
        }
    }
}