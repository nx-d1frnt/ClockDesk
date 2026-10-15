package com.nxd1frnt.clockdesk2.background.provider

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import com.nxd1frnt.clockdesk2.R

class SolidColorBackgroundProvider : IBackgroundProvider {

    companion object {
        const val ID = "internal:solid_color"
        private const val PREFS_NAME = "ClockDeskPrefs"
        private const val KEY_SOLID_COLOR = "background_solid_color"
        val DEFAULT_COLOR = Color.BLACK
    }

    override val id: String = ID
    override val displayName: String = "Solid Color"
    override val description: String = "AMOLED Black or custom color"
    override val iconResId: Int = R.drawable.ic_palette_swatch

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsBlur = false,
        supportsCrop = false,
        supportsDimming = true,
        supportsWeather = true,
        supportsCustomColor = true,
        supportsConfig = true
    )

    fun getColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SOLID_COLOR, DEFAULT_COLOR)
    }

    fun setColor(context: Context, color: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SOLID_COLOR, color).apply()
    }

    override fun getPreviewDrawable(context: Context): Drawable {
        val color = getColor(context)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
        }
    }

    override fun getCurrentPayload(context: Context): BackgroundPayload {
        return BackgroundPayload.SolidColor(getColor(context))
    }
}
