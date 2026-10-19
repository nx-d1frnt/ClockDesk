package com.nxd1frnt.clockdesk2.ui.touch

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class EditModeTouchGuardTest {
    @Test
    fun releasingLongPressDoesNotOpenCustomizationButNextTapDoes() = withFixture { f ->
        f.send(MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis((ViewConfiguration.getTapTimeout() + ViewConfiguration.getLongPressTimeout() + 1).toLong())
        )
        assertTrue("Long press must enter edit mode", f.isEditMode)
        assertFalse("Entering edit mode must cancel the pressed widget", f.widget.isPressed)
        f.send(MotionEvent.ACTION_MOVE)
        f.send(MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, f.customizationClicks)
        assertEquals(0, f.normalClicks)

        f.send(MotionEvent.ACTION_DOWN)
        f.send(MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("A separate tap must still open customization", 1, f.customizationClicks)
    }

    @Test
    fun enteringEditModeWithoutAnActiveGesturePreservesFirstTap() = withFixture { f ->
        f.enterEditMode()
        f.send(MotionEvent.ACTION_DOWN)
        f.send(MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, f.customizationClicks)
    }

    @Test
    fun cancellationAfterEnteringEditModeDoesNotBlockNextGesture() = withFixture { f ->
        f.send(MotionEvent.ACTION_DOWN)
        f.enterEditMode()
        f.send(MotionEvent.ACTION_CANCEL)
        assertFalse(f.widget.isPressed)
        f.send(MotionEvent.ACTION_DOWN)
        f.send(MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, f.customizationClicks)
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            block(Fixture(controller.get()))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private class Fixture(activity: Activity) {
        private val guard = EditModeTouchGuard()
        private val root = FrameLayout(activity)
        val widget = View(activity)
        var isEditMode = false
        var customizationClicks = 0
        var normalClicks = 0
        private var downTime = 0L
        private val detector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (!isEditMode) enterEditMode()
            }
        })

        init {
            root.addView(widget, FrameLayout.LayoutParams(200, 200))
            activity.setContentView(root)
            shadowOf(Looper.getMainLooper()).idle()
            widget.setOnClickListener {
                if (isEditMode) customizationClicks++ else normalClicks++
            }
        }

        fun enterEditMode() {
            guard.cancelCurrentGesture { cancel ->
                detector.onTouchEvent(cancel)
                root.dispatchTouchEvent(cancel)
            }
            isEditMode = true
        }

        fun send(action: Int) {
            val now = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) downTime = now
            val event = MotionEvent.obtain(downTime, now, action, 50f, 50f, 0)
            try {
                if (!guard.shouldConsume(event)) {
                    detector.onTouchEvent(event)
                    root.dispatchTouchEvent(event)
                }
            } finally {
                event.recycle()
            }
        }
    }
}
