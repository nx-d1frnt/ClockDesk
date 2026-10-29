package com.nxd1frnt.clockdesk2.smartchips

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.plugins.BackgroundProgressPlugin
import com.nxd1frnt.clockdesk2.smartchips.plugins.BackgroundProgressPlugin.Stage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class BackgroundProgressPluginTest {
    private val stages = mutableListOf<Stage>()

    @Before
    fun setUp() {
        BackgroundProgressPlugin.onGlobalStateChanged = null
        BackgroundProgressPlugin.reset()
        BackgroundProgressPlugin.onGlobalStateChanged = { stages.add(BackgroundProgressPlugin.currentStage) }
    }

    @After
    fun tearDown() {
        BackgroundProgressPlugin.onGlobalStateChanged = null
        BackgroundProgressPlugin.reset()
    }

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun fastOperationDoesNotFlashChip() {
        BackgroundProgressPlugin.postStage(Stage.LOADING)
        advance(100)
        BackgroundProgressPlugin.postStage(Stage.RENDERING)
        BackgroundProgressPlugin.postStage(Stage.COMPLETED)
        advance(2000)
        assertEquals(Stage.IDLE, BackgroundProgressPlugin.currentStage)
        assertTrue(stages.isEmpty())
    }

    @Test
    fun initialDelayShowsLatestStageWithoutBeingPostponedByUpdates() {
        BackgroundProgressPlugin.postStage(Stage.DOWNLOADING)
        advance(150)
        BackgroundProgressPlugin.postStage(Stage.LOADING)
        advance(149)
        assertTrue(stages.isEmpty())
        advance(1)
        assertEquals(listOf(Stage.LOADING), stages)
    }

    @Test
    fun visibleUpdatesCoalesceAndCompletionDiscardsPendingStages() {
        BackgroundProgressPlugin.postStage(Stage.LOADING)
        advance(300)
        BackgroundProgressPlugin.postStage(Stage.BLURRING)
        BackgroundProgressPlugin.postStage(Stage.EXTRACTING_COLORS)
        advance(300)
        assertEquals(listOf(Stage.LOADING, Stage.EXTRACTING_COLORS), stages)
        BackgroundProgressPlugin.postStage(Stage.APPLYING_THEME)
        BackgroundProgressPlugin.postStage(Stage.RENDERING)
        BackgroundProgressPlugin.postStage(Stage.COMPLETED)
        assertEquals(Stage.COMPLETED, BackgroundProgressPlugin.currentStage)
        advance(499)
        assertEquals(Stage.COMPLETED, BackgroundProgressPlugin.currentStage)
        advance(1)
        assertEquals(listOf(Stage.LOADING, Stage.EXTRACTING_COLORS, Stage.COMPLETED, Stage.IDLE), stages)
    }

    @Test
    fun failureAppearsImmediatelyAndClearsPendingWork() {
        BackgroundProgressPlugin.postStage(Stage.LOADING)
        BackgroundProgressPlugin.postStage(Stage.FAILED, "Error")
        assertEquals(Stage.FAILED, BackgroundProgressPlugin.currentStage)
        assertEquals("Error", BackgroundProgressPlugin.customMessage)
        advance(2000)
        assertEquals(listOf(Stage.FAILED, Stage.IDLE), stages)
    }

    @Test
    fun newWorkCancelsPreviousCompletionTimeout() {
        BackgroundProgressPlugin.postStage(Stage.LOADING)
        advance(300)
        BackgroundProgressPlugin.postStage(Stage.COMPLETED)
        advance(200)
        BackgroundProgressPlugin.postStage(Stage.DOWNLOADING)
        advance(1000)
        assertEquals(Stage.DOWNLOADING, BackgroundProgressPlugin.currentStage)
    }

    @Test
    fun resetAndNewSessionDiscardPendingStages() {
        BackgroundProgressPlugin.postStage(Stage.LOADING)
        BackgroundProgressPlugin.startSession()
        advance(500)
        assertTrue(stages.isEmpty())
        BackgroundProgressPlugin.postStage(Stage.BLURRING)
        BackgroundProgressPlugin.reset()
        advance(500)
        assertTrue(stages.isEmpty())
    }

    @Test
    fun duplicateCompletionDoesNotExtendVisibility() {
        BackgroundProgressPlugin.postStage(Stage.LOADING)
        advance(300)
        BackgroundProgressPlugin.postStage(Stage.COMPLETED)
        advance(400)
        BackgroundProgressPlugin.postStage(Stage.COMPLETED)
        advance(100)
        assertEquals(Stage.IDLE, BackgroundProgressPlugin.currentStage)
    }

    @Test
    fun contentUpdatesSynchronouslyWithoutChangingChipTransforms() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_ClockDesk)
        val plugin = BackgroundProgressPlugin(context)
        val view = plugin.createView(context)
        val prefs = context.getSharedPreferences("background_progress_test", Context.MODE_PRIVATE)
        view.alpha = 0.45f
        view.scaleX = 0.82f
        view.scaleY = 0.82f
        BackgroundProgressPlugin.postStage(Stage.LOADING, "Loading")
        advance(300)
        assertTrue(plugin.update(view, prefs))
        BackgroundProgressPlugin.postStage(Stage.COMPLETED, "Done")
        assertTrue(plugin.update(view, prefs))
        assertEquals("Done", view.findViewById<TextView>(R.id.chip_text).text.toString())
        assertEquals(Stage.COMPLETED.iconRes, view.findViewById<ImageView>(R.id.chip_icon).tag)
        assertEquals(0.45f, view.alpha, 0f)
        assertEquals(0.82f, view.scaleX, 0f)
        assertEquals(0.82f, view.scaleY, 0f)
    }
}
