package com.nxd1frnt.clockdesk2.smartchips

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.utils.FontManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w1000dp-h1000dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SmartChipExpandedTouchTest {
    private class Fixture {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = ConstraintLayout(activity)
        val scroll = ScrollView(activity).apply {
            layoutParams = ConstraintLayout.LayoutParams(165, 165)
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
        }
        val container = ConstraintLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -2)
            clipChildren = false
            clipToPadding = false
        }
        val prefs = activity.getSharedPreferences("test_chips", Context.MODE_PRIVATE).apply {
            edit().clear().putBoolean("smart_chips_stack_overflow", true).commit()
        }
        val manager: SmartChipManager
        val chips = mutableListOf<TextView>()
        var clicks = 0

        init {
            scroll.addView(container)
            root.addView(scroll)
            activity.setContentView(root)
            layout()
            val fontManager = FontManager(
                activity, TextView(activity), TextView(activity), FrameLayout(activity),
                TextView(activity), ImageView(activity), TextView(activity), ImageView(activity),
                container, false
            )
            manager = SmartChipManager(activity, container, prefs, fontManager)
            entries().clear()
        }

        @Suppress("UNCHECKED_CAST")
        private fun entries() = SmartChipManager::class.java.getDeclaredField("allChips")
            .apply { isAccessible = true }.get(manager) as MutableList<Any>

        fun addChips(count: Int) {
            val constructor = SmartChipManager::class.java.declaredClasses
                .first { it.simpleName == "ChipInfo" }.declaredConstructors
                .single { it.parameterCount == 6 }.apply { isAccessible = true }
            repeat(count) {
                val id = "chip_${chips.size}"
                val chip = TextView(activity).apply {
                    text = id
                    minHeight = 36
                    minWidth = 100
                    setOnClickListener { clicks++ }
                }
                chips.add(chip)
                entries().add(constructor.newInstance(id, "test_channel", chip, true, id, null))
            }
            redraw()
        }

        fun redraw() {
            SmartChipManager::class.java.getDeclaredMethod("executeSortAndRedrawChips", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(manager, true)
            settle()
        }

        fun layout() {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, 1000, 1000)
        }

        fun settle() {
            shadowOf(Looper.getMainLooper()).idle()
            layout()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            layout()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }

        fun touch(action: Int, x: Float, y: Float, downTime: Long = SystemClock.uptimeMillis()): Boolean {
            val location = IntArray(2)
            scroll.getLocationOnScreen(location)
            val event = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), action, x + location[0], y + location[1], 0
            )
            return try { manager.handleExpandedStackTouch(event) } finally { event.recycle() }
        }

        fun close() = controller.pause().stop().destroy()
    }

    @Test
    fun handleExpandedStackTouchCollapsesOnTapOutside() {
        val f = Fixture()
        try {
            assertFalse(f.touch(MotionEvent.ACTION_DOWN, 800f, 800f))
            f.addChips(8)
            f.manager.expandStack()
            f.settle()
            assertTrue(f.manager.isStackExpanded)
            assertFalse(f.touch(MotionEvent.ACTION_DOWN, 800f, 800f))
            assertFalse(f.manager.isStackExpanded)
        } finally { f.close() }
    }

    @Test
    fun expandedStackScrollsWithoutClickingOrCollapsing() {
        val f = Fixture()
        try {
            f.addChips(12)
            f.manager.expandStack()
            f.settle()
            assertTrue(f.scroll.clipChildren)
            assertTrue(f.container.height > f.scroll.height)
            val downTime = SystemClock.uptimeMillis()
            assertTrue(f.touch(MotionEvent.ACTION_DOWN, 100f, 140f, downTime))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            assertTrue(f.touch(MotionEvent.ACTION_MOVE, 100f, 90f, downTime))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            assertTrue(f.touch(MotionEvent.ACTION_MOVE, 100f, 20f, downTime))
            assertTrue("Dragging must scroll the expanded list", f.scroll.scrollY > 0)
            assertTrue(f.touch(MotionEvent.ACTION_UP, 100f, 20f, downTime))
            f.settle()
            assertEquals(0, f.clicks)
            assertTrue(f.manager.isStackExpanded)
            f.manager.collapseStack()
            f.settle()
            assertEquals(0, f.scroll.scrollY)
            assertFalse(f.scroll.clipChildren)
        } finally { f.close() }
    }

    @Test
    fun expandedStackDeliversClicksAfterScrolling() {
        val f = Fixture()
        try {
            f.addChips(12)
            f.manager.expandStack()
            f.settle()
            val chip = f.chips[7]
            f.scroll.scrollTo(0, chip.top)
            val x = chip.left + chip.width / 2f
            val y = chip.top - f.scroll.scrollY + chip.height / 2f
            val downTime = SystemClock.uptimeMillis()
            assertTrue(f.touch(MotionEvent.ACTION_DOWN, x, y, downTime))
            assertTrue(f.touch(MotionEvent.ACTION_UP, x, y, downTime))
            f.settle()
            assertEquals(1, f.clicks)
            assertFalse(f.manager.isStackExpanded)
        } finally { f.close() }
    }

    @Test
    fun chipsOutsideExpandedViewportCannotReceiveClicks() {
        val f = Fixture()
        try {
            f.addChips(12)
            f.manager.expandStack()
            f.settle()
            val chip = f.chips.last()
            assertFalse(f.touch(MotionEvent.ACTION_DOWN, 100f, chip.top + 10f))
            assertEquals(0, f.clicks)
            assertFalse(f.manager.isStackExpanded)
        } finally { f.close() }
    }

    @Test
    fun firstLoadAndRedrawKeepOverflowBehindTheStackAnchor() {
        val f = Fixture()
        try {
            f.addChips(8)
            fun assertStack() {
                // 165 px fits two standalone chips, the anchor and its two peeking layers.
                val anchor = f.chips[2]
                assertTrue(anchor.top >= f.chips[1].bottom)
                assertEquals("Layer bounds: " + f.chips.map { "${it.top}..${it.bottom}, y=${it.translationY}" }, anchor.top, f.chips[3].top)
                assertEquals(8f, f.chips[3].translationY, 0.01f)
                assertEquals(0.90f, f.chips[3].scaleX, 0.01f)
                assertEquals(16f, f.chips[4].translationY, 0.01f)
                assertEquals(0.82f, f.chips[4].scaleX, 0.01f)
                assertTrue(anchor.z > f.chips[3].z)
                assertTrue(f.chips[3].z > f.chips[4].z)
                f.chips.drop(5).forEach { assertEquals(View.INVISIBLE, it.visibility) }
            }
            assertStack()
            f.redraw()
            assertStack()
            android.view.ViewTreeObserver::class.java.getDeclaredMethod("dispatchOnScrollChanged")
                .apply { isAccessible = true }.invoke(f.container.viewTreeObserver)
            f.settle()
            assertStack()
            f.manager.expandStack()
            f.settle()
            f.manager.collapseStack()
            f.settle()
            assertStack()
            // Reverse a transition before its visibility callbacks have completed.
            f.manager.expandStack()
            f.redraw()
            f.manager.collapseStack()
            f.settle()
            assertStack()
        } finally { f.close() }
    }
}
