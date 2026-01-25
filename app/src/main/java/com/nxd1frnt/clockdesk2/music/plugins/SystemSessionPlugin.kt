package com.nxd1frnt.clockdesk2.music.plugins

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import androidx.fragment.app.Fragment
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.music.ClockDeskMediaService
import com.nxd1frnt.clockdesk2.music.IMusicPlugin
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.music.PluginState
import com.nxd1frnt.clockdesk2.utils.Logger

class SystemSessionPlugin(private val context: Context) : IMusicPlugin {
    override val id = "system_media"
    override val displayName = context.getString(R.string.system_media_plugin_name)
    override val description = context.getString(R.string.system_media_plugin_description)
    override val settingsFragmentClass: Class<out Fragment>? = null

    private var callback: ((PluginState) -> Unit)? = null
    private var isEnabled = true
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val componentName = ComponentName(context, ClockDeskMediaService::class.java)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        processControllers(controllers)
    }

    private val callbackMap = mutableMapOf<MediaController, MediaController.Callback>()

    override fun init() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        isEnabled = prefs.getBoolean("enable_$id", true)

        if (isEnabled) {
            startSessionMonitoring()
        }

        prefs.registerOnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == "enable_$id") {
                isEnabled = sharedPrefs.getBoolean(key, true)
                if (isEnabled) startSessionMonitoring() else stopSessionMonitoring()
            }
        }
    }

    private fun startSessionMonitoring() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            processControllers(controllers)
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
        } catch (e: SecurityException) {
            callback?.invoke(PluginState.Disabled)
        } catch (e: Exception) {
            Logger.e("SystemMediaPlugin"){"Error starting monitoring"}
        }
    }

    private fun stopSessionMonitoring() {
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            callbackMap.keys.forEach { it.unregisterCallback(callbackMap[it]!!) }
            callbackMap.clear()
            callback?.invoke(PluginState.Disabled)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun processControllers(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            callback?.invoke(PluginState.Idle)
            return
        }

        val activeController = controllers.firstOrNull {
            val state = it.playbackState?.state ?: PlaybackState.STATE_NONE
            state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        } ?: controllers.firstOrNull()

        if (activeController != null) {
            registerCallback(activeController)
            updateStateFromController(activeController)
        } else {
            callback?.invoke(PluginState.Idle)
        }
    }

    private fun registerCallback(controller: MediaController) {
        callbackMap.keys.forEach {
            try { it.unregisterCallback(callbackMap[it]!!) } catch (e: Exception) {}
        }
        callbackMap.clear()

        val cb = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                updateStateFromController(controller)
            }
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                updateStateFromController(controller)
            }
            override fun onSessionDestroyed() {
                try {
                    val controllers = mediaSessionManager.getActiveSessions(componentName)
                    processControllers(controllers)
                } catch (e: Exception) {
                    callback?.invoke(PluginState.Idle)
                }
            }
        }
        controller.registerCallback(cb)
        callbackMap[controller] = cb
    }

    private fun updateStateFromController(controller: MediaController) {
        val playbackState = controller.playbackState
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING ||
                playbackState?.state == PlaybackState.STATE_BUFFERING

        if (isPlaying) {
            val meta = controller.metadata

            val bitmap = meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)

            val artUri = meta?.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)

            Logger.d("SystemMediaPlugin"){"Update: ${controller.packageName}, hasBitmap=${bitmap != null}, uri=$artUri"}

            val track = MusicTrack(
                title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown",
                artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                artworkBitmap = bitmap,
                artworkUrl = artUri,
                sourcePackageName = controller.packageName
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
        stopSessionMonitoring()
    }
}