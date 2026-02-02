package com.nxd1frnt.clockdesk2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.PaintDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

class SmartPixelManager(
    private val context: Context,
    private val overlayView: View,
    private val timeoutMs: Long = 5000L // Время до активации (например, 5 сек)
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isActive = false
    private var isEnabled = true

    // Таймер для активации эффекта
    private val activationRunnable = Runnable {
        enableEffect()
    }

    init {
        // Создаем паттерн шахматной доски 2x2 пикселя
        setupPattern()
    }

    private fun setupPattern() {
        val blockSize = 2
        val patternSize = blockSize * 2

        val bitmap = Bitmap.createBitmap(patternSize, patternSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            color = Color.BLACK
            alpha = 255
        }

        canvas.drawRect(0f, 0f, blockSize.toFloat(), blockSize.toFloat(), paint)

        canvas.drawRect(
            blockSize.toFloat(),
            blockSize.toFloat(),
            patternSize.toFloat(),
            patternSize.toFloat(),
            paint
        )

        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

        val drawable = PaintDrawable()
        drawable.paint.shader = shader
        overlayView.background = drawable

        overlayView.isClickable = false
        overlayView.isFocusable = false
    }

    fun onUserInteraction() {
        if (!isEnabled) return

        if (isActive) {
            disableEffect()
        }

        handler.removeCallbacks(activationRunnable)
        handler.postDelayed(activationRunnable, timeoutMs)
    }

    fun start() {
        isEnabled = true
        onUserInteraction()
    }

    fun stop() {
        isEnabled = false
        handler.removeCallbacks(activationRunnable)
        disableEffect()
    }

    private fun enableEffect() {
        if (isActive) return
        isActive = true

        overlayView.visibility = View.VISIBLE
        overlayView.animate()
            .alpha(1f)
            .setDuration(1000)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun disableEffect() {
        isActive = false
        overlayView.animate()
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                overlayView.visibility = View.GONE
            }
            .start()
    }
}