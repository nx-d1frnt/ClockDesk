package com.nxd1frnt.clockdesk2.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import java.util.Calendar
import java.util.Date

class ClockTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var isAnalogMode: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var showBackdrop: Boolean = true
        set(value) {
            field = value
            if (isAnalogMode) {
                invalidate()
            }
        }

    var customFontVariationSettings: String? = null
        set(value) {
            field = value
            if (isAnalogMode) {
                invalidate()
            }
        }

    private var calendar: Calendar = Calendar.getInstance()

    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val minuteHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun setTime(date: Date) {
        calendar.time = date
        if (isAnalogMode) {
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isAnalogMode) {
            val desiredSize = (textSize * 2.2f).toInt().coerceAtLeast(200)
            val width = resolveSize(desiredSize, widthMeasureSpec)
            val height = resolveSize(desiredSize, heightMeasureSpec)
            val size = minOf(width, height)
            setMeasuredDimension(size, size)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!isAnalogMode) {
            super.onDraw(canvas)
            return
        }

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0 || height <= 0) return

        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.95f
        val color = currentTextColor

        // 1. Dark Circular Backdrop Disc (if enabled)
        if (showBackdrop) {
            backdropPaint.color = Color.argb((alpha * 170).toInt().coerceIn(0, 255), 24, 24, 26)
            canvas.drawCircle(cx, cy, radius * 0.88f, backdropPaint)
        }

        // 2. Extra Bold Large Numbers (12, 3, 6, 9)
        numberPaint.color = color
        numberPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        numberPaint.typeface = paint.typeface ?: typeface ?: Typeface.DEFAULT_BOLD
        numberPaint.textSize = radius * 0.44f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val varSettings = customFontVariationSettings ?: fontVariationSettings
                if (!varSettings.isNullOrEmpty()) {
                    numberPaint.fontVariationSettings = varSettings
                }
            } catch (e: Exception) {}
        }

        val fontMetrics = numberPaint.fontMetrics
        val textOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
        val numberRadius = radius * 0.58f

        // Draw 12, 3, 6, 9
        canvas.drawText("12", cx, cy - numberRadius - textOffset, numberPaint)
        canvas.drawText("3", cx + numberRadius, cy - textOffset, numberPaint)
        canvas.drawText("6", cx, cy + numberRadius - textOffset, numberPaint)
        canvas.drawText("9", cx - numberRadius, cy - textOffset, numberPaint)

        // Time calculation
        val hours = calendar.get(Calendar.HOUR)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)

        val hourAngle = (hours % 12 + minutes / 60f) * 30f
        val minuteAngle = (minutes + seconds / 60f) * 6f

        // 3. Hour Hand (Chunky Rounded Pill)
        hourHandPaint.color = color
        hourHandPaint.alpha = (alpha * 255 * 0.90f).toInt().coerceIn(0, 255)
        hourHandPaint.strokeWidth = radius * 0.14f
        drawHand(canvas, cx, cy, hourAngle, radius * 0.42f, hourHandPaint)

        // 4. Minute Hand (Sleek Rounded Pill)
        minuteHandPaint.color = color
        minuteHandPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        minuteHandPaint.strokeWidth = radius * 0.07f
        drawHand(canvas, cx, cy, minuteAngle, radius * 0.72f, minuteHandPaint)

        // 5. Center Pin Dot
        centerDotPaint.color = color
        centerDotPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, radius * 0.05f, centerDotPaint)
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, angleDegrees: Float, length: Float, paint: Paint) {
        canvas.save()
        canvas.rotate(angleDegrees, cx, cy)
        canvas.drawLine(cx, cy, cx, cy - length, paint)
        canvas.restore()
    }
}
