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
import com.nxd1frnt.clockdesk2.utils.DateStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DateTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var dateStyle: DateStyle = DateStyle.STANDARD
        set(value) {
            field = value
            isBadgeMode = value.isBadge
            isTwoLineMode = value.isTwoLine
            if (value.isHeroDay || value.isChipPill) {
                requestLayout()
                invalidate()
            }
        }

    var isBadgeMode: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var isTwoLineMode: Boolean = false
        set(value) {
            field = value
            if (!isBadgeMode && dateStyle != DateStyle.HERO_DAY && dateStyle != DateStyle.CHIP_PILL) {
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
            if (isBadgeMode || dateStyle.isHeroDay || dateStyle.isChipPill) {
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

    private val heroSideTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }

    private val cardPath = Path()
    private val cardRect = RectF()
    private val headerRect = RectF()

    fun setDate(date: Date) {
        calendar.time = date
        if (isBadgeMode || dateStyle.isHeroDay || dateStyle.isChipPill) {
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isBadgeMode || dateStyle == DateStyle.CALENDAR_BADGE) {
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
        } else if (dateStyle == DateStyle.CHIP_PILL) {
            // Measure pill container size based on content
            val rawText = text?.toString()?.takeIf { it.isNotBlank() } ?: SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(calendar.time)
            val tf = paint.typeface ?: typeface ?: Typeface.DEFAULT_BOLD
            dayTextPaint.typeface = tf
            dayTextPaint.textSize = textSize
            dayTextPaint.isFakeBoldText = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dayTextPaint.letterSpacing = 0.04f
            }
            applyFontVariation(dayTextPaint)

            val textWidth = dayTextPaint.measureText(rawText)
            val fm = dayTextPaint.fontMetrics
            val textHeight = fm.descent - fm.ascent

            val padX = textSize * 0.75f
            val padY = textSize * 0.35f
            val desiredW = (textWidth + padX * 2f + compoundPaddingLeft + compoundPaddingRight + 8f).toInt()
            val desiredH = (textHeight + padY * 2f + compoundPaddingTop + compoundPaddingBottom + 8f).toInt()

            val w = resolveSize(desiredW, widthMeasureSpec)
            val h = resolveSize(desiredH, heightMeasureSpec)
            setMeasuredDimension(w, h)
        } else if (dateStyle == DateStyle.HERO_DAY) {
            // Measure big day number on left + stacked weekday/month on right
            val tf = paint.typeface ?: typeface ?: Typeface.DEFAULT_BOLD
            val daySize = textSize * 1.35f
            val sideSize = textSize * 0.50f

            dayTextPaint.typeface = tf
            dayTextPaint.textSize = daySize
            dayTextPaint.isFakeBoldText = true
            applyFontVariation(dayTextPaint)

            heroSideTextPaint.typeface = tf
            heroSideTextPaint.textSize = sideSize
            heroSideTextPaint.isFakeBoldText = true
            applyFontVariation(heroSideTextPaint)

            val dayStr = String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.DAY_OF_MONTH))
            val dayWidth = dayTextPaint.measureText(dayStr)

            val weekdayStr = try { SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time).uppercase(Locale.getDefault()) } catch (e: Exception) { "" }
            val monthStr = try { SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time).uppercase(Locale.getDefault()) } catch (e: Exception) { "" }
            val sideWidth = maxOf(heroSideTextPaint.measureText(weekdayStr), heroSideTextPaint.measureText(monthStr))

            val gap = textSize * 0.35f
            val desiredW = (dayWidth + gap + sideWidth + compoundPaddingLeft + compoundPaddingRight + 16f).toInt()
            val desiredH = (daySize * 1.15f + compoundPaddingTop + compoundPaddingBottom).toInt()

            val w = resolveSize(desiredW, widthMeasureSpec)
            val h = resolveSize(desiredH, heightMeasureSpec)
            setMeasuredDimension(w, h)
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

    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val headerPillRect = RectF()

    override fun onDraw(canvas: Canvas) {
        when {
            dateStyle == DateStyle.CALENDAR_BADGE || isBadgeMode -> {
                drawCalendarBadge(canvas)
            }
            dateStyle == DateStyle.CHIP_PILL -> {
                drawChipPill(canvas)
            }
            dateStyle == DateStyle.HERO_DAY -> {
                drawHeroDay(canvas)
            }
            else -> {
                super.onDraw(canvas)
            }
        }
    }

    private fun drawCalendarBadge(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = 4f
        val left = padding
        val top = padding
        val right = w - padding
        val bottom = h - padding
        val cardWidth = right - left
        val cardHeight = bottom - top

        // Material 3 Expressive squircle rounding (~24-28% of width)
        val cornerRadius = cardWidth * 0.26f

        cardRect.set(left, top, right, bottom)
        cardPath.reset()
        cardPath.addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)

        val accentColor = currentTextColor
        val viewAlpha = alpha

        val isLightAccent = ColorUtils.calculateLuminance(accentColor) > 0.5

        // Container background: Surface container tonal elevated surface
        val bodyBgColor = if (isLightAccent) {
            Color.argb((viewAlpha * 230).toInt().coerceIn(0, 255), 30, 32, 38)
        } else {
            Color.argb((viewAlpha * 230).toInt().coerceIn(0, 255), 242, 244, 250)
        }

        canvas.save()
        canvas.clipPath(cardPath)

        // 1. Draw solid card body background
        cardBackgroundPaint.color = bodyBgColor
        canvas.drawRect(cardRect, cardBackgroundPaint)

        // 2. Draw subtle border stroke (M3 Expressive outline variant)
        cardBorderPaint.color = ColorUtils.setAlphaComponent(accentColor, (viewAlpha * 40).toInt().coerceIn(0, 255))
        cardBorderPaint.strokeWidth = 2.5f
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardBorderPaint)

        // 3. Draw M3 Expressive floating pill / capsule header for the month
        val pillMarginX = cardWidth * 0.12f
        val pillMarginTop = cardHeight * 0.10f
        val pillHeight = cardHeight * 0.22f
        val pillLeft = left + pillMarginX
        val pillRight = right - pillMarginX
        val pillTop = top + pillMarginTop
        val pillBottom = pillTop + pillHeight
        val pillRadius = pillHeight / 2f

        headerPillRect.set(pillLeft, pillTop, pillRight, pillBottom)
        headerPaint.color = accentColor
        headerPaint.alpha = (viewAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(headerPillRect, pillRadius, pillRadius, headerPaint)

        // 4. Month text inside the floating capsule pill
        val tf = paint.typeface ?: typeface ?: Typeface.DEFAULT_BOLD
        headerTextPaint.typeface = tf
        headerTextPaint.textSize = pillHeight * 0.58f
        headerTextPaint.isFakeBoldText = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            headerTextPaint.letterSpacing = 0.10f
        }

        val headerTextColor = if (ColorUtils.calculateLuminance(accentColor) > 0.5) {
            Color.argb((viewAlpha * 245).toInt().coerceIn(0, 255), 18, 19, 24)
        } else {
            Color.argb((viewAlpha * 245).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        headerTextPaint.color = headerTextColor

        applyFontVariation(headerTextPaint)

        val monthStr = try {
            SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time).uppercase(Locale.getDefault())
        } catch (e: Exception) {
            " "
        }

        val headerMetrics = headerTextPaint.fontMetrics
        headerTextPaint.textAlign = Paint.Align.CENTER
        val headerTextY = headerPillRect.centerY() - (headerMetrics.descent + headerMetrics.ascent) / 2f
        canvas.drawText(monthStr, headerPillRect.centerX(), headerTextY, headerTextPaint)

        // 5. Large Day number centered in the lower expressive region
        dayTextPaint.typeface = tf
        val dayRegionTop = pillBottom
        val dayRegionHeight = bottom - dayRegionTop
        dayTextPaint.textSize = dayRegionHeight * 0.62f
        dayTextPaint.textAlign = Paint.Align.CENTER
        dayTextPaint.isFakeBoldText = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            dayTextPaint.letterSpacing = -0.02f
        }

        val dayTextColor = if (isLightAccent) {
            Color.argb((viewAlpha * 255).toInt().coerceIn(0, 255), 245, 246, 250)
        } else {
            Color.argb((viewAlpha * 255).toInt().coerceIn(0, 255), 24, 25, 30)
        }
        dayTextPaint.color = dayTextColor

        applyFontVariation(dayTextPaint)

        val dayStr = calendar.get(Calendar.DAY_OF_MONTH).toString()
        val dayMetrics = dayTextPaint.fontMetrics
        val dayRegionCenterY = dayRegionTop + dayRegionHeight / 2f
        val dayTextY = dayRegionCenterY - (dayMetrics.descent + dayMetrics.ascent) / 2f
        canvas.drawText(dayStr, cardRect.centerX(), dayTextY, dayTextPaint)

        canvas.restore()
    }

    private fun drawChipPill(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val accentColor = currentTextColor
        val viewAlpha = alpha
        val isLightAccent = ColorUtils.calculateLuminance(accentColor) > 0.5
        val tf = paint.typeface ?: typeface ?: Typeface.DEFAULT_BOLD

        val rawText = text?.toString()?.takeIf { it.isNotBlank() } ?: SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(calendar.time)
        dayTextPaint.typeface = tf
        dayTextPaint.textAlign = Paint.Align.CENTER
        dayTextPaint.isFakeBoldText = true
        dayTextPaint.color = accentColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            dayTextPaint.letterSpacing = 0.04f
        }
        applyFontVariation(dayTextPaint)

        // Fit text within bounds if view width is constrained
        val maxAvailableWidth = (w - 8f).coerceAtLeast(0f)
        var currentTextSize = textSize
        dayTextPaint.textSize = currentTextSize
        var textWidth = dayTextPaint.measureText(rawText)
        var padX = currentTextSize * 0.75f
        var padY = currentTextSize * 0.35f

        if (maxAvailableWidth > 0 && (textWidth + padX * 2f) > maxAvailableWidth) {
            val scale = (maxAvailableWidth / (textWidth + padX * 2f)).coerceIn(0.4f, 1.0f)
            currentTextSize *= scale
            dayTextPaint.textSize = currentTextSize
            padX = currentTextSize * 0.75f
            padY = currentTextSize * 0.35f
            textWidth = dayTextPaint.measureText(rawText)
        }

        val fm = dayTextPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent

        val pillWidth = minOf(w - 8f, textWidth + padX * 2f).coerceAtLeast(0f)
        val pillHeight = minOf(h - 8f, textHeight + padY * 2f).coerceAtLeast(0f)

        val hGrav = gravity and android.view.Gravity.HORIZONTAL_GRAVITY_MASK
        val pillLeft = when (hGrav) {
            android.view.Gravity.START, android.view.Gravity.LEFT -> 4f
            android.view.Gravity.END, android.view.Gravity.RIGHT -> maxOf(4f, w - pillWidth - 4f)
            else -> maxOf(4f, (w - pillWidth) / 2f)
        }
        val pillTop = maxOf(4f, (h - pillHeight) / 2f)
        val pillRight = minOf(w - 4f, pillLeft + pillWidth)
        val pillBottom = minOf(h - 4f, pillTop + pillHeight)
        val pillRadius = maxOf(0f, (pillBottom - pillTop) / 2f)

        cardRect.set(pillLeft, pillTop, pillRight, pillBottom)
        cardPath.reset()
        cardPath.addRoundRect(cardRect, pillRadius, pillRadius, Path.Direction.CW)

        // Container background: subtle translucent surface
        val containerBgColor = if (isLightAccent) {
            Color.argb((viewAlpha * 75).toInt().coerceIn(0, 255), 255, 255, 255)
        } else {
            Color.argb((viewAlpha * 50).toInt().coerceIn(0, 255), 0, 0, 0)
        }

        canvas.save()
        canvas.clipPath(cardPath)

        // 1. Draw pill background container
        cardBackgroundPaint.color = containerBgColor
        canvas.drawRoundRect(cardRect, pillRadius, pillRadius, cardBackgroundPaint)

        // 2. Draw subtle pill outline border
        cardBorderPaint.color = ColorUtils.setAlphaComponent(accentColor, (viewAlpha * 80).toInt().coerceIn(0, 255))
        cardBorderPaint.strokeWidth = 2.5f
        canvas.drawRoundRect(cardRect, pillRadius, pillRadius, cardBorderPaint)

        // 3. Draw formatted text centered in the pill
        val textY = cardRect.centerY() - (fm.descent + fm.ascent) / 2f
        canvas.drawText(rawText, cardRect.centerX(), textY, dayTextPaint)

        canvas.restore()
    }

    private fun drawHeroDay(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val accentColor = currentTextColor
        val viewAlpha = alpha
        val tf = paint.typeface ?: typeface ?: Typeface.DEFAULT_BOLD

        val daySize = textSize * 1.35f
        val sideSize = textSize * 0.50f

        // 1. Large Day Number on Left
        dayTextPaint.typeface = tf
        dayTextPaint.textSize = daySize
        dayTextPaint.textAlign = Paint.Align.LEFT
        dayTextPaint.isFakeBoldText = true
        dayTextPaint.color = accentColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            dayTextPaint.letterSpacing = -0.04f
        }
        applyFontVariation(dayTextPaint)

        val dayStr = String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.DAY_OF_MONTH))
        val dayMetrics = dayTextPaint.fontMetrics
        val startX = paddingLeft.toFloat() + 4f
        val centerY = height / 2f
        val dayY = centerY - (dayMetrics.descent + dayMetrics.ascent) / 2f
        canvas.drawText(dayStr, startX, dayY, dayTextPaint)

        // 2. Stacked Weekday and Month on Right
        val dayWidth = dayTextPaint.measureText(dayStr)
        val gap = textSize * 0.35f
        val sideX = startX + dayWidth + gap

        heroSideTextPaint.typeface = tf
        heroSideTextPaint.textSize = sideSize
        heroSideTextPaint.textAlign = Paint.Align.LEFT
        heroSideTextPaint.isFakeBoldText = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            heroSideTextPaint.letterSpacing = 0.08f
        }
        applyFontVariation(heroSideTextPaint)

        val weekdayStr = try { SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time).uppercase(Locale.getDefault()) } catch (e: Exception) { "" }
        val monthStr = try { SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time).uppercase(Locale.getDefault()) } catch (e: Exception) { "" }

        val sideFm = heroSideTextPaint.fontMetrics
        val sideLineHeight = (sideFm.descent - sideFm.ascent) * 1.05f
        val totalSideHeight = sideLineHeight * 2f

        val topSideY = centerY - (totalSideHeight / 2f) - sideFm.ascent
        val bottomSideY = topSideY + sideLineHeight

        // Weekday with full accent
        heroSideTextPaint.color = accentColor
        canvas.drawText(weekdayStr, sideX, topSideY, heroSideTextPaint)

        // Month with slightly softened opacity for editorial hierarchy
        heroSideTextPaint.color = ColorUtils.setAlphaComponent(accentColor, (viewAlpha * 180).toInt().coerceIn(0, 255))
        canvas.drawText(monthStr, sideX, bottomSideY, heroSideTextPaint)
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
