package com.nxd1frnt.clockdesk2.music

import android.graphics.Bitmap

data class MusicTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val artworkBitmap: Bitmap? = null,
    val sourcePackageName: String? = null
)

sealed class PluginState {
    data class Playing(val track: MusicTrack) : PluginState()
    object Idle : PluginState()
    object Disabled : PluginState()
}

interface IMusicPlugin {
    val id: String
    val displayName: String
    val description: String

    val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>?

    fun init()
    fun destroy()
    fun setCallback(callback: (PluginState) -> Unit)
}