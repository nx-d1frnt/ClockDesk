package com.nxd1frnt.clockdesk2.background.provider

import android.graphics.Bitmap

sealed class BackgroundPayload {
    data class SolidColor(val color: Int) : BackgroundPayload()
    data class ImageUri(val uriString: String) : BackgroundPayload()
    data class ImageBitmap(val bitmap: Bitmap) : BackgroundPayload()
    object Gradient : BackgroundPayload()
}
