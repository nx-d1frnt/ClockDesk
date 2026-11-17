package com.nxd1frnt.clockdesk2.alarms

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AlarmSoundPlayerTest {
    @Test fun customMelodyLoopsRampsAndReleasesOnStop() {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://media/internal/audio/media/42")
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(context, uri), ShadowMediaPlayer.MediaInfo(60_000, 0))
        val sound = AlarmSoundPlayer(context)
        try {
            sound.play(DeskAlarm(hour = 7, minute = 0, soundUri = uri.toString(), volumePercent = 50, fadeInSeconds = 2))
            shadowOf(Looper.getMainLooper()).idle()
            val media = ReflectionHelpers.getField<MediaPlayer>(sound, "player")
            val shadow = shadowOf(media)
            assertEquals(uri, shadow.sourceUri)
            assertEquals(AudioAttributes.USAGE_ALARM, shadow.audioAttributes.usage)
            assertTrue(media.isLooping)
            assertTrue(sound.isPlaying)
            assertEquals(0.025f, shadow.leftVolume, 0.001f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            assertEquals(0.5f, shadow.leftVolume, 0.001f)
            sound.stop()
            assertFalse(sound.isPlaying)
            assertEquals(ShadowMediaPlayer.State.END, shadow.state)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            assertEquals(ShadowMediaPlayer.State.END, shadow.state)
        } finally { sound.stop() }
    }

    @Test fun missingMelodyFallsBackToDefaultAndPendingPlaybackCanBeCancelled() {
        val context = RuntimeEnvironment.getApplication()
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 100) }
        val broken = Uri.parse("content://missing/alarm")
        ShadowMediaPlayer.addException(DataSource.toDataSource(context, broken), java.io.IOException("Gone"))
        val sound = AlarmSoundPlayer(context)
        try {
            sound.play(DeskAlarm(hour = 7, minute = 0, soundUri = broken.toString()))
            val media = ReflectionHelpers.getField<MediaPlayer>(sound, "player")
            val shadow = shadowOf(media)
            assertNotEquals(broken, shadow.sourceUri)
            sound.stop()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertFalse(sound.isPlaying)
            assertEquals(ShadowMediaPlayer.State.END, shadow.state)
        } finally { sound.stop() }
    }
}
