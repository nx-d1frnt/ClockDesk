package com.nxd1frnt.clockdesk2.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w1000dp-h1000dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetMoverTest {
    @Test fun dragFollowsFingerAtDesktopScalesWithHighlightedWidget() {
        for (scale in listOf(1f, 0.92f, 0.85f, 0.65f)) {
            withFixture { fixture ->
                fixture.desktop.scaleX = scale
                fixture.desktop.scaleY = scale
                fixture.desktop.translationX = 40f
                fixture.desktop.translationY = 30f
                fixture.send(MotionEvent.ACTION_DOWN, 150f, 150f)
                fixture.widget.animate().cancel()
                fixture.widget.scaleX = 1.05f
                fixture.widget.scaleY = 1.05f
                fixture.send(MotionEvent.ACTION_MOVE, 200f, 170f)
                fixture.send(MotionEvent.ACTION_MOVE, 250f, 190f)

                assertEquals("X at scale $scale", 200f, fixture.widget.translationX, 0.01f)
                assertEquals("Y at scale $scale", 140f, fixture.widget.translationY, 0.01f)

                fixture.send(MotionEvent.ACTION_UP, 250f, 190f)
                assertEquals(200f, fixture.prefs.getFloat("time_text_x", -1f), 0.01f)
                assertEquals(140f, fixture.prefs.getFloat("time_text_y", -1f), 0.01f)
                assertEquals(0, fixture.clicks)
            }
        }
    }

    @Test fun oversizedWidgetCanBeDraggedWithoutInvalidBounds() {
        withFixture(oversized = true) { fixture ->
            assertTrue(fixture.widget.width > fixture.desktop.width)
            assertTrue(fixture.widget.height > fixture.desktop.height)
            fixture.send(MotionEvent.ACTION_DOWN, 150f, 150f)
            fixture.send(MotionEvent.ACTION_MOVE, 200f, 200f)
            fixture.send(MotionEvent.ACTION_MOVE, 250f, 250f)
            fixture.send(MotionEvent.ACTION_UP, 250f, 250f)
            assertEquals(0f, fixture.widget.translationX, 0f)
            assertEquals(0f, fixture.widget.translationY, 0f)
        }
    }

    @Test fun cancelledDragRestoresPositionWithoutSavingOrClicking() {
        withFixture { fixture ->
            fixture.send(MotionEvent.ACTION_DOWN, 150f, 150f)
            fixture.send(MotionEvent.ACTION_MOVE, 250f, 250f)
            assertTrue(fixture.widget.translationX > 100f)
            fixture.send(MotionEvent.ACTION_CANCEL, 250f, 250f)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(100f, fixture.widget.translationX, 0.01f)
            assertEquals(100f, fixture.widget.translationY, 0.01f)
            assertEquals(100f, fixture.prefs.getFloat("time_text_x", -1f), 0f)
            assertEquals(100f, fixture.prefs.getFloat("time_text_y", -1f), 0f)
            assertEquals(0, fixture.clicks)
            assertEquals(listOf(true, false), fixture.interactions)
        }
    }

    @Test fun cancelledTapDoesNotClickButNextTapStillWorks() {
        withFixture { fixture ->
            fixture.send(MotionEvent.ACTION_DOWN, 150f, 150f)
            fixture.send(MotionEvent.ACTION_CANCEL, 150f, 150f)
            assertEquals(0, fixture.clicks)
            fixture.send(MotionEvent.ACTION_DOWN, 150f, 150f)
            fixture.send(MotionEvent.ACTION_UP, 150f, 150f)
            assertEquals(1, fixture.clicks)
        }
    }

    @Test fun leavingEditModeCancelsUnfinishedDrag() {
        withFixture { fixture ->
            fixture.send(MotionEvent.ACTION_DOWN, 150f, 150f)
            fixture.send(MotionEvent.ACTION_MOVE, 250f, 250f)
            fixture.mover.setEditMode(false)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(100f, fixture.prefs.getFloat("time_text_x", -1f), 0f)
            assertEquals(100f, fixture.widget.translationX, 0.01f)
            assertEquals(listOf(true, false), fixture.interactions)
            assertEquals(0, fixture.clicks)
        }
    }

    private fun withFixture(oversized: Boolean = false, block: (Fixture) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val prefs = activity.getSharedPreferences("WidgetPositions", Context.MODE_PRIVATE)
            prefs.edit().clear()
                .putBoolean("is_layout_initialized", true)
                .putInt("widget_mover_layout_version", 2)
                .putBoolean("time_text_individual_free_mode", true)
                .putBoolean("time_text_has_user_saved_pos", true)
                .putFloat("time_text_x", 100f)
                .putFloat("time_text_y", 100f)
                .apply()
            val root = FrameLayout(activity)
            val desktop = ConstraintLayout(activity)
            root.addView(desktop, FrameLayout.LayoutParams(-1, -1))
            val widget = View(activity).apply { id = R.id.time_text }
            val size = if (oversized) 2000 else 100
            desktop.addView(widget, ConstraintLayout.LayoutParams(size, size).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            })
            activity.setContentView(root)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Desktop must be laid out", desktop.width > 0 && desktop.height > 0)
            val mover = WidgetMover(activity, listOf(widget), desktop)
            mover.setGridSnapEnabled(false)
            mover.setCollisionCheckEnabled(false)
            mover.setEditMode(true)
            shadowOf(Looper.getMainLooper()).idle()
            val fixture = Fixture(root, desktop, widget, prefs, mover)
            widget.setOnClickListener { fixture.clicks++ }
            mover.setOnInteractionListener { fixture.interactions.add(it) }
            block(fixture)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private class Fixture(
        val root: FrameLayout,
        val desktop: ConstraintLayout,
        val widget: View,
        val prefs: SharedPreferences,
        val mover: WidgetMover
    ) {
        var clicks = 0
        val interactions = mutableListOf<Boolean>()
        private val downTime = SystemClock.uptimeMillis()
        private var eventTime = downTime

        fun send(action: Int, desktopX: Float, desktopY: Float) {
            val point = floatArrayOf(desktopX, desktopY)
            desktop.matrix.mapPoints(point)
            val event = MotionEvent.obtain(
                downTime, eventTime, action, point[0] + desktop.left, point[1] + desktop.top, 0
            )
            eventTime += 16
            try {
                assertTrue("Touch event must be handled", root.dispatchTouchEvent(event))
            } finally {
                event.recycle()
            }
        }
    }
}
