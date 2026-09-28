package com.nxd1frnt.clockdesk2.background

import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.view.DynamicBackgroundView
import com.nxd1frnt.clockdesk2.utils.MotionUtils

class BackgroundCropController(
    private val dynamicBackgroundView: DynamicBackgroundView,
    private val overlayRoot: View,
    private val backgroundManager: BackgroundManager,
    private val onApply: () -> Unit,
    private val onCancel: () -> Unit
) {
    private var curScale = 1f
    private var curOffsetX = 0f
    private var curOffsetY = 0f
    private var savedScale = 1f
    private var savedOffsetX = 0f
    private var savedOffsetY = 0f

    // Ленивые ссылки на дочерние view оверлея
    private val hintCard   by lazy { overlayRoot.findViewById<View>(R.id.crop_hint_card) }
    private val navCard    by lazy { overlayRoot.findViewById<View>(R.id.crop_nav_card) }
    private val applyBtn   by lazy { overlayRoot.findViewById<View>(R.id.crop_apply_btn) }
    private val cancelBtn  by lazy { overlayRoot.findViewById<View>(R.id.crop_cancel_btn) }
    private val resetBtn   by lazy { overlayRoot.findViewById<View>(R.id.crop_reset_btn) }

    private val DURATION_IN  = 350L
    private val DURATION_OUT = 250L

    // --- Gesture detectors ---

    private val scaleDetector = ScaleGestureDetector(
        dynamicBackgroundView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (curScale * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                val focusX = detector.focusX
                val focusY = detector.focusY
                val vw = dynamicBackgroundView.width.toFloat()
                val vh = dynamicBackgroundView.height.toFloat()
                val centerX = vw / 2f
                val centerY = vh / 2f
                curOffsetX = (focusX - centerX) * (1f - newScale / curScale) + curOffsetX * (newScale / curScale)
                curOffsetY = (focusY - centerY) * (1f - newScale / curScale) + curOffsetY * (newScale / curScale)
                curScale = newScale
                clampOffset()
                applyMatrix()
                return true
            }
        }
    )

    private var lastDragX = 0f
    private var lastDragY = 0f
    private var isDragging = false

    private val touchListener = View.OnTouchListener { _, event ->
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastDragX = event.x; lastDragY = event.y; isDragging = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        curOffsetX += event.x - lastDragX
                        curOffsetY += event.y - lastDragY
                        lastDragX = event.x; lastDragY = event.y
                        clampOffset(); applyMatrix()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
            }
        }
        true
    }

    init {
        overlayRoot.findViewById<View>(R.id.crop_apply_btn).setOnClickListener  { applyAndExit() }
        overlayRoot.findViewById<View>(R.id.crop_cancel_btn).setOnClickListener { cancelAndExit() }
        overlayRoot.findViewById<View>(R.id.crop_reset_btn).setOnClickListener  { resetTransform() }
    }

    // --- Public API ---

    fun enter() {
        savedScale   = backgroundManager.getBgScale()
        savedOffsetX = backgroundManager.getBgOffsetX()
        savedOffsetY = backgroundManager.getBgOffsetY()
        curScale   = savedScale
        curOffsetX = savedOffsetX
        curOffsetY = savedOffsetY

        dynamicBackgroundView.post { applyMatrix() }

        overlayRoot.visibility = View.VISIBLE
        overlayRoot.alpha = 1f

        val enterInterpolator = MotionUtils.EMPHASIZED_DECELERATE
        val springInterpolator = MotionUtils.EXPRESSIVE_SPRING

        hintCard?.apply { alpha = 0f; translationY = -40f; scaleX = 0.94f; scaleY = 0.94f }
        navCard?.apply  { alpha = 0f; translationY = 60f; scaleX = 0.94f; scaleY = 0.94f }
        applyBtn?.apply { alpha = 0f; translationY = -30f }
        cancelBtn?.apply { alpha = 0f; translationY = -30f }
        resetBtn?.apply { alpha = 0f; translationY = -30f }

        applyBtn?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(DURATION_IN)?.setStartDelay(0)?.setInterpolator(enterInterpolator)?.start()
        cancelBtn?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(DURATION_IN)?.setStartDelay(0)?.setInterpolator(enterInterpolator)?.start()
        resetBtn?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(DURATION_IN)?.setStartDelay(80)?.setInterpolator(enterInterpolator)?.start()

        hintCard?.animate()
            ?.alpha(1f)?.translationY(0f)?.scaleX(1f)?.scaleY(1f)
            ?.setDuration(DURATION_IN)?.setStartDelay(40)
            ?.setInterpolator(springInterpolator)?.start()

        navCard?.animate()
            ?.alpha(1f)?.translationY(0f)?.scaleX(1f)?.scaleY(1f)
            ?.setDuration(DURATION_IN)?.setStartDelay(60)
            ?.setInterpolator(springInterpolator)?.start()

        overlayRoot.setOnTouchListener(touchListener)
    }

    fun isActive(): Boolean = overlayRoot.visibility == View.VISIBLE

    fun applyStoredTransform(offsetX: Float, offsetY: Float, scale: Float) {
        curOffsetX = offsetX
        curOffsetY = offsetY
        curScale   = scale

        dynamicBackgroundView.post { applyMatrix() }
    }

    // --- Private ---

    private fun applyMatrix() {
        val dw = dynamicBackgroundView.imageWidth.toFloat()
        val dh = dynamicBackgroundView.imageHeight.toFloat()
        val vw = dynamicBackgroundView.width.toFloat()
        val vh = dynamicBackgroundView.height.toFloat()
        if (vw == 0f || vh == 0f || dw == 0f || dh == 0f) return

        clampOffset()
        dynamicBackgroundView.setCropTransform(curScale, curOffsetX, curOffsetY)
    }

    private fun clampOffset() {
        val dw = dynamicBackgroundView.imageWidth.toFloat()
        val dh = dynamicBackgroundView.imageHeight.toFloat()
        val vw = dynamicBackgroundView.width.toFloat()
        val vh = dynamicBackgroundView.height.toFloat()
        if (vw == 0f || vh == 0f || dw == 0f || dh == 0f) return

        val baseScale = maxOf(vw / dw, vh / dh)
        val scaledW   = dw * baseScale * curScale
        val scaledH   = dh * baseScale * curScale

        val maxX = maxOf(0f, (scaledW - vw) / 2f)
        val maxY = maxOf(0f, (scaledH - vh) / 2f)
        curOffsetX = curOffsetX.coerceIn(-maxX, maxX)
        curOffsetY = curOffsetY.coerceIn(-maxY, maxY)
    }

    private fun resetTransform() {
        val fromScale = curScale
        val fromX = curOffsetX
        val fromY = curOffsetY
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener {
                val t = it.animatedFraction
                curScale   = fromScale + (1f - fromScale) * t
                curOffsetX = fromX * (1f - t)
                curOffsetY = fromY * (1f - t)
                clampOffset(); applyMatrix()
            }
            start()
        }
    }

    private fun applyAndExit() {
        backgroundManager.setBgTransform(curOffsetX, curOffsetY, curScale)
        exitOverlay { onApply() }
    }

    private fun cancelAndExit() {
        curScale   = savedScale
        curOffsetX = savedOffsetX
        curOffsetY = savedOffsetY
        clampOffset(); applyMatrix()
        exitOverlay { onCancel() }
    }

    private fun exitOverlay(onDone: () -> Unit) {
        overlayRoot.setOnTouchListener(null)

        val exitInterpolator = MotionUtils.EMPHASIZED_ACCELERATE

        applyBtn?.animate()?.alpha(0f)?.translationY(-30f)?.setDuration(DURATION_OUT)?.setStartDelay(0)?.setInterpolator(exitInterpolator)?.start()
        cancelBtn?.animate()?.alpha(0f)?.translationY(-30f)?.setDuration(DURATION_OUT)?.setStartDelay(0)?.setInterpolator(exitInterpolator)?.start()
        resetBtn?.animate()?.alpha(0f)?.translationY(-30f)?.setDuration(DURATION_OUT)?.setStartDelay(0)?.setInterpolator(exitInterpolator)?.start()

        hintCard?.animate()
            ?.alpha(0f)?.translationY(-40f)?.scaleX(0.94f)?.scaleY(0.94f)
            ?.setDuration(DURATION_OUT)?.setStartDelay(0)?.setInterpolator(exitInterpolator)?.start()

        navCard?.animate()
            ?.alpha(0f)?.translationY(60f)?.scaleX(0.94f)?.scaleY(0.94f)
            ?.setDuration(DURATION_OUT)?.setStartDelay(0)?.setInterpolator(exitInterpolator)
            ?.withEndAction {
                overlayRoot.visibility = View.GONE

                applyBtn?.animate()?.setStartDelay(0)?.setInterpolator(null)
                cancelBtn?.animate()?.setStartDelay(0)?.setInterpolator(null)
                resetBtn?.animate()?.setStartDelay(0)?.setInterpolator(null)
                hintCard?.animate()?.setStartDelay(0)?.setInterpolator(null)
                navCard?.animate()?.setStartDelay(0)?.setInterpolator(null)

                applyMatrix()

                onDone()
            }?.start()
    }

    companion object {
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 4f
    }
}