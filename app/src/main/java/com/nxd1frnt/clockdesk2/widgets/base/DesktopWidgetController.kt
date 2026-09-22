package com.nxd1frnt.clockdesk2.widgets.base

import android.view.View
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetDefinition
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetType
import com.nxd1frnt.clockdesk2.widgets.WidgetInstance

abstract class DesktopWidgetController(
    val instance: WidgetInstance,
    val definition: DesktopWidgetDefinition,
    val host: DesktopWidgetHost
) {
    val type: DesktopWidgetType get() = definition.type
    val instanceId: String get() = instance.instanceId

    abstract var rootView: View?

    open fun bindView(view: View) {
        rootView = view
        setupClickListeners()
        updateEditModeUI(host.isEditMode)
    }

    protected open fun setupClickListeners() {
        rootView?.setOnClickListener {
            if (host.isEditMode) {
                host.onWidgetClicked(this)
            }
        }
    }

    open fun setEditMode(isEditMode: Boolean) {
        updateEditModeUI(isEditMode)
    }

    protected open fun updateEditModeUI(isEditMode: Boolean) {
        rootView?.let { v ->
            if (isEditMode) {
                v.setBackgroundResource(R.drawable.editable_border)
            } else {
                v.background = null
            }
        }
    }

    open fun setVisible(visible: Boolean) {
        instance.isVisible = visible
        rootView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    open fun isVisible(): Boolean {
        return instance.isVisible && (rootView?.visibility == View.VISIBLE)
    }

    open fun onPause() {}
    open fun onResume() {}
    open fun onDestroy() {
        rootView = null
    }
}
