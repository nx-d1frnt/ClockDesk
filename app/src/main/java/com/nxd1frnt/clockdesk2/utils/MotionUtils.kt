package com.nxd1frnt.clockdesk2.utils

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * Material 3 Expressive motion tokens and easing curves.
 * Conforms to the official Material Design 3 Expressive motion specifications.
 */
object MotionUtils {

    /**
     * Emphasized Decelerate (Incoming / Entering):
     * Rapid acceleration with an extended, natural deceleration curve.
     * Use for all incoming elements, entering widgets, cards, and modal sheets.
     */
    val EMPHASIZED_DECELERATE: Interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)

    /**
     * Emphasized Accelerate (Outgoing / Exiting):
     * Starts with subtle motion and quickly accelerates elements off-screen or to zero opacity.
     * Use for dismissals, exiting sheets, and hiding toolbars.
     */
    val EMPHASIZED_ACCELERATE: Interpolator = PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f)

    /**
     * Emphasized (Standard / Spatial / Scaling within viewport):
     * Symmetrical, expressive standard curve used when elements move or resize on screen.
     */
    val EMPHASIZED: Interpolator = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)

    /**
     * Expressive Spring / Tactile Rebound:
     * Gentle, organic spring overshoot for elevated cards, bottom action bars, and floating docks.
     */
    val EXPRESSIVE_SPRING: Interpolator = PathInterpolator(0.34f, 1.25f, 0.64f, 1.0f)
}
