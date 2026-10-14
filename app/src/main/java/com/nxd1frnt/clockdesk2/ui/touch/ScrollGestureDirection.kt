package com.nxd1frnt.clockdesk2.ui.touch

import kotlin.math.abs

/** Locks the direction once movement exceeds the system touch slop. */
internal class ScrollGestureDirection(private val touchSlop: Int) {
    enum class Axis { UNDECIDED, HORIZONTAL, VERTICAL }

    var axis = Axis.UNDECIDED
        private set
    private var startX = 0f
    private var startY = 0f

    fun start(x: Float, y: Float) {
        axis = Axis.UNDECIDED
        rebase(x, y)
    }

    fun rebase(x: Float, y: Float) {
        startX = x
        startY = y
    }

    fun move(x: Float, y: Float) {
        if (axis != Axis.UNDECIDED) return
        val dx = abs(x - startX)
        val dy = abs(y - startY)
        axis = when {
            dy > touchSlop && dy > dx -> Axis.VERTICAL
            dx > touchSlop && dx > dy -> Axis.HORIZONTAL
            else -> Axis.UNDECIDED
        }
    }
}
