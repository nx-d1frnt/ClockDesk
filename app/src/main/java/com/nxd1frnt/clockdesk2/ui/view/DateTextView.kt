package com.nxd1frnt.clockdesk2.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DateTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var isBadgeMode: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var isTwoLineMode: Boolean = false
        set(value) {
            field = value
            if (!isBadgeMode) {
                if (value) {
                    gravity = android.view.Gravity.START or (gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
                    }
                }
                requestLayout()
                invalidate()
            }
        }

    var customFontVariationSettings: String? = null
        set(value) {
            field = value
            if (isBadgeMode) {
                invalidate()
            }
        }

    private var calendar: Calendar = Calendar.getInstance()

    private val cardBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val dayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val cardPath = Path()
    private val cardRect = RectF()
    private val headerRect = RectF()

    fun setDate(date: Date) {
        calendar.time = date
        if (isBadgeMode) {
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isBadgeMode) {
            val widthSize = MeasureSpec.getSize(widthMeasureSpec)
            val heightSize = MeasureSpec.getSize(heightMeasureSpec)
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)

            val desiredSize = (textSize * 2.8f).toInt().coerceAtLeast(100)

            val size = when {
                widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY -> {
                    minOf(widthSize, heightSize)
                }
                widthMode == MeasureSpec.EXACTLY -> {
                    minOf(desiredSize, widthSize)
                }
                heightMode == MeasureSpec.EXACTLY -> {
                    minOf(desiredSize, heightSize)
                }
                widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.AT_MOST -> {
                    minOf(desiredSize, minOf(widthSize, heightSize))
                }
                widthMode == MeasureSpec.AT_MOST -> {
                    minOf(desiredSize, widthSize)
                }
                heightMode == MeasureSpec.AT_MOST -> {
                    minOf(desiredSize, heightSize)
                }
                else -> desiredSize
            }

            val finalSize = size.coerceAtLeast(60)
            setMeasuredDimension(finalSize, (finalSize * 1.08f).toInt())
        } else if (isTwoLineMode) {
            val rawText = text?.toString() ?: ""
            val parts = rawText.split('\n')
            val line1 = if (parts.isNotEmpty()) parts[0].replace("\u2060", "") else ""
            val line2 = if (parts.size > 1) parts[1].replace("\u2060", "") else ""

            val line1Width = paint.measureText(line1)
            val line2Width = paint.measureText(line2)
            val maxTextWidth = maxOf(line1Width, line2Width)
            val neededWidth = (maxTextWidth + compoundPaddingLeft + compoundPaddingRight + 16).toInt()

            val widthSize = MeasureSpec.getSize(widthMeasureSpec)
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)

            val targetWidth = if (widthMode == MeasureSpec.EXACTLY) {
                maxOf(widthSize, neededWidth)
            } else {
                neededWidth
            }

            val overrideWidthSpec = MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY)
            super.onMeasure(overrideWidthSpec, heightMeasureSpec)
            setMeasuredDimension(maxOf(measuredWidth, targetWidth), measuredHeight)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!isBadgeMode) {
            super.onDraw(canvas)
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = 4f
        val left = padding
        val top = padding
        val right = w - padding
        val bottom = h - padding
        val cornerRadius = w * 0.18f

        cardRect.set(left, top, right, bottom)
        cardPath.reset()
        cardPath.addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)

        val accentColor = currentTextColor
        val viewAlpha = alpha

        val isLightAccent = ColorUtils.calculateLuminance(accentColor) > 0.5
        // Card body: solid modern surface
        val bodyBgColor = if (isLightAccent) {
            Color.argb((viewAlpha * 240).toInt().coerceIn(0, 255), 28, 29, 33)
        } else {
            Color.argb((viewAlpha * 240).toInt().coerceIn(0, 255), 245, 245, 248)
        }

        canvas.save()
        canvas.clipPath(cardPath)

        // 1. Draw solid card body background
        cardBackgroundPaint.color = bodyBgColor
        canvas.drawRect(cardRect, cardBackgroundPaint)

        // 2. Draw top header banner
        val headerHeight = (bottom - top) * 0.32f
        headerRect.set(left, top, right, top + headerHeight)
        headerPaint.color = accentColor
        headerPaint.alpha = (viewAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawRect(headerRect, headerPaint)

        // 3. Month text in top header banner
        val tf = paint.typeface ?: typeface ?: Typeface.DEFAULT_BOLD
        headerTextPaint.typeface = tf
        headerTextPaint.textSize = headerHeight * 0.55f
        headerTextPaint.isFakeBoldText = true

        val headerTextColor = if (ColorUtils.calculateLuminance(accentColor) > 0.5) {
            Color.argb((viewAlpha * 240).toInt().coerceIn(0, 255), 18, 18, 20)
        } else {
            Color.argb((viewAlpha * 240).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        headerTextPaint.color = headerTextColor

        applyFontVariation(headerTextPaint)

        val monthStr = try {
            SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time).uppercase(Locale.getDefault())
        } catch (e: Exception) {
            " "
        }

        val headerMetrics = headerTextPaint.fontMetrics
        val headerTextY = headerRect.centerY() - (headerMetrics.descent + headerMetrics.ascent) / 2f
        canvas.drawText(monthStr, headerRect.centerX(), headerTextY, headerTextPaint)

        // 4. Large Day number in center of the body
        dayTextPaint.typeface = tf
        val bodyHeight = bottom - (top + headerHeight)
        dayTextPaint.textSize = bodyHeight * 0.65f
        dayTextPaint.isFakeBoldText = true

        val dayTextColor = if (isLightAccent) {
            Color.argb((viewAlpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
        } else {
            Color.argb((viewAlpha * 255).toInt().coerceIn(0, 255), 20, 20, 22)
        }
        dayTextPaint.color = dayTextColor

        applyFontVariation(dayTextPaint)

        val dayStr = calendar.get(Calendar.DAY_OF_MONTH).toString()
        val dayMetrics = dayTextPaint.fontMetrics
        val bodyCenterY = (top + headerHeight) + bodyHeight / 2f
        val dayTextY = bodyCenterY - (dayMetrics.descent + dayMetrics.ascent) / 2f
        canvas.drawText(dayStr, cardRect.centerX(), dayTextY, dayTextPaint)

        canvas.restore()
    }

    private fun applyFontVariation(targetPaint: Paint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val varSettings = customFontVariationSettings ?: fontVariationSettings
                if (!varSettings.isNullOrEmpty()) {
                    targetPaint.fontVariationSettings = varSettings
                }
            } catch (e: Throwable) {}
        }
    }
}
