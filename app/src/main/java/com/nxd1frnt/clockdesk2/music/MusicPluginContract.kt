package com.nxd1frnt.clockdesk2.music

import android.graphics.Bitmap

data class MusicTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val artworkBitmap: Bitmap? = null,
    val sourcePackageName: String? = null,
    val sourceIconBitmap: Bitmap? = null,
    val sourceIconUri: String? = null,
    val sourceIconResId: Int? = null,
    val durationMs: Long? = null,
    val positionMs: Long? = null
)

sealed class PluginState {
    open val canControl: Boolean get() = false
    data class Playing(val track: MusicTrack, override val canControl: Boolean = false) : PluginState()
    data class Paused(val track: MusicTrack, override val canControl: Boolean = false) : PluginState()
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

    fun play() {}
    fun pause() {}
    fun togglePlayPause() {}
    fun next() {}
    fun previous() {}
    val canControl: Boolean get() = false
}