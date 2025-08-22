package com.nxd1frnt.clockdesk2

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

data class Background(
    val id: String,
    val type: BackgroundType,
    val previewDrawable: Drawable?, // For card preview
    val data: Any // GradientDrawable for gradient, Uri/String for image
)

enum class BackgroundType {
    GRADIENT, IMAGE
}