package com.nxd1frnt.clockdesk2.ui.touch

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration

/** Observes dispatch, which still receives events when a child disallows interception. */
internal class VerticalScrollGesture(context: Context) {
    private val direction = ScrollGestureDirection(ViewConfiguration.get(context).scaledTouchSlop)
    private var pointerId = MotionEvent.INVALID_POINTER_ID

    val isHorizontal: Boolean
        get() = direction.axis == ScrollGestureDirection.Axis.HORIZONTAL
    val isVertical: Boolean
        get() = direction.axis == ScrollGestureDirection.Axis.VERTICAL

    fun observe(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                direction.start(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index >= 0) direction.move(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointerId) {
                    val index = if (event.actionIndex == 0) 1 else 0
                    pointerId = event.getPointerId(index)
                    direction.rebase(event.getX(index), event.getY(index))
                }
            }
        }
    }
}
