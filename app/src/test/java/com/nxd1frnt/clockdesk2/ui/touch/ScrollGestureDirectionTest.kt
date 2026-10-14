package com.nxd1frnt.clockdesk2.ui.touch

import org.junit.Assert.assertEquals
import org.junit.Test
import com.nxd1frnt.clockdesk2.ui.touch.ScrollGestureDirection.Axis

class ScrollGestureDirectionTest {
    private val direction = ScrollGestureDirection(8)

    @Test fun tapAndSmallMovementDoNotChooseDirection() {
        direction.start(100f, 100f)
        direction.move(104f, 108f)
        assertEquals(Axis.UNDECIDED, direction.axis)
    }

    @Test fun verticalSwipeWithHorizontalDriftStartsScroll() {
        direction.start(100f, 100f)
        direction.move(110f, 80f)
        assertEquals(Axis.VERTICAL, direction.axis)
    }

    @Test fun horizontalDragStaysHorizontalEvenAfterVerticalDrift() {
        direction.start(100f, 100f)
        direction.move(120f, 110f)
        direction.move(125f, 180f)
        assertEquals(Axis.HORIZONTAL, direction.axis)
    }

    @Test fun newTouchResetsPreviousDirection() {
        direction.start(0f, 0f)
        direction.move(0f, 20f)
        direction.start(100f, 100f)
        direction.move(120f, 100f)
        assertEquals(Axis.HORIZONTAL, direction.axis)
    }

    @Test fun pointerReplacementDoesNotCreateArtificialMovement() {
        direction.start(0f, 0f)
        direction.rebase(100f, 100f)
        direction.move(103f, 104f)
        assertEquals(Axis.UNDECIDED, direction.axis)
        direction.move(103f, 120f)
        assertEquals(Axis.VERTICAL, direction.axis)
    }
}
