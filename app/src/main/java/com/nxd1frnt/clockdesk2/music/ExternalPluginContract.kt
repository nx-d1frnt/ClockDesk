package com.nxd1frnt.clockdesk2.music

object ExternalPluginContract {
    const val ACTION_MUSIC_PLUGIN_SERVICE = "com.nxd1frnt.clockdesk2.music.PLUGIN"

    const val META_DATA_PLUGIN_INFO = "com.nxd1frnt.clockdesk2.music.PLUGIN_INFO"
    const val ACTION_UPDATE_STATE = "com.nxd1frnt.clockdesk2.music.UPDATE_STATE"

    const val KEY_IS_PLAYING = "is_playing"
    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_ALBUM = "album"
    const val KEY_ART_URL = "art_url"
    const val KEY_PACKAGE_NAME = "source_package"

    const val ACTION_MEDIA_CONTROL = "com.nxd1frnt.clockdesk2.music.MEDIA_CONTROL"
    const val KEY_CONTROL_ACTION = "control_action"
    const val KEY_SEEK_POSITION = "seek_position"

    const val CONTROL_PLAY = "play"
    const val CONTROL_PAUSE = "pause"
    const val CONTROL_TOGGLE_PLAY_PAUSE = "toggle_play_pause"
    const val CONTROL_SKIP_NEXT = "skip_next"
    const val CONTROL_SKIP_PREVIOUS = "skip_previous"
    const val CONTROL_SEEK = "seek"
}