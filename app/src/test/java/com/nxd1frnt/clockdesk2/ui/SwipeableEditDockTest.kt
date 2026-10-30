package com.nxd1frnt.clockdesk2.ui

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SwipeableEditDockTest {
    @Test
    fun downwardSwipeCollapsesButCancelAndHorizontalMovementDoNot() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val dock = SwipeableEditDock(activity)
        activity.setContentView(FrameLayout(activity).apply { addView(dock) })
        var collapses = 0
        dock.onCollapse = { collapses++ }
        fun event(action: Int, x: Float, y: Float) = MotionEvent.obtain(0, 100, action, x, y, 0)
        fun intercept(action: Int, x: Float, y: Float): Boolean {
            val e = event(action, x, y)
            return try { dock.onInterceptTouchEvent(e) } finally { e.recycle() }
        }
        fun touch(action: Int, y: Float = 500f) {
            val e = event(action, 0f, y)
            try { dock.onTouchEvent(e) } finally { e.recycle() }
        }
        intercept(MotionEvent.ACTION_DOWN, 0f, 0f)
        assertFalse(intercept(MotionEvent.ACTION_MOVE, 500f, 10f))
        assertTrue(intercept(MotionEvent.ACTION_MOVE, 0f, 500f))
        assertTrue("The dock follows the finger before release", dock.translationY > 0f)
        touch(MotionEvent.ACTION_MOVE, 40f)
        val shortDragTranslation = dock.translationY
        touch(MotionEvent.ACTION_MOVE, 60f)
        assertTrue("Moving farther moves the dock farther", dock.translationY > shortDragTranslation)
        touch(MotionEvent.ACTION_CANCEL)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(0f, dock.translationY, 0.01f)
        assertEquals(0, collapses)
        intercept(MotionEvent.ACTION_DOWN, 0f, 0f)
        assertTrue(intercept(MotionEvent.ACTION_MOVE, 0f, 500f))
        touch(MotionEvent.ACTION_UP, 0f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals("Dragging back before release keeps actions open", 0, collapses)
        assertEquals(0f, dock.translationY, 0.01f)
        intercept(MotionEvent.ACTION_DOWN, 0f, 0f)
        assertTrue(intercept(MotionEvent.ACTION_MOVE, 0f, 500f))
        touch(MotionEvent.ACTION_UP)
        assertEquals(1, collapses)
        dock.collapsed = true
        intercept(MotionEvent.ACTION_DOWN, 0f, 0f)
        assertFalse(intercept(MotionEvent.ACTION_MOVE, 0f, 500f))
    }
}
