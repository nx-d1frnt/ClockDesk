package com.nxd1frnt.clockdesk2.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SquigglySlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnSliderTouchListener {
        fun onStartTrackingTouch(slider: SquigglySlider)
        fun onStopTrackingTouch(slider: SquigglySlider)
    }

    var onSliderTouchListener: OnSliderTouchListener? = null

    var valueFrom: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var valueTo: Float = 100f
        set(value) {
            field = value
            invalidate()
        }

    var value: Float = 0f
        set(value) {
            field = value.coerceIn(valueFrom, valueTo)
            invalidate()
        }

    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    postInvalidateOnAnimation()
                }
            }
        }

    // Design Tokens (Scaled via density)
    private val dp = resources.displayMetrics.density
    private var amplitude = 3f * dp     // Wave height
    private var wavelength = 30f * dp     // Wave length
    private var phase = 0f
    private val phaseSpeed = 0.01f         // Speed of wave ripple

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }

    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
        strokeCap = Paint.Cap.ROUND
        color = 0x40FFFFFF                  // 25% transparent white
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    private val wavePath = Path()
    private val thumbRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingL = paddingLeft.toFloat()
        val paddingR = paddingRight.toFloat()
        val widthUsable = width - paddingL - paddingR
        val centerY = height / 2f

        // Calculate progress fraction
        val range = valueTo - valueFrom
        val fraction = if (range > 0) (value - valueFrom) / range else 0f
        val progressX = paddingL + fraction * widthUsable

        // 1. Draw Active Wave (Left Part)
        wavePath.reset()
        wavePath.moveTo(paddingL, centerY)
        val step = 2f * dp
        var x = paddingL
        while (x < progressX) {
            // Sine wave starting from progressX moving backwards (so wave flows naturally)
            val angle = ((progressX - x) / wavelength) * (2 * Math.PI) + phase
            val y = centerY + Math.sin(angle).toFloat() * amplitude
            wavePath.lineTo(x, y)
            x += step
        }
        // Snap precisely to the progress point
        wavePath.lineTo(progressX, centerY)
        canvas.drawPath(wavePath, activePaint)

        // 2. Draw Inactive Straight Line (Right Part)
        if (progressX < width - paddingR) {
            canvas.drawLine(progressX, centerY, width - paddingR, centerY, inactivePaint)
        }

        // 3. Draw Vertical Pill Thumb
        val thumbW = 4f * dp
        val thumbH = 14f * dp
        thumbRect.set(
            progressX - thumbW / 2f,
            centerY - thumbH / 2f,
            progressX + thumbW / 2f,
            centerY + thumbH / 2f
        )
        canvas.drawRoundRect(thumbRect, thumbW / 2f, thumbW / 2f, thumbPaint)

        // 4. Animate Phase if playing
        if (isPlaying) {
            phase += phaseSpeed
            if (phase > 2 * Math.PI) {
                phase -= (2 * Math.PI).toFloat()
            }
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        val paddingL = paddingLeft.toFloat()
        val paddingR = paddingRight.toFloat()
        val widthUsable = width - paddingL - paddingR

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onSliderTouchListener?.onStartTrackingTouch(this)
                updateValueFromTouch(event.x, paddingL, widthUsable)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateValueFromTouch(event.x, paddingL, widthUsable)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onSliderTouchListener?.onStopTrackingTouch(this)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateValueFromTouch(touchX: Float, paddingL: Float, widthUsable: Float) {
        if (widthUsable <= 0) return
        val fraction = ((touchX - paddingL) / widthUsable).coerceIn(0f, 1f)
        value = valueFrom + fraction * (valueTo - valueFrom)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minHeight = (32f * dp).toInt()
        val resolvedHeight = resolveSizeAndState(minHeight, heightMeasureSpec, 0)
        val resolvedWidth = resolveSize(100, widthMeasureSpec)
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }
}
