package com.nxd1frnt.clockdesk2.widgets

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.music.PluginState
import com.nxd1frnt.clockdesk2.utils.FontManager
import com.nxd1frnt.clockdesk2.utils.MediaStyle
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetController
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetHost
import com.nxd1frnt.clockdesk2.widgets.impl.MediaDesktopWidget
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MediaDesktopWidgetTest {
    private lateinit var widget: MediaDesktopWidget
    private lateinit var activity: Activity
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Before fun setup() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        activity = controller.get()
        activity.setTheme(R.style.Theme_ClockDesk)
        controller.setup().visible()
        val parent = FrameLayout(activity)
        activity.setContentView(parent)
        val prefs = activity.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val host = object : DesktopWidgetHost {
            override val hostContext = activity
            override val isEditMode = false
            override val fontManager: FontManager? = null
            override fun getPreferences() = prefs
            override fun getRestTranslationX(view: View) = 0f
            override fun onMusicArtworkChanged(artworkSource: Any?) {}
            override fun onWidgetClicked(widget: DesktopWidgetController) {}
        }
        widget = MediaDesktopWidget(WidgetInstance("media", DesktopWidgetType.MEDIA), MediaWidgetDefinition, host)
        val view = MediaWidgetDefinition.createView(activity, parent)
        widget.bindView(view)
        parent.addView(view)
        widget.handleMusicStateUpdate(PluginState.Playing(MusicTrack("Track", "Artist", durationMs = 100_000L, positionMs = 10_000L)))
        widget.updateMediaStyle(MediaStyle.EXPANDED_PLAYER)
        idle(0)
    }

    @After fun cleanup() {
        if (::widget.isInitialized) widget.onDestroy()
        if (::activity.isInitialized) activity.finish()
    }

    @Test fun savingModeUpdatesEveryFiveSecondsUsingElapsedTime() {
        widget.onPowerSaveModeChanged(true)
        idle(0)
        assertFalse(widget.expandedProgressBar!!.isPlaying)
        val initialProgress = widget.expandedProgressBar!!.progress
        assertTrue(initialProgress >= 100)
        idle(4000)
        assertEquals(initialProgress, widget.expandedProgressBar!!.progress)
        idle(1000)
        assertEquals(initialProgress + 50, widget.expandedProgressBar!!.progress)
        widget.onPause()
        idle(5000)
        assertEquals(initialProgress + 50, widget.expandedProgressBar!!.progress)
        widget.onResume()
        idle(0)
        // UI animations may also advance Robolectric's clock during resume.
        // The tracker must catch up for the full pause rather than adding one tick.
        assertTrue(widget.expandedProgressBar!!.progress >= initialProgress + 100)
    }

    @Test fun idleCannotRestartAnimationOnPolicyChange() {
        widget.handleMusicStateUpdate(PluginState.Idle)
        widget.onPowerSaveModeChanged(true)
        widget.onPowerSaveModeChanged(false)
        assertFalse(widget.expandedProgressBar!!.isPlaying)
    }
}
