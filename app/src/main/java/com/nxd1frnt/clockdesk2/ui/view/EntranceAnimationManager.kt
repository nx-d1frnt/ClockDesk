package com.nxd1frnt.clockdesk2.utils

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.ui.view.DynamicBackgroundView
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceView

class EntranceAnimationManager(
    private val rootView: ViewGroup,
    private val widgets: List<View>,
    private val turbulenceOverlay: TurbulenceView? = null,
    private val isTurbulenceEnabled: Boolean = true,
    private val dynamicBackgroundView: DynamicBackgroundView? = null,
    private val targetTranslationYProvider: ((View) -> Float)? = null
) {
    private var hasAnimationPlayed = false

    private val expressiveInterpolator = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)

    private val targetTranslationsY = mutableMapOf<View, Float>()

    private var loaderView: com.google.android.material.loadingindicator.LoadingIndicator? = null

    fun prepareViews() {
        if (hasAnimationPlayed) return

        if (isTurbulenceEnabled) {
            if (dynamicBackgroundView != null) {
                dynamicBackgroundView.playTurbulence(Color.parseColor("#5A7184")) {}
            } else {
                turbulenceOverlay?.playAnimation(Color.parseColor("#5A7184")) {}
            }
        }

        widgets.forEach { view ->
            view.alpha = 0f
            view.visibility = View.INVISIBLE
        }

        loaderView = com.google.android.material.loadingindicator.LoadingIndicator(rootView.context).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                dpToPx(context, 48f).toInt(),
                dpToPx(context, 48f).toInt()
            ).apply {
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            alpha = 0f
            id = View.generateViewId()
        }

        rootView.addView(loaderView)

        loaderView?.animate()
            ?.alpha(1f)
            ?.setDuration(200L)
            ?.start()
    }

    fun play(onAnimationEnd: (() -> Unit)? = null) {
        if (hasAnimationPlayed) {
            onAnimationEnd?.invoke()
            return
        }
        hasAnimationPlayed = true

        if (widgets.isEmpty()) {
            loaderView?.let { rootView.removeView(it) }
            loaderView = null
            onAnimationEnd?.invoke()
            return
        }

        val offset = dpToPx(rootView.context, 40f)

        widgets.forEach { view ->
            val targetY = targetTranslationYProvider?.invoke(view) ?: view.translationY
            targetTranslationsY[view] = targetY
            view.translationY = targetY + offset
            view.scaleX = 0.85f
            view.scaleY = 0.85f
            view.visibility = View.VISIBLE
        }

        loaderView?.animate()
            ?.alpha(0f)
            ?.scaleX(0.5f)
            ?.scaleY(0.5f)
            ?.setDuration(400L)
            ?.setInterpolator(PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f))
            ?.withEndAction {
                rootView.removeView(loaderView)
                loaderView = null
            }
            ?.start()

        var delay = 200L
        val staggerDelay = 100L
        val animationDuration = 900L

        var completedCount = 0
        var hasNotifiedEnd = false
        fun notifyEnd() {
            if (!hasNotifiedEnd) {
                hasNotifiedEnd = true
                onAnimationEnd?.invoke()
            }
        }

        widgets.forEach { view ->
            val targetY = targetTranslationsY[view] ?: 0f

            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(targetY)
                .setDuration(animationDuration)
                .setStartDelay(delay)
                .setInterpolator(expressiveInterpolator)
                .withEndAction {
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.alpha = 1f
                    view.animate().setListener(null)
                    completedCount++
                    if (completedCount >= widgets.size) {
                        notifyEnd()
                    }
                }
                .start()

            delay += staggerDelay
        }

        val maxDuration = delay + animationDuration + 100L
        rootView.postDelayed({
            notifyEnd()
        }, maxDuration)
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

}