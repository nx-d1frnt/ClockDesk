package com.nxd1frnt.clockdesk2.background.provider

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.background.BackgroundManager

class ImageBackgroundProvider : IBackgroundProvider {

    companion object {
        const val ID = "internal:image"
    }

    override val id: String = ID
    override val displayName: String = "Custom Photos"
    override val description: String = "Photos from your gallery"
    override val iconResId: Int = R.drawable.baseline_add_photo_alternate_24

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsBlur = true,
        supportsCrop = true,
        supportsDimming = true,
        supportsWeather = true,
        supportsCustomColor = false,
        supportsConfig = false
    )

    override fun getPreviewDrawable(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.baseline_add_photo_alternate_24)
    }

    override fun getCurrentPayload(context: Context): BackgroundPayload {
        val bgManager = BackgroundManager(context)
        val uriStr = bgManager.getSavedBackgroundUri()
        return if (!uriStr.isNullOrEmpty()) {
            BackgroundPayload.ImageUri(uriStr)
        } else {
            BackgroundPayload.Gradient
        }
    }
}
