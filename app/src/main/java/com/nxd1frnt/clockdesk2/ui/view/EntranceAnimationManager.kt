package com.nxd1frnt.clockdesk2.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.ui.view.DynamicBackgroundView
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceView

class EntranceAnimationManager(
    private val rootView: ViewGroup,
    private val widgets: List<View>,
    private val turbulenceOverlay: TurbulenceView? = null,
    private val isTurbulenceEnabled: Boolean = true,
    private val dynamicBackgroundView: DynamicBackgroundView? = null,
    private val targetTranslationYProvider: ((View) -> Float)? = null,
    private val themeColor: Int? = null
) {
    private data class Target(val view: View, val alpha: Float, var translationY: Float)
    private val targets = mutableListOf<Target>()
    private val animations = mutableListOf<AnimatorSet>()
    private var prepared = false
    var hasStarted = false
        private set
    var isComplete = false
        private set
    private var completion: (() -> Unit)? = null
    private var loaderView: com.google.android.material.loadingindicator.LoadingIndicator? = null
    private val completionFallback = Runnable { finish() }

    fun prepareViews() {
        if (prepared || isComplete) return
        prepared = true
        val activeTurbulenceColor = themeColor ?: Color.parseColor("#5A7184")
        if (isTurbulenceEnabled) {
            if (dynamicBackgroundView != null) dynamicBackgroundView.playTurbulence(activeTurbulenceColor) {}
            else turbulenceOverlay?.playAnimation(activeTurbulenceColor) {}
        }
        widgets.filter { it.visibility == View.VISIBLE }.forEach { view ->
            targets.add(Target(view, view.alpha, view.translationY))
            view.alpha = 0f
            // INVISIBLE keeps the widgets measurable while their positions are restored.
            view.visibility = View.INVISIBLE
        }
        loaderView = com.google.android.material.loadingindicator.LoadingIndicator(rootView.context).apply {
            layoutParams = ConstraintLayout.LayoutParams(dpToPx(context, 48f).toInt(), dpToPx(context, 48f).toInt()).apply {
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            themeColor?.let { setIndicatorColor(it) }
            alpha = 0f
            id = View.generateViewId()
        }.also { loader ->
            rootView.addView(loader)
            loader.animate().alpha(1f).setStartDelay(0L).setDuration(200L)
                .setInterpolator(MotionUtils.EMPHASIZED_DECELERATE).start()
        }
    }

    fun play(onAnimationEnd: (() -> Unit)? = null) {
        if (isComplete) {
            onAnimationEnd?.invoke()
            return
        }
        if (hasStarted) return
        if (!prepared) prepareViews()
        hasStarted = true
        completion = onAnimationEnd
        if (targets.isEmpty()) {
            finish()
            return
        }
        loaderView?.animate()?.alpha(0f)?.scaleX(0.7f)?.scaleY(0.7f)
            ?.setStartDelay(0L)?.setDuration(220L)?.setInterpolator(MotionUtils.EMPHASIZED_ACCELERATE)
            ?.withEndAction { removeLoader() }?.start()

        val offset = dpToPx(rootView.context, 44f)
        var completed = 0
        targets.forEachIndexed { index, target ->
            val view = target.view
            val restY = targetTranslationYProvider?.invoke(view) ?: view.translationY
            target.translationY = restY
            // A font reload or layout pass may have changed alpha since preparation.
            view.alpha = 0f
            view.scaleX = 0.85f
            view.scaleY = 0.85f
            view.translationY = restY + offset
            view.visibility = View.VISIBLE
            // WidgetMover uses ViewPropertyAnimator too. Independent animators keep
            // its position restoration from cancelling the entrance animation.
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, View.ALPHA, 0f, target.alpha),
                    ObjectAnimator.ofFloat(view, View.SCALE_X, 0.85f, 1f),
                    ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.85f, 1f),
                    ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, restY + offset, restY)
                )
                duration = 650L
                startDelay = 40L + index * 70L
                interpolator = MotionUtils.EXPRESSIVE_SPRING
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isComplete && ++completed == targets.size) finish()
                    }
                })
                animations.add(this)
                start()
            }
        }
        rootView.postDelayed(completionFallback, 40L + (targets.size - 1) * 70L + 650L + 80L)
    }

    /** Finish before changing desks so no old widget animation can affect the new layout. */
    fun finish() {
        if (isComplete) return
        isComplete = true
        rootView.removeCallbacks(completionFallback)
        animations.forEach { it.cancel() }
        animations.clear()
        targets.forEach { target ->
            target.view.alpha = target.alpha
            target.view.scaleX = 1f
            target.view.scaleY = 1f
            target.view.translationY = if (hasStarted) target.translationY
                else targetTranslationYProvider?.invoke(target.view) ?: target.translationY
            target.view.visibility = View.VISIBLE
        }
        removeLoader()
        val callback = completion
        completion = null
        callback?.invoke()
    }

    fun destroy() {
        completion = null
        finish()
    }

    private fun removeLoader() {
        loaderView?.let { loader ->
            loader.animate().cancel()
            rootView.removeView(loader)
        }
        loaderView = null
    }

    private fun dpToPx(context: Context, dp: Float) = dp * context.resources.displayMetrics.density
}
