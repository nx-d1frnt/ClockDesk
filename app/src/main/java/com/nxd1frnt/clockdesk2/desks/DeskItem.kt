package com.nxd1frnt.clockdesk2.desks

data class DeskItem(
    val id: String,
    val type: DeskType,
    var title: String,
    var order: Int,
    var customUrl: String = "https://www.google.com",
    var autoRefreshMinutes: Int = 0,
    var widgetLayoutKey: String = "active_widgets_json"
)
