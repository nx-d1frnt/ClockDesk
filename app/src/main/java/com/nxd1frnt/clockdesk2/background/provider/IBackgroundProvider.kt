package com.nxd1frnt.clockdesk2.background.provider

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

interface IBackgroundProvider {
    val id: String
    val displayName: String
    val description: String
    val iconResId: Int
    val capabilities: ProviderCapabilities
    val settingsFragmentClass: Class<out Fragment>? get() = null

    fun getIconDrawable(context: Context): Drawable? {
        return if (iconResId != 0) {
            try {
                ContextCompat.getDrawable(context, iconResId)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    fun getPreviewDrawable(context: Context): Drawable?
    fun getCurrentPayload(context: Context): BackgroundPayload
    fun onSelected(context: Context) {}
    fun onDeselected(context: Context) {}
}
