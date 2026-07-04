package com.nxd1frnt.clockdesk2.ui.dashboard

import android.content.Context
import android.view.View

interface NativeDashboardPlugin {
    fun createWidgetView(pluginContext: Context): View
}
