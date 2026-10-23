package com.nxd1frnt.clockdesk2.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.google.android.material.color.utilities.Hct

/** Light artwork accents remain readable over the player's dark scrim. */
data class ArtworkColors(val accent: Int, val onAccent: Int) {
    companion object {
        val DEFAULT = ArtworkColors(Color.rgb(162, 245, 195), Color.rgb(0, 57, 30))

        fun fromBitmap(bitmap: Bitmap): ArtworkColors {
            if (bitmap.isRecycled) return DEFAULT
            // Bound quantization work even when the media source supplies a large cover.
            val palette = Palette.from(bitmap).resizeBitmapArea(32 * 32).maximumColorCount(16)
                .clearFilters().generate()
            val seed = palette.dominantSwatch?.rgb ?: return DEFAULT
            val accent = Hct.fromInt(seed).apply { tone = 80.0 }.toInt()
            val onAccent = if (ColorUtils.calculateContrast(Color.BLACK, accent) >=
                ColorUtils.calculateContrast(Color.WHITE, accent)) Color.BLACK else Color.WHITE
            return ArtworkColors(accent, onAccent)
        }
    }
}
