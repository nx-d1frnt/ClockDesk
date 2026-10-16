package com.nxd1frnt.clockdesk2.smartchips

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.utils.FontManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w1000dp-h1000dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SmartChipExpandedTouchTest {

    @Test
    fun handleExpandedStackTouchCollapsesOnTapOutside() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()

        val root = ConstraintLayout(activity).apply {
            layoutParams = ConstraintLayout.LayoutParams(1000, 1000)
        }
        activity.setContentView(root)

        val scrollView = ScrollView(activity).apply {
            layoutParams = ConstraintLayout.LayoutParams(300, 300)
        }
        val chipContainer = ConstraintLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(chipContainer)
        root.addView(scrollView)

        root.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1000, 1000)

        val timeView = TextView(activity)
        val dateView = TextView(activity)
        val lastfmLayout = FrameLayout(activity)
        val lastfmText = TextView(activity)
        val lastfmIcon = ImageView(activity)
        val weatherText = TextView(activity)
        val weatherIcon = ImageView(activity)

        val prefs = activity.getSharedPreferences("test_chips", Context.MODE_PRIVATE)
        val fontManager = FontManager(
            activity,
            timeView,
            dateView,
            lastfmLayout,
            lastfmText,
            lastfmIcon,
            weatherText,
            weatherIcon,
            chipContainer,
            false
        )
        val manager = SmartChipManager(activity, chipContainer, prefs, fontManager)

        // Stack not expanded initially
        val dummyEvent = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
            MotionEvent.ACTION_DOWN, 100f, 100f, 0
        )
        assertFalse(manager.handleExpandedStackTouch(dummyEvent))
        dummyEvent.recycle()

        // Expand stack
        manager.expandStack()
        assertTrue(manager.isStackExpanded)

        // Tap outside any chip collapses the stack
        val outsideDown = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
            MotionEvent.ACTION_DOWN, 800f, 800f, 0
        )
        val handledOutside = manager.handleExpandedStackTouch(outsideDown)
        outsideDown.recycle()
        assertFalse(handledOutside)
        assertFalse("Stack must collapse when tapped outside", manager.isStackExpanded)

        controller.pause().stop().destroy()
    }

    @Test
    fun handleExpandedStackTouchDispatchesClickBeyondBaseContainer() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()

        val root = ConstraintLayout(activity).apply {
            layoutParams = ConstraintLayout.LayoutParams(1000, 1000)
        }
        activity.setContentView(root)

        val scrollView = ScrollView(activity).apply {
            layoutParams = ConstraintLayout.LayoutParams(300, 300)
        }
        val chipContainer = ConstraintLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(chipContainer)
        root.addView(scrollView)

        var clicked = false
        val chipView = View(activity).apply {
            layoutParams = ConstraintLayout.LayoutParams(200, 60)
            isClickable = true
            setOnClickListener { clicked = true }
        }
        chipContainer.addView(chipView)

        root.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1000, 1000)

        // Position chipView beyond base container height of 300 (e.g. at y = 500)
        chipView.layout(0, 500, 200, 560)

        val timeView = TextView(activity)
        val dateView = TextView(activity)
        val lastfmLayout = FrameLayout(activity)
        val lastfmText = TextView(activity)
        val lastfmIcon = ImageView(activity)
        val weatherText = TextView(activity)
        val weatherIcon = ImageView(activity)

        val prefs = activity.getSharedPreferences("test_chips", Context.MODE_PRIVATE)
        val fontManager = FontManager(
            activity,
            timeView,
            dateView,
            lastfmLayout,
            lastfmText,
            lastfmIcon,
            weatherText,
            weatherIcon,
            chipContainer,
            false
        )
        val manager = SmartChipManager(activity, chipContainer, prefs, fontManager)

        // Add a mock ChipInfo entry into allChips via reflection
        val chipInfoClass = SmartChipManager::class.java.declaredClasses.first { it.simpleName == "ChipInfo" }
        val constructor = chipInfoClass.declaredConstructors.first().apply { isAccessible = true }
        // ChipInfo(id, channelId, view, isVisible, currentText, clickActivityClassName)
        val chipInfo = constructor.newInstance("test_overflow_chip", "test_channel", chipView, true, "Test", null)

        val allChipsField = SmartChipManager::class.java.getDeclaredField("allChips").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val allChips = allChipsField.get(manager) as MutableList<Any>
        allChips.add(chipInfo)

        manager.expandStack()
        assertTrue(manager.isStackExpanded)

        // Screen coords for chip: left = 0, right = 200, top = 500, bottom = 560
        val loc = IntArray(2)
        chipView.getLocationOnScreen(loc)
        val touchX = loc[0] + 50f
        val touchY = loc[1] + 30f

        val downEvent = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
            MotionEvent.ACTION_DOWN, touchX, touchY, 0
        )
        val handledDown = manager.handleExpandedStackTouch(downEvent)
        downEvent.recycle()

        assertTrue("ACTION_DOWN on chip beyond container must be handled", handledDown)
        assertTrue("Stack must remain expanded on ACTION_DOWN on chip", manager.isStackExpanded)

        val upEvent = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis() + 50,
            MotionEvent.ACTION_UP, touchX, touchY, 0
        )
        val handledUp = manager.handleExpandedStackTouch(upEvent)
        upEvent.recycle()

        assertTrue("ACTION_UP on chip beyond container must be handled", handledUp)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue("Chip beyond container must receive click", clicked)

        controller.pause().stop().destroy()
    }
}
