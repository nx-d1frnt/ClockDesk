package com.nxd1frnt.clockdesk2.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated squiggly progress bar inspired by Android 13+ System Media Player.
 * Draws played progress as a sine wave that ripples during active playback,
 * and the unplayed section as a subtle straight line.
 */
class SquigglyProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density

    // Styling dimensions
    private val strokeWidthPx = 3.5f * density
    private val maxWaveAmplitudePx = 3f * density
    private val waveLengthPx = 28f * density
    private val thumbWidthPx = 3.5f * density
    private val thumbHeightPx = 13f * density
    private val thumbCornerRadiusPx = thumbWidthPx / 2f

    // State
    var max: Int = 1000
        set(value) {
            field = value.coerceAtLeast(1)
            invalidate()
        }

    var progress: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, max)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    var isPlaying: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                animateAmplitudeTransition(targetAmplitude = if (value) maxWaveAmplitudePx else 0f)
                if (value) {
                    startWaveAnimation()
                } else {
                    stopWaveAnimation()
                }
            }
        }

    private var waveColor: Int = Color.WHITE
    private var trackColor: Int = Color.argb(80, 255, 255, 255)

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = strokeWidthPx
        color = waveColor
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokeWidthPx
        color = trackColor
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = waveColor
    }

    private val wavePath = Path()

    // Animation variables
    private var phaseOffset = 0f
    private var currentAmplitudePx = 0f
    private var amplitudeAnimator: ValueAnimator? = null
    private var waveAnimator: ValueAnimator? = null

    init {
        // Initialize amplitude based on default state
        currentAmplitudePx = if (isPlaying) maxWaveAmplitudePx else 0f
    }

    fun setWaveColor(color: Int) {
        if (waveColor != color) {
            waveColor = color
            wavePaint.color = color
            thumbPaint.color = color
            invalidate()
        }
    }

    fun setTrackColor(color: Int) {
        if (trackColor != color) {
            trackColor = color
            trackPaint.color = color
            invalidate()
        }
    }

    private fun startWaveAnimation() {
        if (!isShown || !isAttachedToWindow || windowVisibility != VISIBLE) return
        if (waveAnimator?.isRunning == true) return
        waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                phaseOffset = fraction * (2f * PI.toFloat())
                invalidate()
            }
            start()
        }
    }

    private fun stopWaveAnimation() {
        waveAnimator?.cancel()
        waveAnimator = null
        invalidate()
    }

    private fun animateAmplitudeTransition(targetAmplitude: Float) {
        amplitudeAnimator?.cancel()
        amplitudeAnimator = ValueAnimator.ofFloat(currentAmplitudePx, targetAmplitude).apply {
            duration = 350L
            addUpdateListener {
                currentAmplitudePx = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isPlaying && isShown) {
            startWaveAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopWaveAnimation()
        amplitudeAnimator?.cancel()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isShown && isPlaying) {
            startWaveAnimation()
        } else {
            stopWaveAnimation()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isPlaying && isShown) startWaveAnimation()
        else stopWaveAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (maxOf(maxWaveAmplitudePx * 2f + strokeWidthPx, thumbHeightPx) + (4f * density)).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        val width = resolveSize(0, widthMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Inset by half-stroke / thumb width so round caps and thumb are never clipped at view edges
        val horizontalInset = maxOf(strokeWidthPx / 2f, thumbWidthPx / 2f) + (1f * density)
        val startX = paddingLeft + horizontalInset
        val endX = width - paddingRight - horizontalInset
        val availableWidth = (endX - startX).coerceAtLeast(0f)
        if (availableWidth <= 0f) return

        val centerY = height / 2f
        val progressRatio = (progress.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        val progressX = startX + (availableWidth * progressRatio)

        // 1. Draw unplayed straight line (from progressX to endX)
        if (progressX < endX) {
            canvas.drawLine(progressX, centerY, endX, centerY, trackPaint)
        }

        // 2. Draw played wave (from startX to progressX)
        if (progressX > startX) {
            wavePath.reset()

            if (currentAmplitudePx > 0.1f) {
                // Wave starts directly at startY without any point at centerY, eliminating the dot behind the wave
                val startAngle = phaseOffset
                val startY = centerY + sin(startAngle) * currentAmplitudePx
                wavePath.moveTo(startX, startY)

                val stepPx = 2f * density
                var x = startX + stepPx
                while (x <= progressX) {
                    val angle = ((x - startX) / waveLengthPx) * (2f * PI.toFloat()) + phaseOffset
                    val y = centerY + sin(angle) * currentAmplitudePx
                    wavePath.lineTo(x, y)
                    x += stepPx
                }

                // Connect to exact progress point into the vertical pill thumb
                val endAngle = ((progressX - startX) / waveLengthPx) * (2f * PI.toFloat()) + phaseOffset
                val endY = centerY + sin(endAngle) * currentAmplitudePx
                wavePath.lineTo(progressX, endY)
                canvas.drawPath(wavePath, wavePaint)
            } else {
                // Flat line when amplitude is 0 (paused)
                wavePath.moveTo(startX, centerY)
                wavePath.lineTo(progressX, centerY)
                canvas.drawPath(wavePath, wavePaint)
            }
        }

        // 3. Draw vertical pill thumb (matching Android 13+ QPR / Android 16 scrubber)
        canvas.drawRoundRect(
            progressX - thumbWidthPx / 2f,
            centerY - thumbHeightPx / 2f,
            progressX + thumbWidthPx / 2f,
            centerY + thumbHeightPx / 2f,
            thumbCornerRadiusPx,
            thumbCornerRadiusPx,
            thumbPaint
        )
    }
}
