package com.nxd1frnt.clockdesk2.background.provider

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.nxd1frnt.clockdesk2.R

class DynamicGradientBackgroundProvider : IBackgroundProvider {

    companion object {
        const val ID = "internal:gradient"
    }

    override val id: String = ID
    override val displayName: String = "Dynamic Gradient"
    override val description: String = "Sun-based day/night gradient"
    override val iconResId: Int = R.drawable.ic_sun_clock_outline

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsBlur = true,
        supportsCrop = false,
        supportsDimming = true,
        supportsWeather = true,
        supportsCustomColor = false,
        supportsConfig = false
    )

    override fun getPreviewDrawable(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.gradient_background)
    }

    override fun getCurrentPayload(context: Context): BackgroundPayload {
        return BackgroundPayload.Gradient
    }
}
