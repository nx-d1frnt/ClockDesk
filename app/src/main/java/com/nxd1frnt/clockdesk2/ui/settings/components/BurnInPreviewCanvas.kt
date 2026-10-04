package com.nxd1frnt.clockdesk2.ui.settings.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.google.android.material.color.MaterialColors
import com.nxd1frnt.clockdesk2.R
import java.util.Random

/**
 * Interactive preview canvas for Burn-In Protection.
 * Shows center crosshair, maximum shift bounding boundary, and an animated
 * mini-widget that periodically shifts to demonstrate pixel displacement.
 */
class BurnInPreviewCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    var shiftDistanceDp: Float = 10f
        set(value) {
            field = value.coerceIn(2f, 30f)
            updateShiftBounds()
            invalidate()
        }

    private var maxShiftPx: Float = 10f * density

    // Paint objects
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
    }

    private val originDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val boundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
    }

    private val boundaryFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = 14f * density
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 9f * density
    }

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Geometry
    private val badgeWidth = 92f * density
    private val badgeHeight = 44f * density
    private val badgeCornerRadius = 14f * density
    private val badgeRect = RectF()
    private val boundaryRect = RectF()

    // Animation state
    private var currentShiftX = 0f
    private var currentShiftY = 0f
    private var targetShiftX = 0f
    private var targetShiftY = 0f
    private var startShiftX = 0f
    private var startShiftY = 0f

    private val recentPositions = mutableListOf<Pair<Float, Float>>()
    private val random = Random()
    private val handler = Handler(Looper.getMainLooper())
    private var shiftAnimator: ValueAnimator? = null
    private var isSimulating = false

    private val shiftRunnable = object : Runnable {
        override fun run() {
            startNextShiftAnimation()
            handler.postDelayed(this, 1800L)
        }
    }

    init {
        updateColors()
        updateShiftBounds()
    }

    private fun updateColors() {
        val primaryColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.CYAN)
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val surfaceContainerHigh = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.DKGRAY)

        crosshairPaint.color = Color.argb(40, Color.red(onSurface), Color.green(onSurface), Color.blue(onSurface))
        originDotPaint.color = Color.argb(90, Color.red(onSurface), Color.green(onSurface), Color.blue(onSurface))

        boundaryPaint.color = Color.argb(120, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        boundaryFillPaint.color = Color.argb(22, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))

        badgePaint.color = surfaceContainerHigh
        badgeStrokePaint.color = Color.argb(80, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))

        textPaint.color = onSurface
        subTextPaint.color = Color.argb(170, Color.red(onSurface), Color.green(onSurface), Color.blue(onSurface))

        trailPaint.color = Color.argb(60, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
    }

    private fun updateShiftBounds() {
        maxShiftPx = shiftDistanceDp * density
    }

    private fun startNextShiftAnimation() {
        if (width <= 0 || height <= 0) return

        startShiftX = currentShiftX
        startShiftY = currentShiftY

        // Record old point in recent positions trail (keep last 5)
        recentPositions.add(Pair(startShiftX, startShiftY))
        if (recentPositions.size > 5) {
            recentPositions.removeAt(0)
        }

        // Pick next random displacement within [-maxShiftPx, maxShiftPx]
        val range = (maxShiftPx * 2).toInt()
        targetShiftX = if (range > 0) (random.nextInt(range + 1) - maxShiftPx) else 0f
        targetShiftY = if (range > 0) (random.nextInt(range + 1) - maxShiftPx) else 0f

        shiftAnimator?.cancel()
        shiftAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 750L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                currentShiftX = startShiftX + (targetShiftX - startShiftX) * fraction
                currentShiftY = startShiftY + (targetShiftY - startShiftY) * fraction
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateColors()
        isSimulating = true
        handler.removeCallbacks(shiftRunnable)
        handler.postDelayed(shiftRunnable, 600L)
    }

    override fun onDetachedFromWindow() {
        isSimulating = false
        handler.removeCallbacks(shiftRunnable)
        shiftAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateColors()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        // 1. Draw center crosshair & origin dot
        canvas.drawLine(16f * density, cy, width - 16f * density, cy, crosshairPaint)
        canvas.drawLine(cx, 16f * density, cx, height - 16f * density, crosshairPaint)
        canvas.drawCircle(cx, cy, 3f * density, originDotPaint)

        // 2. Draw maximum shift boundary frame
        val boundHalfW = (badgeWidth / 2f) + maxShiftPx
        val boundHalfH = (badgeHeight / 2f) + maxShiftPx
        boundaryRect.set(cx - boundHalfW, cy - boundHalfH, cx + boundHalfW, cy + boundHalfH)
        canvas.drawRoundRect(boundaryRect, badgeCornerRadius + 4f * density, badgeCornerRadius + 4f * density, boundaryFillPaint)
        canvas.drawRoundRect(boundaryRect, badgeCornerRadius + 4f * density, badgeCornerRadius + 4f * density, boundaryPaint)

        // 3. Draw past trail dots showing movement coverage
        for (i in recentPositions.indices) {
            val (px, py) = recentPositions[i]
            val alphaFraction = (i + 1).toFloat() / recentPositions.size
            trailPaint.alpha = (alphaFraction * 90).toInt()
            canvas.drawCircle(cx + px, cy + py, 3f * density, trailPaint)
        }

        // 4. Draw moving badge widget at (cx + currentShiftX, cy + currentShiftY)
        val badgeCenterX = cx + currentShiftX
        val badgeCenterY = cy + currentShiftY

        badgeRect.set(
            badgeCenterX - badgeWidth / 2f,
            badgeCenterY - badgeHeight / 2f,
            badgeCenterX + badgeWidth / 2f,
            badgeCenterY + badgeHeight / 2f
        )

        canvas.drawRoundRect(badgeRect, badgeCornerRadius, badgeCornerRadius, badgePaint)
        canvas.drawRoundRect(badgeRect, badgeCornerRadius, badgeCornerRadius, badgeStrokePaint)

        // 5. Text inside badge: "12:45" and tiny "OLED SHIFT"
        val textY = badgeCenterY + (textPaint.textSize * 0.1f)
        canvas.drawText("12:45", badgeCenterX, textY, textPaint)

        val subTextY = badgeCenterY + (badgeHeight / 2f) - (6f * density)
        val dxDisplay = currentShiftX / density
        val dyDisplay = currentShiftY / density
        val offsetLabel = String.format("%+.0fdp, %+.0fdp", dxDisplay, dyDisplay)
        canvas.drawText(offsetLabel, badgeCenterX, subTextY, subTextPaint)
    }
}
