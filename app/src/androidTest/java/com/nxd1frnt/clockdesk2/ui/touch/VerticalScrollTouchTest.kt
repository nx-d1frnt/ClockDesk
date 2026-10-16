package com.nxd1frnt.clockdesk2.ui.touch

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VerticalScrollTouchTest {
    @Test fun verticalSwipeCancelsBlockingChildAndScrollsBothContainers() = onMain {
        for (recycler in listOf(false, true)) {
            val (container, child) = createContainer(recycler)
            swipe(container, 200f, 80f)
            assertTrue("Child must receive CANCEL (recycler=$recycler)", child.cancelled)
            val scrolled = if (container is RecyclerView) {
                container.computeVerticalScrollOffset() > 0
            } else container.scrollY > 0
            assertTrue("Vertical swipe must scroll (recycler=$recycler)", scrolled)
        }
    }

    @Test fun horizontalSwipeAndTapRemainWithChildInBothContainers() = onMain {
        for (recycler in listOf(false, true)) {
            for (endX in listOf(200f, 320f)) {
                val (container, child) = createContainer(recycler)
                swipe(container, endX, 200f)
                assertFalse(child.cancelled)
                assertTrue(child.released)
                val scrolled = if (container is RecyclerView) {
                    container.computeVerticalScrollOffset() > 0
                } else container.scrollY > 0
                assertFalse(scrolled)
            }
        }
    }

    private fun createContainer(recycler: Boolean): Pair<ViewGroup, BlockingChild> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val child = BlockingChild(context)
        val container = if (recycler) {
            VerticalScrollRecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun getItemCount() = 1
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                        child.layoutParams = RecyclerView.LayoutParams(400, 1600)
                        return object : RecyclerView.ViewHolder(child) {}
                    }
                    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
                }
            }
        } else {
            VerticalNestedScrollView(context).apply {
                addView(child, ViewGroup.LayoutParams(400, 1600))
            }
        }
        val spec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        container.measure(spec, spec)
        container.layout(0, 0, 400, 400)
        return container to child
    }

    private fun swipe(container: ViewGroup, endX: Float, endY: Float) {
        val downTime = SystemClock.uptimeMillis()
        val steps = listOf(
            Triple(MotionEvent.ACTION_DOWN, 200f, 200f),
            Triple(MotionEvent.ACTION_MOVE, (200f + endX) / 2, (200f + endY) / 2),
            Triple(MotionEvent.ACTION_MOVE, endX, endY),
            Triple(MotionEvent.ACTION_UP, endX, endY)
        )
        steps.forEachIndexed { index, (action, x, y) ->
            val event = MotionEvent.obtain(downTime, downTime + index * 16, action, x, y, 0)
            try {
                container.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private class BlockingChild(context: Context) : View(context) {
        var cancelled = false
        var released = false

        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Reproduce components that veto parent interception immediately on DOWN.
            parent.requestDisallowInterceptTouchEvent(true)
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) cancelled = true
            if (event.actionMasked == MotionEvent.ACTION_UP) released = true
            return true
        }
    }
}
