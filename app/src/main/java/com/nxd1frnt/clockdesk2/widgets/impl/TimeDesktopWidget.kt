package com.nxd1frnt.clockdesk2.widgets.impl

import android.view.View
import android.widget.TextView
import com.nxd1frnt.clockdesk2.ui.view.ClockTextView
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetDefinition
import com.nxd1frnt.clockdesk2.widgets.WidgetInstance
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetController
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetHost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeDesktopWidget(
    instance: WidgetInstance,
    definition: DesktopWidgetDefinition,
    host: DesktopWidgetHost
) : DesktopWidgetController(instance, definition, host) {

    override var rootView: View? = null
    val textView: TextView? get() = rootView as? TextView

    override fun bindView(view: View) {
        super.bindView(view)
        updateTime(Date())
    }

    fun updateTime(currentTime: Date) {
        val view = textView ?: return
        val fontManager = host.fontManager ?: return
        val clockStyle = fontManager.getClockStyle()
        val timePattern = fontManager.getTimeFormatPattern().ifBlank { "HH:mm" }

        if (view is ClockTextView) {
            view.isAnalogMode = clockStyle.isAnalog
            view.setTime(currentTime)
        }

        if (clockStyle.isAnalog) {
            view.text = " "
        } else if (clockStyle.isTwoLine) {
            val hourPattern = if (timePattern.contains("h")) "hh" else "HH"
            val minutePattern = "mm"
            val hourStr = SimpleDateFormat(hourPattern, Locale.getDefault()).format(currentTime)
            val minStr = SimpleDateFormat(minutePattern, Locale.getDefault()).format(currentTime)
            view.text = "$hourStr\n$minStr"
        } else {
            val showSeconds = host.getPreferences().getBoolean("clock_show_seconds", false)
            val effectivePattern = if (showSeconds && !timePattern.contains("s")) {
                if (timePattern.contains("a")) {
                    timePattern.replace("a", ":ss a")
                } else {
                    "$timePattern:ss"
                }
            } else {
                timePattern
            }
            try {
                val formatted = SimpleDateFormat(effectivePattern, Locale.getDefault()).format(currentTime)
                view.text = formatted
            } catch (e: Exception) {
                view.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime)
            }
        }
    }
}
