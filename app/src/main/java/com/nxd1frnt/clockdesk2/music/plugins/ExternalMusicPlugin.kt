package com.nxd1frnt.clockdesk2.music.plugins

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.nxd1frnt.clockdesk2.music.ExternalPluginContract
import com.nxd1frnt.clockdesk2.music.IMusicPlugin
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.music.PluginState


class ExternalMusicPlugin(
    private val context: Context,
    override val id: String,
    override val displayName: String,
    override val description: String
) : IMusicPlugin {

    override val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>? = null

    private var callback: ((PluginState) -> Unit)? = null

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ExternalPluginContract.ACTION_UPDATE_STATE) {
                val senderPackage = intent.getStringExtra(ExternalPluginContract.KEY_PACKAGE_NAME)
                if (senderPackage != id) return

                processUpdate(intent)
            }
        }
    }

    override fun init() {
        val filter = IntentFilter(ExternalPluginContract.ACTION_UPDATE_STATE)
        ContextCompat.registerReceiver(
            context,
            dataReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

    }

    private fun processUpdate(intent: Intent) {
        val isPlaying = intent.getBooleanExtra(ExternalPluginContract.KEY_IS_PLAYING, false)
        lastIsPlaying = isPlaying

        if (isPlaying) {
            val track = MusicTrack(
                title = intent.getStringExtra(ExternalPluginContract.KEY_TITLE) ?: "Unknown",
                artist = intent.getStringExtra(ExternalPluginContract.KEY_ARTIST) ?: "Unknown",
                album = intent.getStringExtra(ExternalPluginContract.KEY_ALBUM),
                artworkUrl = intent.getStringExtra(ExternalPluginContract.KEY_ART_URL),
                sourcePackageName = id
            )
            callback?.invoke(PluginState.Playing(track))
        } else {
            callback?.invoke(PluginState.Idle)
        }
    }

    private var lastIsPlaying = false

    override fun getPlaybackInfo(): com.nxd1frnt.clockdesk2.music.PlaybackInfo {
        return com.nxd1frnt.clockdesk2.music.PlaybackInfo(
            isPlaying = lastIsPlaying,
            canPlayPause = true,
            canSkipNext = true,
            canSkipPrevious = true,
            canSeek = false
        )
    }

    private fun sendControlIntent(action: String, positionMs: Long = 0L) {
        val intent = Intent(ExternalPluginContract.ACTION_MEDIA_CONTROL).apply {
            setPackage(id)
            putExtra(ExternalPluginContract.KEY_CONTROL_ACTION, action)
            if (action == ExternalPluginContract.CONTROL_SEEK) {
                putExtra(ExternalPluginContract.KEY_SEEK_POSITION, positionMs)
            }
        }
        context.sendBroadcast(intent)
    }

    override fun play() {
        sendControlIntent(ExternalPluginContract.CONTROL_PLAY)
    }

    override fun pause() {
        sendControlIntent(ExternalPluginContract.CONTROL_PAUSE)
    }

    override fun togglePlayPause() {
        sendControlIntent(ExternalPluginContract.CONTROL_TOGGLE_PLAY_PAUSE)
    }

    override fun skipToNext() {
        sendControlIntent(ExternalPluginContract.CONTROL_SKIP_NEXT)
    }

    override fun skipToPrevious() {
        sendControlIntent(ExternalPluginContract.CONTROL_SKIP_PREVIOUS)
    }

    override fun seekTo(positionMs: Long) {
        sendControlIntent(ExternalPluginContract.CONTROL_SEEK, positionMs)
    }

    override fun setCallback(callback: (PluginState) -> Unit) {
        this.callback = callback
    }

    override fun destroy() {
        try {
            context.unregisterReceiver(dataReceiver)
        } catch (e: Exception) {
        }
    }
}