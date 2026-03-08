package com.nxd1frnt.clockdesk2.shared.ui

import android.graphics.drawable.Drawable

data class PluginUiModel(
    val id: String,
    val name: String,
    val desc: String,
    val settingsActivityClassName: String? = null,
    var isEnabled: Boolean,
    val iconDrawable: Drawable? = null,
    val isExternal: Boolean = false
) {
    val hasSettings: Boolean
        get() = settingsActivityClassName != null || id == "lastfm" || id == "system_media"
}