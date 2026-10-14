package com.nxd1frnt.clockdesk2.ui.touch

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

/** Direction-aware scrolling for panels containing sliders and horizontal lists. */
class VerticalNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.scrollViewStyle
) : NestedScrollView(context, attrs, defStyleAttr) {
    private val gesture = VerticalScrollGesture(context)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gesture.observe(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && gesture.isVertical) {
            super.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_MOVE && gesture.isHorizontal) return false
        return super.onInterceptTouchEvent(event)
    }
}
