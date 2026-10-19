package com.nxd1frnt.clockdesk2.background

import com.nxd1frnt.clockdesk2.ui.view.CompositeLoadingTimeline

import org.junit.Assert.*
import org.junit.Test

class CompositeLoadingTimelineTest {
    @Test fun fastImageContinuesTheFadeAlreadyStartedWithZoom() {
        val timeline = CompositeLoadingTimeline()
        timeline.begin(1000L)
        assertEquals(0f, timeline.progress(1000L), 0f)
        val progress = timeline.imageReady(1400L)
        assertEquals(400f / 4700f, progress, 0.00001f)
        assertEquals(4300f, (1f - progress) * 4700f, 0.001f)
        assertFalse(timeline.isWaitingForImage)
    }

    @Test fun slowImageHoldsTheOldImageUntilReadyAndKeepsTheFullReveal() {
        val timeline = CompositeLoadingTimeline()
        timeline.begin(1000L)
        assertEquals(1100f / 4700f, timeline.progress(2100L), 0.00001f)
        assertEquals(timeline.progress(2100L), timeline.progress(31000L), 0f)
        assertTrue(timeline.isWaitingForImage)
        val progress = timeline.imageReady(31000L)
        assertEquals(3600f, (1f - progress) * 4700f, 0.001f)
        assertFalse(timeline.isWaitingForImage)
    }

    @Test fun failedOrCanceledRequestClearsTheLoadingHold() {
        val timeline = CompositeLoadingTimeline()
        timeline.begin(1000L)
        timeline.cancel()
        assertFalse(timeline.isWaitingForImage)
        assertEquals(0f, timeline.progress(31000L), 0f)
        assertEquals(0f, timeline.imageReady(31000L), 0f)
    }

    @Test fun replacementRequestGetsItsOwnLoadingStart() {
        val timeline = CompositeLoadingTimeline()
        timeline.begin(1000L)
        timeline.cancel()
        timeline.begin(31000L)
        assertEquals(0f, timeline.progress(31000L), 0f)
        assertEquals(200f / 4700f, timeline.imageReady(31200L), 0.00001f)
    }
    @Test fun successorLoadDoesNotRestartAnAlreadyVisibleEffect() {
        val timeline = CompositeLoadingTimeline()
        timeline.begin(1000L)
        val beforeReplacement = timeline.progress(1600L)
        timeline.begin(1600L)
        assertEquals(beforeReplacement, timeline.progress(1600L), 0f)
        assertEquals(800f / 4700f, timeline.imageReady(1800L), 0.00001f)
    }

    @Test fun interruptingARevealKeepsTheLoadingLayerAtFullStrength() {
        val timeline = CompositeLoadingTimeline()
        timeline.begin(10000L, initialProgress = 0.7f)
        assertEquals(CompositeLoadingTimeline.FADE_FRACTION, timeline.progress(10000L), 0.0003f)
        assertEquals(CompositeLoadingTimeline.FADE_FRACTION, timeline.imageReady(11000L), 0.00001f)
    }

    @Test fun interruptedFadeContinuesWithoutAZeroOpacityFrame() {
        val timeline = CompositeLoadingTimeline()
        timeline.begin(10000L, initialProgress = 600f / 4700f)
        assertEquals(600f / 4700f, timeline.progress(10000L), 0.0003f)
        assertEquals(800f / 4700f, timeline.imageReady(10200L), 0.0003f)
    }

}
