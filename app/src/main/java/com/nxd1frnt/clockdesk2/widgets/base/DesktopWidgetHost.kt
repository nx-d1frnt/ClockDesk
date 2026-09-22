package com.nxd1frnt.clockdesk2.widgets.base

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import com.nxd1frnt.clockdesk2.utils.FontManager

interface DesktopWidgetHost {
    val hostContext: Context
    val isEditMode: Boolean
    val fontManager: FontManager?

    fun getRestTranslationX(view: View): Float
    fun onMusicArtworkChanged(artworkSource: Any?)
    fun onWidgetClicked(widget: DesktopWidgetController)
    fun getPreferences(): SharedPreferences
}
