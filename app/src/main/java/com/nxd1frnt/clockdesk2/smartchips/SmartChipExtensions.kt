package com.nxd1frnt.clockdesk2.smartchips

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet

/**
 * Updates TextView text with a subtle fade transition only when the text genuinely changes.
 * Avoids cancelling running marquee animations when the text is identical.
 */
fun TextView.setTextWithFade(newText: String, animateBounds: Boolean = true) {
    if (this.text.toString() == newText) {
        if (!this.isSelected) this.isSelected = true
        return
    }

    this.animate().cancel()

    if (this.text.isNullOrEmpty() || this.visibility != View.VISIBLE) {
        this.text = newText
        if (!this.isSelected) this.isSelected = true
        return
    }

    val parentLayout = (this.parent as? ViewGroup)

    this.animate()
        .alpha(0f)
        .setDuration(100)
        .setInterpolator(FastOutSlowInInterpolator())
        .withEndAction {
            if (animateBounds && parentLayout != null) {
                val boundsTransition = TransitionSet().apply {
                    ordering = TransitionSet.ORDERING_TOGETHER
                    duration = 350L
                    interpolator = PathInterpolatorCompat.create(0.2f, 0f, 0f, 1f)
                    addTransition(ChangeBounds().apply {
                        resizeClip = false
                    })
                }
                TransitionManager.beginDelayedTransition(parentLayout, boundsTransition)
            }

            this.text = newText
            this.isSelected = true

            this.animate()
                .alpha(1f)
                .setDuration(150)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
        .start()
}
