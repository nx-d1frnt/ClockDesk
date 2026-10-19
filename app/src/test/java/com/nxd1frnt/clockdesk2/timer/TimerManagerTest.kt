package com.nxd1frnt.clockdesk2.timer

import android.app.Application
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class TimerManagerTest {
    @Before fun setup() { TimerManager.reset(); TimerManager.onPowerSaveModeChanged(false) }
    @After fun cleanup() { TimerManager.reset(); TimerManager.onPowerSaveModeChanged(false) }
    private fun idle(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test fun reducedTickRateStillFinishesAtFractionalDeadline() {
        TimerManager.onPowerSaveModeChanged(true)
        TimerManager.start(1500)
        idle(999)
        assertEquals(1500L, TimerManager.remainingMillis)
        idle(1)
        assertEquals(500L, TimerManager.remainingMillis)
        idle(499)
        assertEquals(TimerState.RUNNING, TimerManager.state)
        idle(1)
        assertEquals(TimerState.FINISHED, TimerManager.state)
        assertEquals(0L, TimerManager.remainingMillis)
    }

    @Test fun modeChangeAndPausePreserveElapsedTime() {
        TimerManager.start(2500)
        idle(300)
        TimerManager.onPowerSaveModeChanged(true)
        idle(0)
        assertEquals(2200L, TimerManager.remainingMillis)
        idle(200)
        TimerManager.pause()
        assertEquals(2000L, TimerManager.remainingMillis)
        idle(1000)
        TimerManager.resume()
        idle(2000)
        assertEquals(TimerState.FINISHED, TimerManager.state)
    }
}
