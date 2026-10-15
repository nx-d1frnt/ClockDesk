package com.nxd1frnt.clockdesk2.background.provider

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.nxd1frnt.clockdesk2.R

class ExternalBackgroundPlugin(
    val packageName: String,
    val receiverClassName: String,
    override val id: String,
    override val displayName: String,
    override val description: String,
    override val iconResId: Int = R.drawable.ic_layers,
    val settingsActivity: String? = null,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(),
    private var lastPayload: BackgroundPayload = BackgroundPayload.Gradient
) : IBackgroundProvider {

    fun updatePayload(payload: BackgroundPayload) {
        lastPayload = payload
    }

    override fun getPreviewDrawable(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, iconResId)
    }

    override fun getCurrentPayload(context: Context): BackgroundPayload {
        return lastPayload
    }
}
