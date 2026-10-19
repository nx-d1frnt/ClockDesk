package com.nxd1frnt.clockdesk2.ui.touch

import android.os.SystemClock
import android.view.MotionEvent

/** Keeps the gesture that opens edit mode from clicking a widget on release. */
internal class EditModeTouchGuard {
    private var gestureActive = false
    private var consumeUntilNextDown = false
    private var downTime = 0L

    fun shouldConsume(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureActive = true
                consumeUntilNextDown = false
                downTime = event.downTime
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> gestureActive = false
        }
        return consumeUntilNextDown
    }

    fun cancelCurrentGesture(dispatchCancel: (MotionEvent) -> Unit) {
        if (!gestureActive) return
        gestureActive = false
        consumeUntilNextDown = true
        val cancel = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0f, 0f, 0
        )
        try {
            dispatchCancel(cancel)
        } finally {
            cancel.recycle()
        }
    }
}
