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