package com.nxd1frnt.clockdesk2.alarms

import java.util.concurrent.CopyOnWriteArrayList

/** Whether the desk hosting the in-app alarm banner is visible (including paused, visible windows). */
internal object AlarmPresentation {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    var isDeskVisible = false
        private set

    fun setDeskVisible(visible: Boolean) {
        if (isDeskVisible == visible) return
        isDeskVisible = visible
        listeners.forEach { it() }
    }

    fun addListener(listener: () -> Unit) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
}
