package com.nxd1frnt.clockdesk2.background

import com.nxd1frnt.clockdesk2.ui.view.TurbulenceVisibility
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceColorTransition

import org.junit.Assert.*
import org.junit.Test

class TurbulencePresentationTest {
    @Test fun overlayReturnsSmoothlyAfterTheFinalRevealFrame() {
        val visibility = TurbulenceVisibility()
        assertEquals(1f, visibility.update(1f, 1000L), 0f)
        assertEquals(0f, visibility.update(0f, 2000L), 0f)
        assertEquals(0f, visibility.update(0f, 4700L), 0f)
        assertEquals(0f, visibility.update(null, 4716L), 0f)
        assertEquals(0.5f, visibility.update(null, 5091L), 0.00001f)
        assertEquals(1f, visibility.update(null, 5466L), 0f)
        assertEquals(1f, visibility.update(null, 10000L), 0f)
    }

    @Test fun cancelingAnIncompleteLoadingFadeDoesNotJumpToFullVisibility() {
        val visibility = TurbulenceVisibility()
        visibility.update(0.4f, 1000L)
        assertEquals(0.4f, visibility.update(null, 1100L), 0.00001f)
        assertEquals(0.7f, visibility.update(null, 1475L), 0.00001f)
        assertEquals(1f, visibility.update(null, 1850L), 0f)
    }

    @Test fun anotherTransitionDuringReturnDoesNotExposeTheFullOverlay() {
        val visibility = TurbulenceVisibility()
        visibility.update(0f, 1000L)
        visibility.update(null, 2000L)
        val before = visibility.update(null, 2375L)
        assertEquals(before, visibility.update(1f, 2375L), 0f)
        assertEquals(before * 0.5f, visibility.update(0.5f, 2400L), 0f)
        assertEquals(0f, visibility.update(0f, 2500L), 0f)
    }

    @Test fun newArtworkColorArrivesWithoutRestartingTheVisibleTint() {
        val old = 0xff336699.toInt()
        val next = 0xffcc2211.toInt()
        val color = TurbulenceColorTransition(old)
        color.setColor(next, 1000L, animate = true)
        assertEquals(old, color.colorAt(1000L))
        assertNotEquals(old, color.colorAt(1200L))
        assertNotEquals(next, color.colorAt(1200L))
        assertEquals(next, color.colorAt(1400L))
    }

    @Test fun repeatedThemeNotificationsDoNotRestartColorAnimation() {
        val next = 0xffcc2211.toInt()
        val color = TurbulenceColorTransition(0xff336699.toInt())
        color.setColor(next, 1000L, animate = true)
        color.setColor(next, 1200L, animate = true)
        assertEquals(next, color.colorAt(1400L))
    }

    @Test fun replacingTheTargetColorStartsFromTheCurrentInterpolatedTint() {
        val color = TurbulenceColorTransition(0xff336699.toInt())
        color.setColor(0xffcc2211.toInt(), 1000L, animate = true)
        val current = color.colorAt(1200L)
        val next = 0xff22cc44.toInt()
        color.setColor(next, 1200L, animate = true)
        assertEquals(current, color.colorAt(1200L))
        assertEquals(next, color.colorAt(1600L))
    }

    @Test fun inactiveEffectsTakeTheirRequestedColorImmediatelyIncludingAlpha() {
        val color = TurbulenceColorTransition()
        val tint = 0x804080c0.toInt()
        color.setColor(tint, 1000L, animate = false)
        assertEquals(tint, color.colorAt(1000L))
    }
}
