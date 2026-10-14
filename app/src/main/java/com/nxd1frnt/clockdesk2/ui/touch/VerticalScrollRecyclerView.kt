package com.nxd1frnt.clockdesk2.ui.touch

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

/** Lets vertical scrolling start over interactive children such as sliders. */
class VerticalScrollRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.recyclerview.R.attr.recyclerViewStyle
) : RecyclerView(context, attrs, defStyleAttr) {
    private val gesture = VerticalScrollGesture(context)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gesture.observe(event)
        // Clear the child's veto before ViewGroup decides whether to call onInterceptTouchEvent.
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
