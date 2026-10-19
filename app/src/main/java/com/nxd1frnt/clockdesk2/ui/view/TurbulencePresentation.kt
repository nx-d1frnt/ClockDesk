package com.nxd1frnt.clockdesk2.ui.view

import kotlin.math.roundToInt

/** Keeps the standalone overlay continuous across loading masks and circular reveals. */
internal class TurbulenceVisibility(private val returnDurationMs: Long = 750L) {
    private var value = 1f
    private var masked = false
    private var entryValue = 1f
    private var returnFrom = 1f
    private var returnStartedAt: Long? = null

    /** Null means normal presentation; a mask means the transition owns overlay visibility. */
    fun update(mask: Float?, nowMs: Long): Float {
        if (mask != null) {
            if (!masked) entryValue = sampleReturn(nowMs)
            masked = true
            returnStartedAt = null
            value = entryValue * mask.coerceIn(0f, 1f)
            return value
        }
        if (masked) {
            masked = false
            returnFrom = value
            returnStartedAt = nowMs
        }
        value = sampleReturn(nowMs)
        return value
    }

    private fun sampleReturn(nowMs: Long): Float {
        val start = returnStartedAt ?: return value
        val progress = if (returnDurationMs <= 0) 1f
            else ((nowMs - start).toFloat() / returnDurationMs).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        val result = returnFrom + (1f - returnFrom) * eased
        if (progress >= 1f) returnStartedAt = null
        return result
    }
}

/** Changes tint without changing noise position, animation phase, or opacity. */
internal class TurbulenceColorTransition(initialColor: Int = -1, private val durationMs: Long = 400L) {
    private var from = initialColor
    private var target = initialColor
    private var startedAt: Long? = null

    fun setColor(color: Int, nowMs: Long, animate: Boolean) {
        if (!animate) {
            from = color
            target = color
            startedAt = null
        } else if (color != target) {
            from = colorAt(nowMs)
            target = color
            startedAt = nowMs
        }
    }

    fun colorAt(nowMs: Long): Int {
        val start = startedAt ?: return target
        val progress = if (durationMs <= 0) 1f
            else ((nowMs - start).toFloat() / durationMs).coerceIn(0f, 1f)
        if (progress >= 1f) {
            startedAt = null
            return target
        }
        val eased = progress * progress * (3f - 2f * progress)
        fun channel(shift: Int): Int {
            val a = (from ushr shift) and 255
            val b = (target ushr shift) and 255
            return (a + (b - a) * eased).roundToInt()
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
