package com.nxd1frnt.clockdesk2.widgets

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuffColorFilter
import androidx.core.graphics.ColorUtils
import com.nxd1frnt.clockdesk2.utils.ArtworkColors
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.view.Gravity
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.ui.WidgetMover
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.ui.view.SquigglyProgressBar
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
    @Test fun artworkPlayerShowsTrackAndDispatchesTransportActions() {
        val cover = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        widget.handleMusicStateUpdate(PluginState.Playing(MusicTrack(
            "desolate", "vämpp", artworkBitmap = cover,
            durationMs = 100_000L, positionMs = 25_000L
        ), canControl = true))
        widget.updateMediaStyle(MediaStyle.ARTWORK_PLAYER)
        idle(0)
        val root = widget.rootView!!
        assertTrue(root.findViewById<View>(R.id.media_artwork_card).isShown)
        assertFalse(widget.mediaExpandedCard!!.isShown)
        assertFalse(widget.mediaCompactCard!!.isShown)
        assertEquals("desolate", root.findViewById<TextView>(R.id.artwork_title_text).text.toString())
        assertEquals("vämpp", root.findViewById<TextView>(R.id.artwork_artist_text).text.toString())
        assertSame(cover, (root.findViewById<ImageView>(R.id.artwork_cover_art).drawable as BitmapDrawable).bitmap)
        val progress = root.findViewById<SquigglyProgressBar>(R.id.artwork_progress_bar)
        assertEquals(250, progress.progress)
        assertTrue(progress.isPlaying)
        var actions = ""
        widget.onPrevAction = { actions += "previous " }
        widget.onPlayPauseAction = { actions += "toggle " }
        widget.onNextAction = { actions += "next" }
        root.findViewById<View>(R.id.btn_artwork_prev).performClick()
        root.findViewById<View>(R.id.btn_artwork_play_pause).performClick()
        root.findViewById<View>(R.id.btn_artwork_next).performClick()
        assertEquals("previous toggle next", actions)

        widget.onPowerSaveModeChanged(true)
        idle(0)
        assertFalse(progress.isPlaying)
        val savingProgress = progress.progress
        idle(4000)
        assertEquals(savingProgress, progress.progress)
        idle(1000)
        assertEquals(savingProgress + 50, progress.progress)
        widget.onPause()
        idle(5000)
        assertEquals(savingProgress + 50, progress.progress)
    }

    @Test fun artworkPlayerHandlesPauseAndMissingDuration() {
        widget.handleMusicStateUpdate(PluginState.Paused(MusicTrack("Paused", "Artist")))
        widget.updateMediaStyle(MediaStyle.ARTWORK_PLAYER)
        val root = widget.rootView!!
        val progress = root.findViewById<SquigglyProgressBar>(R.id.artwork_progress_bar)
        assertEquals(View.GONE, progress.visibility)
        assertFalse(progress.isPlaying)
        assertEquals(R.drawable.ic_play_arrow, root.findViewById<ImageView>(R.id.btn_artwork_play_pause).tag)
        assertEquals(View.GONE, root.findViewById<View>(R.id.artwork_source_label).visibility)
        widget.updateMediaStyle(MediaStyle.COMPACT_CARD)
        assertEquals(View.GONE, root.findViewById<View>(R.id.media_artwork_card).visibility)
        assertTrue(widget.mediaCompactCard!!.isShown)
        assertEquals(MediaStyle.ARTWORK_PLAYER, MediaStyle.fromId("ARTWORK_PLAYER"))
    }

    @Test fun artworkAccentFollowsCoverAndResetsWhenArtworkDisappears() {
        fun showCover(color: Int): Int {
            val cover = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            widget.handleMusicStateUpdate(PluginState.Playing(MusicTrack("Track", "Artist", artworkBitmap = cover)))
            widget.updateMediaStyle(MediaStyle.ARTWORK_PLAYER)
            val root = widget.rootView!!
            val button = root.findViewById<ImageView>(R.id.btn_artwork_play_pause)
            val accent = button.backgroundTintList!!.defaultColor
            assertTrue(ColorUtils.calculateContrast(button.imageTintList!!.defaultColor, accent) >= 4.5)
            val icon = root.findViewById<ImageView>(R.id.artwork_source_icon)
            assertEquals(accent, shadowOf(icon.colorFilter as PorterDuffColorFilter).color)
            // Source icon refreshes must preserve the artwork accent.
            widget.updateSourceIcon(MusicTrack("Track", "Artist"))
            assertEquals(accent, shadowOf(icon.colorFilter as PorterDuffColorFilter).color)
            return accent
        }
        val red = showCover(Color.RED)
        val blue = showCover(Color.BLUE)
        assertNotEquals(red, blue)
        widget.handleMusicStateUpdate(PluginState.Playing(MusicTrack("No cover", "Artist")))
        val button = widget.rootView!!.findViewById<ImageView>(R.id.btn_artwork_play_pause)
        assertEquals(ArtworkColors.DEFAULT.accent, button.backgroundTintList!!.defaultColor)
    }

    @Test fun artworkPlayerHonorsTypographyColorsOpacityAndWidth() {
        widget.updateMediaStyle(MediaStyle.ARTWORK_PLAYER)
        val root = widget.rootView!! as ViewGroup
        val manager = FontManager(activity, TextView(activity), TextView(activity), root,
            root.findViewById(R.id.now_playing_text), root.findViewById(R.id.lastfm_icon),
            TextView(activity), ImageView(activity), ConstraintLayout(activity), false)
        val button = root.findViewById<ImageView>(R.id.btn_artwork_play_pause)
        val accent = button.backgroundTintList!!.defaultColor
        manager.setFontIndex(root, 2)
        assertEquals(2, manager.getSettings(root)!!.fontIndex)
        manager.setVariationAxis(root, "wght", 650f)
        manager.setFontSize(root, 40f)
        manager.setFontColor(root, Color.YELLOW)
        manager.setFontAlpha(root, 0.4f)
        manager.setMaxWidthPercent(root, 25)
        manager.setSmartChipBackgroundColor(root, Color.BLUE)
        val ticker = root.findViewById<TextView>(R.id.now_playing_text)
        val title = root.findViewById<TextView>(R.id.artwork_title_text)
        val artist = root.findViewById<TextView>(R.id.artwork_artist_text)
        val label = root.findViewById<TextView>(R.id.artwork_source_label)
        assertSame(ticker.typeface, title.typeface)
        assertSame(ticker.typeface, artist.typeface)
        assertSame(ticker.typeface, label.typeface)
        assertEquals(ticker.fontVariationSettings, title.fontVariationSettings)
        assertEquals(ticker.fontVariationSettings, artist.fontVariationSettings)
        val metrics = activity.resources.displayMetrics
        assertEquals(40 * metrics.scaledDensity, title.textSize, 0.01f)
        assertEquals(32 * metrics.scaledDensity, artist.textSize, 0.01f)
        assertEquals(Color.YELLOW, title.currentTextColor)
        assertEquals(Color.YELLOW, artist.currentTextColor)
        val card = root.findViewById<View>(R.id.media_artwork_card)
        assertEquals(0.4f, card.alpha, 0f)
        assertEquals(1f, title.alpha, 0f)
        assertEquals(0.8f, artist.alpha, 0f)
        assertEquals(minOf((320 * metrics.density).toInt(), metrics.widthPixels / 4), card.layoutParams.width)
        assertTrue(card.layoutParams.height > 152 * metrics.density)
        val scrim = root.findViewById<View>(R.id.artwork_scrim).background as GradientDrawable
        assertEquals(Color.BLUE, scrim.colors!!.last() or Color.BLACK)
        assertEquals(accent, button.backgroundTintList!!.defaultColor)
        // Track refreshes and style changes must not restore hard-coded XML typography.
        widget.handleMusicStateUpdate(PluginState.Playing(MusicTrack("New track", "Artist")))
        widget.updateMediaStyle(MediaStyle.ARTWORK_PLAYER)
        assertEquals(40 * metrics.scaledDensity, title.textSize, 0.01f)
        assertEquals(Color.YELLOW, title.currentTextColor)
    }

    @Test fun artworkPlayerHonorsTextAlignment() {
        val root = widget.rootView!!
        val mover = WidgetMover(activity, listOf(root), root.parent as View, autoInitialize = false)
        for (mode in listOf(mover.GRAVITY_START, mover.GRAVITY_CENTER, mover.GRAVITY_END)) {
            mover.setTextGravity(root, mode)
            for (id in listOf(R.id.artwork_title_text, R.id.artwork_artist_text)) {
                val text = root.findViewById<TextView>(id)
                val expected = when (mode) {
                    mover.GRAVITY_START -> View.TEXT_ALIGNMENT_VIEW_START
                    mover.GRAVITY_END -> View.TEXT_ALIGNMENT_VIEW_END
                    else -> View.TEXT_ALIGNMENT_CENTER
                }
                assertEquals(expected, text.textAlignment)
            }
        }
    }

    @Test fun statusOnlyProvidersHideControlsAndGiveTrackTextTheAvailableWidth() {
        val root = widget.rootView!!
        val track = MusicTrack("Track", "Artist", durationMs = 100_000, positionMs = 10_000)
        val buttons = listOf(R.id.btn_media_prev, R.id.btn_media_play_pause, R.id.btn_media_next,
            R.id.btn_artwork_prev, R.id.btn_artwork_play_pause, R.id.btn_artwork_next)
        var commands = 0
        widget.onPrevAction = { commands++ }
        widget.onPlayPauseAction = { commands++ }
        widget.onNextAction = { commands++ }
        for (state in listOf(PluginState.Playing(track), PluginState.Paused(track))) {
            widget.handleMusicStateUpdate(state)
            for (style in listOf(MediaStyle.ARTWORK_PLAYER, MediaStyle.EXPANDED_PLAYER)) {
                widget.updateMediaStyle(style)
                for (id in buttons) {
                    val button = root.findViewById<View>(id)
                    assertEquals(View.GONE, button.visibility)
                    button.performClick()
                }
            }
            assertEquals(0, commands)
            val params = root.findViewById<View>(R.id.artwork_track_text).layoutParams as ViewGroup.MarginLayoutParams
            assertEquals((18 * activity.resources.displayMetrics.density).toInt(), params.marginEnd)
            assertEquals("Track", root.findViewById<TextView>(R.id.artwork_title_text).text.toString())
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.artwork_progress_bar).visibility)
        }
        widget.handleMusicStateUpdate(PluginState.Paused(track, canControl = true))
        widget.updateMediaStyle(MediaStyle.ARTWORK_PLAYER)
        buttons.forEach { id -> assertEquals(View.VISIBLE, root.findViewById<View>(id).visibility) }
        root.findViewById<View>(R.id.btn_artwork_play_pause).performClick()
        assertEquals(1, commands)
        widget.handleMusicStateUpdate(PluginState.Idle)
        buttons.forEach { id -> assertEquals(View.GONE, root.findViewById<View>(id).visibility) }
    }

}
