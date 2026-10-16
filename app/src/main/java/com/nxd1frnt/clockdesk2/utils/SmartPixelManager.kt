package com.nxd1frnt.clockdesk2.utils

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

    var intensity: Int = DEFAULT_INTENSITY
        private set

    init {
        try {
            val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
            intensity = prefs.getInt(PREF_KEY_INTENSITY, DEFAULT_INTENSITY).coerceIn(10, 90)
        } catch (e: Exception) {
            intensity = DEFAULT_INTENSITY
        }
        setupPattern()
    }

    fun setIntensity(newIntensity: Int) {
        val clamped = newIntensity.coerceIn(10, 90)
        if (intensity != clamped) {
            intensity = clamped
            setupPattern()
        }
    }

    private fun setupPattern() {
        val shader = createPatternShader(intensity, blockSize = 2)
        val drawable = PaintDrawable().apply {
            paint.shader = shader
        }
        overlayView.background = drawable

        overlayView.isClickable = false
        overlayView.isFocusable = false
    }

    companion object {
        const val PREF_KEY_INTENSITY = "smart_pixels_intensity"
        const val DEFAULT_INTENSITY = 50

        val BAYER_4X4 = intArrayOf(
             0,  8,  2, 10,
            12,  4, 14,  6,
             3, 11,  1,  9,
            15,  7, 13,  5
        )

        fun createPatternBitmap(intensityPercent: Int, blockSize: Int = 2): Bitmap {
            val clamped = intensityPercent.coerceIn(5, 95)
            val blackCells = Math.round((clamped * 16f) / 100f).coerceIn(1, 15)
            val patternDimension = 4 * blockSize

            val bitmap = Bitmap.createBitmap(patternDimension, patternDimension, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint().apply {
                color = Color.BLACK
                alpha = 255
            }

            for (y in 0 until 4) {
                for (x in 0 until 4) {
                    val threshold = BAYER_4X4[y * 4 + x]
                    if (threshold < blackCells) {
                        val left = (x * blockSize).toFloat()
                        val top = (y * blockSize).toFloat()
                        canvas.drawRect(left, top, left + blockSize, top + blockSize, paint)
                    }
                }
            }
            return bitmap
        }

        fun createPatternShader(intensityPercent: Int, blockSize: Int = 2): BitmapShader {
            val bitmap = createPatternBitmap(intensityPercent, blockSize)
            return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
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