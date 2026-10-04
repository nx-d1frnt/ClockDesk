package com.nxd1frnt.clockdesk2.ui.settings.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.SmartPixelManager

/**
 * Magnified OLED Loupe View for Smart Pixels.
 * Displays an enlarged 4x4/8x8 grid of simulated OLED pixel diodes,
 * showing which pixels are actively shut down vs illuminated.
 */
class SmartPixelLoupeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    var intensityPercent: Int = 50
        set(value) {
            field = value.coerceIn(10, 90)
            invalidate()
        }

    private val onPixelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val offPixelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val offPixelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cellRect = RectF()

    init {
        updateColors()
    }

    private fun updateColors() {
        val primaryColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.CYAN)
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)

        onPixelPaint.color = Color.rgb(
            (Color.red(primaryColor) * 0.7f + Color.red(onSurface) * 0.3f).toInt(),
            (Color.green(primaryColor) * 0.7f + Color.green(onSurface) * 0.3f).toInt(),
            (Color.blue(primaryColor) * 0.7f + Color.blue(onSurface) * 0.3f).toInt()
        )

        offPixelPaint.color = Color.parseColor("#090B0E")
        offPixelStrokePaint.color = Color.argb(45, 255, 255, 255)

        borderPaint.color = Color.argb(120, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        backgroundPaint.color = Color.parseColor("#05070A")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateColors()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cornerRadius = 14f * density

        // Draw loupe background and border
        val outerRect = RectF(borderPaint.strokeWidth / 2f, borderPaint.strokeWidth / 2f, w - borderPaint.strokeWidth / 2f, h - borderPaint.strokeWidth / 2f)
        canvas.drawRoundRect(outerRect, cornerRadius, cornerRadius, backgroundPaint)
        canvas.drawRoundRect(outerRect, cornerRadius, cornerRadius, borderPaint)

        // Draw 6x6 simulated pixel diodes (tiled Bayer pattern)
        val padding = 7f * density
        val gridW = w - (padding * 2)
        val gridH = h - (padding * 2)
        val gridSize = 6
        val cellW = gridW / gridSize
        val cellH = gridH / gridSize
        val cellGap = 1.5f * density
        val cellCorner = 2.5f * density

        val clamped = intensityPercent.coerceIn(5, 95)
        val blackThreshold = Math.round((clamped * 16f) / 100f).coerceIn(1, 15)

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val bayerX = col % 4
                val bayerY = row % 4
                val matrixVal = SmartPixelManager.BAYER_4X4[bayerY * 4 + bayerX]
                val isOff = matrixVal < blackThreshold

                val left = padding + col * cellW + (cellGap / 2f)
                val top = padding + row * cellH + (cellGap / 2f)
                val right = left + cellW - cellGap
                val bottom = top + cellH - cellGap

                cellRect.set(left, top, right, bottom)

                if (isOff) {
                    canvas.drawRoundRect(cellRect, cellCorner, cellCorner, offPixelPaint)
                    canvas.drawRoundRect(cellRect, cellCorner, cellCorner, offPixelStrokePaint)
                } else {
                    canvas.drawRoundRect(cellRect, cellCorner, cellCorner, onPixelPaint)
                }
            }
        }
    }
}
