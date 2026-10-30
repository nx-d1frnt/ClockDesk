package com.nxd1frnt.clockdesk2.ui

import android.app.Activity
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.button.MaterialButton
import com.nxd1frnt.clockdesk2.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AdaptiveEditDockActionsTest {
    @Test
    fun translatedActionsFitAndLabelsReturnWhenWidthIncreases() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        for (language in listOf("en", "ru", "uk")) {
            for (scale in listOf(1f, 1.5f, 2f)) {
                val config = Configuration(activity.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                    fontScale = scale
                }
                val context = android.view.ContextThemeWrapper(activity.createConfigurationContext(config),
                    com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
                val row = LayoutInflater.from(context).inflate(R.layout.activity_main, null)
                    .findViewById<AdaptiveEditDockActions>(R.id.edit_dock_actions)
                fun measure(dp: Int) {
                    row.measure(View.MeasureSpec.makeMeasureSpec(
                        (dp * context.resources.displayMetrics.density).toInt(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    row.layout(0, 0, row.measuredWidth, row.measuredHeight)
                }
                val add = row.findViewById<MaterialButton>(R.id.btn_add_widget)
                val label = add.text.toString()
                measure(280)
                assertEquals(View.VISIBLE, add.visibility)
                assertEquals(View.VISIBLE, row.findViewById<View>(R.id.btn_done_edit).visibility)
                assertEquals(View.VISIBLE, row.findViewById<View>(R.id.btn_edit_dock_more).visibility)
                for (index in 0 until row.childCount) {
                    val child = row.getChildAt(index)
                    if (child.visibility != View.GONE) {
                        assertTrue("$language/$scale: child exceeds row", child.right <= row.width)
                    }
                }
                assertEquals(label, add.contentDescription.toString())
                measure(2400)
                assertEquals(label, add.text.toString())
                assertEquals(View.GONE, row.findViewById<View>(R.id.btn_edit_dock_more).visibility)
                assertEquals(View.VISIBLE, row.findViewById<View>(R.id.demo_button).visibility)
                assertFalse(row.findViewById<MaterialButton>(R.id.settings_button).text.isEmpty())
            }
        }
    }
}
