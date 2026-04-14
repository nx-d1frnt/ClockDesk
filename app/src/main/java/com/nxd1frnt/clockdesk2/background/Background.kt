package com.nxd1frnt.clockdesk2.background

import android.graphics.drawable.Drawable

data class Background(
    val id: String,
    val type: BackgroundType,
    val previewDrawable: Drawable?, // For card preview
    val data: Any // GradientDrawable for gradient, Uri/String for image, Bitmap for plugin
)

enum class BackgroundType {
    GRADIENT, IMAGE, PLUGIN
}