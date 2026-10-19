package com.nxd1frnt.clockdesk2.ui.view

/** Loading can take longer than the fade. Hold the effect until an image is ready to reveal. */
internal class CompositeLoadingTimeline {
    @Volatile private var startedAt: Long? = null
    val isWaitingForImage: Boolean get() = startedAt != null

    fun begin(nowMs: Long, initialProgress: Float = 0f) {
        if (isWaitingForImage) return
        val elapsed = (initialProgress.coerceIn(0f, FADE_FRACTION) * TOTAL_DURATION_MS).toLong()
        startedAt = nowMs - elapsed
    }

    fun progress(nowMs: Long): Float {
        val start = startedAt ?: return 0f
        return ((nowMs - start).coerceAtLeast(0L).toFloat() / TOTAL_DURATION_MS)
            .coerceAtMost(FADE_FRACTION)
    }

    /** Hand the remaining fade and reveal to the transition animator without a visual reset. */
    fun imageReady(nowMs: Long): Float = progress(nowMs).also { cancel() }

    fun cancel() {
        startedAt = null
    }

    companion object {
        const val FADE_DURATION_MS = 1100L
        const val REVEAL_DURATION_MS = 3600L
        const val TOTAL_DURATION_MS = FADE_DURATION_MS + REVEAL_DURATION_MS
        const val FADE_FRACTION = FADE_DURATION_MS.toFloat() / TOTAL_DURATION_MS
    }
}
