package com.nxd1frnt.clockdesk2.connect.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.Logger

object DeskNotificationHelper {

    fun applyIcon(
        context: Context,
        iconView: ImageView,
        iconBytes: ByteArray?,
        notificationId: String,
        tintColor: Int? = null
    ) {
        var iconApplied = false

        if (iconBytes != null && iconBytes.isNotEmpty()) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
                if (bitmap != null) {
                    iconView.clearColorFilter()
                    ImageViewCompat.setImageTintList(iconView, null)
                    if (tintColor != null) {
                        val themed = createMonochromeThemedBitmap(bitmap, tintColor)
                        iconView.setImageBitmap(themed)
                        iconView.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                    } else {
                        iconView.setImageBitmap(bitmap)
                    }
                    iconApplied = true
                }
            } catch (e: Exception) {
                Logger.w("DeskNotificationHelper") { "Failed to decode notification icon: ${e.message}" }
            }
        }

        if (!iconApplied) {
            val pkg = if (notificationId.contains("|")) {
                notificationId.split("|").getOrNull(1)
            } else null

            if (!pkg.isNullOrEmpty()) {
                try {
                    val appIcon = context.packageManager.getApplicationIcon(pkg)
                    iconView.clearColorFilter()
                    ImageViewCompat.setImageTintList(iconView, null)
                    if (tintColor != null) {
                        val bmp = drawableToBitmap(appIcon, 96, 96)
                        val themed = createMonochromeThemedBitmap(bmp, tintColor)
                        iconView.setImageBitmap(themed)
                        iconView.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                    } else {
                        iconView.setImageDrawable(appIcon)
                    }
                    iconApplied = true
                } catch (ignored: Exception) {}
            }
        }

        if (!iconApplied) {
            val effectiveTint = tintColor
                ?: com.google.android.material.color.MaterialColors.getColor(
                    iconView,
                    com.google.android.material.R.attr.colorOnSurface,
                    Color.WHITE
                )
            iconView.setImageResource(R.drawable.ic_notifications)
            iconView.setColorFilter(effectiveTint, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun drawableToBitmap(drawable: Drawable, width: Int = 96, height: Int = 96): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else width
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun createMonochromeThemedBitmap(source: Bitmap, tintColor: Int): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val totalPixels = width * height

        // 1. Check if the image already has significant transparency (e.g. status bar silhouette)
        var transparentCount = 0
        for (p in pixels) {
            if (Color.alpha(p) < 40) transparentCount++
        }
        val isAlreadyTransparentSilhouette = (transparentCount.toFloat() / totalPixels) > 0.20f

        val tr = Color.red(tintColor)
        val tg = Color.green(tintColor)
        val tb = Color.blue(tintColor)

        if (isAlreadyTransparentSilhouette) {
            // Already has transparent background: tint all visible pixels to tintColor preserving alpha
            for (i in pixels.indices) {
                val a = Color.alpha(pixels[i])
                if (a > 0) {
                    pixels[i] = Color.argb(a, tr, tg, tb)
                }
            }
        } else {
            // Opaque / filled icon (e.g. adaptive launcher icon with solid background)
            // Sample edge pixels to determine background color
            val edgePixels = mutableListOf<Int>()
            for (x in 0 until width step 2) {
                edgePixels.add(pixels[x])
                edgePixels.add(pixels[(height - 1) * width + x])
            }
            for (y in 0 until height step 2) {
                edgePixels.add(pixels[y * width])
                edgePixels.add(pixels[y * width + width - 1])
            }

            var sumR = 0L; var sumG = 0L; var sumB = 0L
            for (p in edgePixels) {
                sumR += Color.red(p)
                sumG += Color.green(p)
                sumB += Color.blue(p)
            }
            val edgeCount = edgePixels.size.coerceAtLeast(1)
            val bgR = (sumR / edgeCount).toInt()
            val bgG = (sumG / edgeCount).toInt()
            val bgB = (sumB / edgeCount).toInt()
            val bgLum = bgR * 0.299f + bgG * 0.587f + bgB * 0.114f

            var foregroundPixelCount = 0
            val threshold = 32f

            for (i in pixels.indices) {
                val p = pixels[i]
                val a = Color.alpha(p)
                if (a < 40) {
                    pixels[i] = Color.TRANSPARENT
                    continue
                }
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val lum = r * 0.299f + g * 0.587f + b * 0.114f
                val lumDiff = Math.abs(lum - bgLum)
                val colorDiff = (Math.abs(r - bgR) + Math.abs(g - bgG) + Math.abs(b - bgB)) / 3f

                val maxDiff = Math.max(lumDiff, colorDiff)
                if (maxDiff > threshold) {
                    foregroundPixelCount++
                    // Smooth alpha transition for anti-aliasing
                    val alphaRatio = ((maxDiff - threshold) / 40f).coerceIn(0f, 1f)
                    val glyphAlpha = (a * alphaRatio).toInt().coerceIn(0, 255)
                    pixels[i] = Color.argb(glyphAlpha, tr, tg, tb)
                } else {
                    pixels[i] = Color.TRANSPARENT
                }
            }

            // Fallback: if very few foreground pixels were detected (< 3%),
            // use luminance contrast mapping
            if (foregroundPixelCount < totalPixels * 0.03f) {
                for (i in pixels.indices) {
                    val p = source.getPixel(i % width, i / width)
                    val a = Color.alpha(p)
                    val lum = Color.red(p) * 0.299f + Color.green(p) * 0.587f + Color.blue(p) * 0.114f
                    val glyphAlpha = if (bgLum > 128f) {
                        ((255f - lum) / 255f * a).toInt().coerceIn(0, 255)
                    } else {
                        (lum / 255f * a).toInt().coerceIn(0, 255)
                    }
                    pixels[i] = Color.argb(glyphAlpha, tr, tg, tb)
                }
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
