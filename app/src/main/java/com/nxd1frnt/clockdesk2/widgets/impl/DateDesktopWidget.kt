package com.nxd1frnt.clockdesk2.widgets.impl

import android.view.View
import android.widget.TextView
import com.nxd1frnt.clockdesk2.ui.view.DateTextView
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetDefinition
import com.nxd1frnt.clockdesk2.widgets.WidgetInstance
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetController
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetHost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateDesktopWidget(
    instance: WidgetInstance,
    definition: DesktopWidgetDefinition,
    host: DesktopWidgetHost
) : DesktopWidgetController(instance, definition, host) {

    override var rootView: View? = null
    val textView: TextView? get() = rootView as? TextView

    override fun bindView(view: View) {
        super.bindView(view)
        updateDate(Date())
    }

    fun updateDate(currentDate: Date) {
        val view = textView ?: return
        val fontManager = host.fontManager ?: return
        val dateStyle = fontManager.getDateStyle()

        if (view is DateTextView) {
            view.dateStyle = dateStyle
            view.isBadgeMode = dateStyle.isBadge
            view.isTwoLineMode = dateStyle.isTwoLine
            view.setDate(currentDate)
        }

        if (dateStyle.isBadge || dateStyle.isHeroDay) {
            view.text = " "
        } else if (dateStyle.isTwoLine) {
            val pattern = fontManager.getDateFormatPattern().ifBlank { "EEE, MMM dd" }
            view.text = formatTwoLineDate(currentDate, pattern)
        } else {
            val pattern = fontManager.getDateFormatPattern().ifBlank { "EEE, MMM dd" }
            try {
                val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
                view.text = dateFormat.format(currentDate)
            } catch (e: Exception) {
                val fallbackFormat = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())
                view.text = fallbackFormat.format(currentDate)
            }
        }
    }

    private fun formatTwoLineDate(currentDate: Date, pattern: String): String {
        val locale = Locale.getDefault()
        val effectivePattern = pattern.replace("\\n", "\n").trim()
        return try {
            val (topPattern, bottomPattern) = when {
                effectivePattern.contains("\n") -> {
                    val parts = effectivePattern.split("\n", limit = 2)
                    parts[0].trim() to parts[1].trim()
                }
                effectivePattern.contains(",") -> {
                    val parts = effectivePattern.split(",", limit = 2)
                    parts[0].trim() to parts[1].trim()
                }
                effectivePattern.contains(" ") -> {
                    val parts = effectivePattern.split(Regex("\\s+"), limit = 2)
                    parts[0].trim() to parts[1].trim()
                }
                else -> {
                    "EEE" to effectivePattern
                }
            }

            val line1 = SimpleDateFormat(topPattern.ifBlank { "EEE" }, locale).format(currentDate)
            val line2 = SimpleDateFormat(bottomPattern.ifBlank { "MMM d" }, locale).format(currentDate)
            "$line1\n$line2"
        } catch (e: Exception) {
            val line1 = SimpleDateFormat("EEE", locale).format(currentDate)
            val line2 = SimpleDateFormat("MMM d", locale).format(currentDate)
            "$line1\n$line2"
        }
    }
}
