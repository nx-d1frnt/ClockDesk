package com.nxd1frnt.clockdesk2.music

import android.app.Application
import android.content.Context
import androidx.fragment.app.Fragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MusicPluginManagerTest {
    private class Plugin(override val id: String, override var canControl: Boolean) : IMusicPlugin {
        override val displayName = id
        override val description = id
        override val settingsFragmentClass: Class<out Fragment>? = null
        private var callback: ((PluginState) -> Unit)? = null
        var commands = 0
        override fun init() {}
        override fun destroy() {}
        override fun setCallback(callback: (PluginState) -> Unit) { this.callback = callback }
        fun emit(state: PluginState) { callback!!.invoke(state) }
        override fun play() { commands++ }
        override fun pause() { commands++ }
        override fun togglePlayPause() { commands++ }
        override fun next() { commands++ }
        override fun previous() { commands++ }
    }

    @Test fun controlsFollowSelectedProviderEvenWhenArtworkComesFromAnotherPlugin() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("music_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("music_provider_order", "status,player").commit()
        val status = Plugin("status", false)
        val player = Plugin("player", true)
        var output: PluginState = PluginState.Idle
        val manager = MusicPluginManager(context, prefs, listOf(status, player)) { output = it }
        try {
            player.emit(PluginState.Playing(MusicTrack("Track", "Artist", artworkUrl = "https://example.test/art.jpg")))
            assertTrue(output.canControl)
            // A status-only provider cannot grant itself controls through its update payload.
            status.emit(PluginState.Playing(MusicTrack("Track", "Artist"), canControl = true))
            assertFalse(output.canControl)
            assertEquals("https://example.test/art.jpg", (output as PluginState.Playing).track.artworkUrl)
            manager.play(); manager.pause(); manager.togglePlayPause(); manager.next(); manager.previous()
            assertEquals(0, status.commands)
            assertEquals(0, player.commands)
            status.emit(PluginState.Idle)
            assertTrue(output.canControl)
            manager.play(); manager.pause(); manager.togglePlayPause(); manager.next(); manager.previous()
            assertEquals(5, player.commands)
            player.emit(PluginState.Paused(MusicTrack("Track", "Artist")))
            assertTrue(output.canControl)
            player.canControl = false
            player.emit(PluginState.Paused(MusicTrack("Track", "Artist")))
            assertFalse(output.canControl)
            manager.togglePlayPause()
            assertEquals(5, player.commands)
            player.emit(PluginState.Idle)
            assertFalse(output.canControl)
        } finally {
            manager.destroy()
        }
    }
}
