package com.nxd1frnt.clockdesk2.widgets.impl

import android.view.View
import android.widget.TextView
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
        val pattern = fontManager.getDateFormatPattern().ifBlank { "EEE, d MMM" }
        try {
            val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
            view.text = dateFormat.format(currentDate)
        } catch (e: Exception) {
            val fallbackFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
            view.text = fallbackFormat.format(currentDate)
        }
    }
}
