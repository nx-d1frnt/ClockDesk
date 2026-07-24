package com.nxd1frnt.clockdesk2.music

import android.graphics.Bitmap

data class CustomMediaAction(
    val id: String,
    val name: String,
    val iconRes: Int? = null,
    val isLiked: Boolean = false
)

data class MusicTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val artworkBitmap: Bitmap? = null,
    val sourcePackageName: String? = null,
    val sourceIconBitmap: Bitmap? = null,
    val sourceIconUri: String? = null
)

sealed class PluginState {
    data class Playing(val track: MusicTrack) : PluginState()
    object Idle : PluginState()
    object Disabled : PluginState()
}

data class PlaybackInfo(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val canPlayPause: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canSeek: Boolean = false,
    val isLiked: Boolean = false,
    val canLike: Boolean = false,
    val customActions: List<CustomMediaAction> = emptyList()
)

interface IMusicPlugin {
    val id: String
    val displayName: String
    val description: String

    val settingsFragmentClass: Class<out androidx.fragment.app.Fragment>?

    fun init()
    fun destroy()
    fun setCallback(callback: (PluginState) -> Unit)

    fun getPlaybackInfo(): PlaybackInfo = PlaybackInfo()
    fun play() {}
    fun pause() {}
    fun togglePlayPause() {}
    fun skipToNext() {}
    fun skipToPrevious() {}
    fun seekTo(positionMs: Long) {}
    fun toggleLike() {}
    fun performCustomAction(actionId: String) {}
}