package com.nxd1frnt.clockdesk2.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundStateDispatcherTest {
    @Test fun switchingManyTracksWhilePausedDeliversOnlyTheCurrentTrack() {
        val applied = mutableListOf<String>()
        val updates = ForegroundStateDispatcher<String> { applied.add(it) }
        updates.resume()
        updates.submit("initial track")
        updates.pause(replayLatest = true)
        repeat(100) { updates.submit("track $it") }
        assertEquals(listOf("initial track"), applied)

        updates.resume()
        updates.resume()
        assertEquals(listOf("initial track", "track 99"), applied)
    }

    @Test fun pauseOrStopWhileHiddenReplacesPendingAlbumArtwork() {
        for (finalState in listOf("paused", "idle", "disabled")) {
            val applied = mutableListOf<String>()
            val updates = ForegroundStateDispatcher<String> { applied.add(it) }
            updates.submit("playing with artwork")
            updates.submit(finalState)
            updates.resume()
            assertEquals(listOf(finalState), applied)
        }
    }

    @Test fun resumeReplaysInterruptedLoadWithoutAnyNewTrackEvent() {
        val applied = mutableListOf<String>()
        val updates = ForegroundStateDispatcher<String> { applied.add(it) }
        updates.resume()
        updates.submit("current artwork")
        updates.pause(replayLatest = true)
        updates.resume()
        assertEquals(listOf("current artwork", "current artwork"), applied)
    }

    @Test fun completedLoadIsNotRepeatedAfterPause() {
        val applied = mutableListOf<String>()
        val updates = ForegroundStateDispatcher<String> { applied.add(it) }
        updates.resume()
        updates.submit("completed artwork")
        updates.pause()
        updates.resume()
        assertEquals(listOf("completed artwork"), applied)
    }

    @Test fun currentMusicReplacesInterruptedBackgroundBeforeLoadsResume() {
        val rendered = mutableListOf<String>()
        val backgrounds = ForegroundStateDispatcher<String> { rendered.add(it) }
        val music = ForegroundStateDispatcher<String> { backgrounds.submit(it) }
        backgrounds.resume()
        music.resume()
        music.submit("old artwork")
        music.pause(replayLatest = true)
        backgrounds.pause(replayLatest = true)
        music.submit("intermediate artwork")
        music.submit("current artwork")

        music.resume()
        assertEquals(listOf("old artwork"), rendered)
        backgrounds.resume()
        assertEquals(listOf("old artwork", "current artwork"), rendered)
    }

    @Test fun switchingToGradientDiscardsInterruptedImageLoad() {
        val rendered = mutableListOf<String>()
        val backgrounds = ForegroundStateDispatcher<String> { rendered.add(it) }
        backgrounds.submit("old artwork")
        backgrounds.clear()
        backgrounds.resume()
        assertTrue(rendered.isEmpty())
    }
}
