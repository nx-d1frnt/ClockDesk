package com.nxd1frnt.clockdesk2.utils

/** Delivers only the latest state after a pause. All calls must run on the UI thread. */
internal class ForegroundStateDispatcher<T : Any>(private val deliver: (T) -> Unit) {
    private var isResumed = false
    private var latest: T? = null
    private var pending = false

    fun submit(state: T) {
        latest = state
        pending = true
        dispatchPending()
    }

    fun resume() {
        isResumed = true
        dispatchPending()
    }

    fun pause(replayLatest: Boolean = false) {
        isResumed = false
        if (replayLatest && latest != null) pending = true
    }

    fun clear() {
        latest = null
        pending = false
    }

    private fun dispatchPending() {
        if (!isResumed || !pending) return
        val state = latest ?: return
        pending = false
        deliver(state)
    }
}
