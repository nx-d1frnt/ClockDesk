package com.nxd1frnt.clockdesk2.widgets.impl

import android.view.View
import android.widget.LinearLayout
import com.nxd1frnt.clockdesk2.smartchips.SmartChipManager
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetDefinition
import com.nxd1frnt.clockdesk2.widgets.WidgetInstance
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetController
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetHost

class SmartChipsDesktopWidget(
    instance: WidgetInstance,
    definition: DesktopWidgetDefinition,
    host: DesktopWidgetHost
) : DesktopWidgetController(instance, definition, host) {

    override var rootView: View? = null
    val container: LinearLayout? get() = rootView as? LinearLayout

    var smartChipManager: SmartChipManager? = null

    override fun setEditMode(isEditMode: Boolean) {
        super.setEditMode(isEditMode)
        smartChipManager?.setEditMode(isEditMode) { clickedView ->
            host.onWidgetClicked(this)
        }
    }
}
