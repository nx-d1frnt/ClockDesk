package com.nxd1frnt.clockdesk2.desks.ui

import android.app.Application
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DeskPageIndicatorViewTest {
    private lateinit var root: FrameLayout
    private lateinit var indicator: DeskPageIndicatorView

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_ClockDesk)
        context.getSharedPreferences(UiMonetThemeManager.PREF_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
        root = FrameLayout(context)
        indicator = DeskPageIndicatorView(context)
        indicator.setPageCount(4)
        root.addView(indicator, FrameLayout.LayoutParams(-2, -2))
        layoutRoot()
    }

    private fun layoutRoot() {
        root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1000, 200)
    }

    @Test fun swipeUpdatesBoundsWithoutRequestingLayout() {
        val width = indicator.width
        val initialTotal = (0 until indicator.childCount).sumOf { indicator.getChildAt(it).width }
        for (step in 1..100) {
            indicator.setScrollPosition(1, step / 100f)
            assertFalse("Swipe must not invalidate the activity layout", root.isLayoutRequested)
            assertFalse(indicator.isLayoutRequested)
            assertEquals(width, indicator.width)
            assertEquals(initialTotal, (0 until indicator.childCount).sumOf { indicator.getChildAt(it).width })
            assertTrue(indicator.getChildAt(0).left >= 0)
            assertTrue(indicator.getChildAt(3).right <= width)
        }
    }

    @Test fun rtlMirrorsTheDotsAndPreservesPageClicks() {
        indicator.setScrollPosition(1, 0.4f)
        val lefts = (0 until 4).map { indicator.getChildAt(it).left }
        val rights = (0 until 4).map { indicator.getChildAt(it).right }
        indicator.layoutDirection = View.LAYOUT_DIRECTION_RTL
        layoutRoot()
        indicator.setScrollPosition(1, 0.4f)
        for (i in 0 until 4) {
            assertEquals(indicator.width - rights[i], indicator.getChildAt(i).left)
            assertEquals(indicator.width - lefts[i], indicator.getChildAt(i).right)
        }
        var clicked = -1
        indicator.onPageClicked = { clicked = it }
        indicator.getChildAt(2).performClick()
        assertEquals(2, clicked)
    }

    @Test fun swipeUsesCachedColorsUntilThemeRefresh() {
        val originalColor = (indicator.getChildAt(0).background as GradientDrawable).color!!.defaultColor
        indicator.context.getSharedPreferences(UiMonetThemeManager.PREF_FILE, Context.MODE_PRIVATE)
            .edit().putInt(UiMonetThemeManager.KEY_DYNAMIC_SEED_COLOR, 0xffff0000.toInt()).commit()
        indicator.setScrollPosition(1, 0.5f)
        indicator.setScrollPosition(0, 0f)
        assertEquals(originalColor, (indicator.getChildAt(0).background as GradientDrawable).color!!.defaultColor)
        indicator.refreshMonetTheme()
        assertEquals(UiMonetThemeManager.getPalette(indicator.context).primary,
            (indicator.getChildAt(0).background as GradientDrawable).color!!.defaultColor)
    }
}
