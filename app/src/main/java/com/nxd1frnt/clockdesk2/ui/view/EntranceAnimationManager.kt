package com.nxd1frnt.clockdesk2.utils

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.constraintlayout.widget.ConstraintLayout

class EntranceAnimationManager(
    private val rootView: ViewGroup,
    private val widgets: List<View>
) {
    private var hasAnimationPlayed = false

    private val expressiveInterpolator = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)

    private val targetTranslationsY = mutableMapOf<View, Float>()

    private var loaderView: com.google.android.material.loadingindicator.LoadingIndicator? = null

    fun prepareViews() {
        if (hasAnimationPlayed) return

        widgets.forEach { view ->
            view.alpha = 0f
            view.visibility = View.GONE
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

    fun play() {
        if (hasAnimationPlayed) return
        hasAnimationPlayed = true

        val offset = dpToPx(rootView.context, 40f)

        widgets.forEach { view ->
            targetTranslationsY[view] = view.translationY
            view.translationY = view.translationY + offset
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
                .withEndAction { view.animate().setListener(null) }
                .start()

            delay += staggerDelay
        }
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

}