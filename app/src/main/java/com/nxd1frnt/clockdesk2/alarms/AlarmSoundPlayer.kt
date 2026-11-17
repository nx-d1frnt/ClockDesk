package com.nxd1frnt.clockdesk2.alarms

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** One player for a real alarm or an editor preview; never changes the device volume. */
class AlarmSoundPlayer(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var ramp: Runnable? = null
    var isPlaying: Boolean = false
        private set

    fun play(alarm: DeskAlarm, onFailure: (() -> Unit)? = null) {
        stop()
        if (!alarm.soundEnabled) return
        val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
        val sources = listOfNotNull(alarm.soundUri?.let(Uri::parse), default).distinct()
        fun trySource(index: Int) {
            if (index >= sources.size) {
                stop()
                onFailure?.invoke()
                return
            }
            val media = MediaPlayer()
            player = media
            fun failed() {
                if (player !== media) return
                ramp?.let(handler::removeCallbacks)
                ramp = null
                media.release()
                player = null
                trySource(index + 1)
            }
            try {
                media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                media.setDataSource(context, sources[index])
                media.setOnErrorListener { _, _, _ -> failed(); true }
                media.setOnPreparedListener {
                    if (player !== media) return@setOnPreparedListener
                    try {
                        media.isLooping = true
                        val started = SystemClock.elapsedRealtime()
                        val update = object : Runnable {
                            override fun run() {
                                if (player !== media) return
                                val elapsed = SystemClock.elapsedRealtime() - started
                                val volume = AlarmScheduler.playbackVolume(alarm, elapsed)
                                media.setVolume(volume, volume)
                                if (elapsed < alarm.fadeInSeconds * 1000L) handler.postDelayed(this, 100L)
                            }
                        }
                        ramp = update
                        update.run()
                        media.start()
                        isPlaying = true
                    } catch (e: Exception) { failed() }
                }
                media.prepareAsync()
            } catch (e: Exception) {
                Log.w("DeskAlarms", "Cannot load alarm sound", e)
                failed()
            }
        }
        trySource(0)
    }

    fun stop() {
        ramp?.let(handler::removeCallbacks)
        ramp = null
        player?.release()
        player = null
        isPlaying = false
    }
}
