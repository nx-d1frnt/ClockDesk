package com.nxd1frnt.clockdesk2.ui.view

import android.animation.ValueAnimator
import android.graphics.Region
import android.os.PowerManager
import android.provider.Settings
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import com.nxd1frnt.clockdesk2.utils.ClockDigitAnimation
import com.nxd1frnt.clockdesk2.utils.MotionUtils
import java.util.Calendar
import java.util.Date

class ClockTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var digitAnimationsEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) stopDigitAnimation()
        }

    var digitAnimation: ClockDigitAnimation = ClockDigitAnimation.SLIDE
        set(value) {
            if (field == value) return
            field = value
            stopDigitAnimation()
        }

    private var previousText: String? = null
    private var outgoingText: String? = null
    private var digitAnimator: ValueAnimator? = null
    private var digitProgress = 1f

    private fun stopDigitAnimation() {
        digitAnimator?.cancel()
        digitAnimator = null
        outgoingText = null
        invalidate()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        val next = text?.toString().orEmpty()
        val old = previousText
        previousText = next
        if (old == next) return
        stopDigitAnimation()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val systemAnimations = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f
        if (!digitAnimationsEnabled || isAnalogMode || !isShown || !isLaidOut ||
            powerManager?.isPowerSaveMode == true || !systemAnimations || old == null ||
            old.length != next.length || old.indices.any { old[it] != next[it] &&
                (!old[it].isDigit() || !next[it].isDigit()) }) return
        outgoingText = old
        digitProgress = 0f
        digitAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250L
            // Keep progress within 0..1: it also controls opacity and flip scale.
            interpolator = when (digitAnimation) {
                ClockDigitAnimation.SLIDE -> MotionUtils.EMPHASIZED_DECELERATE
                ClockDigitAnimation.FADE -> MotionUtils.EMPHASIZED
                ClockDigitAnimation.FLIP -> AccelerateDecelerateInterpolator()
            }
            addUpdateListener {
                digitProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        stopDigitAnimation()
        super.onDetachedFromWindow()
    }

    @Suppress("DEPRECATION")
    private fun drawAnimatedDigits(canvas: Canvas): Boolean {
        val old = outgoingText ?: return false
        val current = text.toString()
        val textLayout = layout ?: return false
        if (digitProgress >= 1f || old.length != current.length) return false
        val changed = current.indices.filter { old[it] != current[it] }
        val xOffset = compoundPaddingLeft.toFloat() - scrollX
        val yOffset = baseline.toFloat() - textLayout.getLineBaseline(0) - scrollY
        val saved = canvas.save()
        for (index in changed) {
            val line = textLayout.getLineForOffset(index)
            val left = xOffset + textLayout.getPrimaryHorizontal(index)
            val right = xOffset + textLayout.getPrimaryHorizontal(index + 1)
            canvas.clipRect(minOf(left, right), yOffset + textLayout.getLineTop(line),
                maxOf(left, right), yOffset + textLayout.getLineBottom(line), Region.Op.DIFFERENCE)
        }
        super.onDraw(canvas)
        canvas.restoreToCount(saved)
        val originalAlpha = paint.alpha
        paint.color = currentTextColor
        for (index in changed) {
            val line = textLayout.getLineForOffset(index)
            val left = xOffset + textLayout.getPrimaryHorizontal(index)
            val right = xOffset + textLayout.getPrimaryHorizontal(index + 1)
            val top = yOffset + textLayout.getLineTop(line)
            val bottom = yOffset + textLayout.getLineBottom(line)
            val distance = bottom - top
            val cell = canvas.save()
            canvas.clipRect(minOf(left, right), top, maxOf(left, right), bottom)
            val base = yOffset + textLayout.getLineBaseline(line)
            when (digitAnimation) {
                ClockDigitAnimation.SLIDE -> {
                    paint.alpha = (originalAlpha * (1f - digitProgress)).toInt()
                    canvas.drawText(old, index, index + 1, left, base - distance * digitProgress, paint)
                    paint.alpha = (originalAlpha * digitProgress).toInt()
                    canvas.drawText(current, index, index + 1, left, base + distance * (1f - digitProgress), paint)
                }
                ClockDigitAnimation.FADE -> {
                    paint.alpha = (originalAlpha * (1f - digitProgress)).toInt()
                    canvas.drawText(old, index, index + 1, left, base, paint)
                    paint.alpha = (originalAlpha * digitProgress).toInt()
                    canvas.drawText(current, index, index + 1, left, base, paint)
                }
                ClockDigitAnimation.FLIP -> {
                    val outgoing = digitProgress < 0.5f
                    val scale = if (outgoing) 1f - digitProgress * 2f else digitProgress * 2f - 1f
                    canvas.scale(1f, scale, (left + right) / 2f, (top + bottom) / 2f)
                    paint.alpha = originalAlpha
                    canvas.drawText(if (outgoing) old else current, index, index + 1, left, base, paint)
                }
            }
            canvas.restoreToCount(cell)
        }
        paint.alpha = originalAlpha
        return true
    }

    var isAnalogMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var isTwoLineMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (!isAnalogMode) {
                    requestLayout()
                    invalidate()
                }
            }
        }

    var showBackdrop: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                if (isAnalogMode) {
                    invalidate()
                }
            }
        }

    var customFontVariationSettings: String? = null
        set(value) {
            if (field != value) {
                field = value
                if (isAnalogMode) {
                    invalidate()
                }
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
            val widthSize = MeasureSpec.getSize(widthMeasureSpec)
            val heightSize = MeasureSpec.getSize(heightMeasureSpec)
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)

            val desiredSize = (textSize * 2.2f).toInt().coerceAtLeast(60)

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

            val finalSize = size.coerceAtLeast(40)
            setMeasuredDimension(finalSize, finalSize)
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
        if (!isAnalogMode) {
            if (!drawAnimatedDigits(canvas)) super.onDraw(canvas)
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
            } catch (e: Throwable) {}
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
