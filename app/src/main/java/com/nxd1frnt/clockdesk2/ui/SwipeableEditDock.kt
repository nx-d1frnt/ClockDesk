package com.nxd1frnt.clockdesk2.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.google.android.material.card.MaterialCardView
import com.nxd1frnt.clockdesk2.utils.MotionUtils
import kotlin.math.abs

/** Intercepts downward drags while preserving ordinary clicks on dock actions. */
class SwipeableEditDock @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialCardView(context, attrs) {
    var onCollapse: (() -> Unit)? = null
    var collapsed = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private var dragCanceled = false

    private fun dragDistance(event: MotionEvent) = (event.rawY - startY - slop).coerceAtLeast(0f)

    private fun followFinger(event: MotionEvent) {
        // Screen coordinates remain stable while the dock itself moves.
        translationY = dragDistance(event).coerceAtMost(96f * resources.displayMetrics.density)
    }

    private fun settle() {
        animate().translationY(0f)
            .setDuration(250L)
            .setInterpolator(MotionUtils.EXPRESSIVE_SPRING)
            .start()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                dragging = false
                dragCanceled = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - startY
                if (!collapsed && event.pointerCount == 1 && dy > slop && dy > abs(event.rawX - startX)) {
                    animate().cancel()
                    dragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    followFinger(event)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (!dragCanceled) followFinger(event)
            MotionEvent.ACTION_POINTER_DOWN -> {
                dragCanceled = true
                settle()
            }
            MotionEvent.ACTION_UP -> {
                dragging = false
                if (!dragCanceled && dragDistance(event) > maxOf(slop * 2f, 48f * resources.displayMetrics.density)) {
                    onCollapse?.invoke()
                }
                settle()
                parent.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                settle()
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
