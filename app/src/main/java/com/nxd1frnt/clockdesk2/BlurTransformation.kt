package com.nxd1frnt.clockdesk2

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.util.Preconditions
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

class BlurTransformation(private val radius: Int = 25) : BitmapTransformation() {
    init {
        Preconditions.checkArgument(radius >= 0, "radius must be >= 0")
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("BlurTransformation($radius)".toByteArray(Charsets.UTF_8))
    }

    override fun equals(other: Any?): Boolean {
        return other is BlurTransformation && other.radius == this.radius
    }

    override fun hashCode(): Int {
        return "BlurTransformation($radius)".hashCode()
    }

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        if (radius <= 0) return toTransform.copy(Bitmap.Config.ARGB_8888, false)

        val w = toTransform.width
        val h = toTransform.height

        // Get a bitmap from the pool if available, otherwise create new
        val workingBitmap = pool.get(w, h, Bitmap.Config.ARGB_8888)
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(w * h)
        toTransform.getPixels(pixels, 0, w, 0, 0, w, h)

        // Apply multiple passes of box blur to approximate Gaussian blur
        val passes = 3
        val adjustedRadius = radius / passes

        var currentPixels = pixels
        var tempPixels = IntArray(w * h)

        for (pass in 0 until passes) {
            // Horizontal pass
            for (y in 0 until h) {
                val rowStart = y * w
                for (x in 0 until w) {
                    val left = max(0, x - adjustedRadius)
                    val right = min(w - 1, x + adjustedRadius)
                    val kernelSize = right - left + 1

                    var sumA = 0
                    var sumR = 0
                    var sumG = 0
                    var sumB = 0

                    for (i in left..right) {
                        val pixel = currentPixels[rowStart + i]
                        sumA += (pixel ushr 24) and 0xFF
                        sumR += (pixel ushr 16) and 0xFF
                        sumG += (pixel ushr 8) and 0xFF
                        sumB += pixel and 0xFF
                    }

                    val avgA = sumA / kernelSize
                    val avgR = sumR / kernelSize
                    val avgG = sumG / kernelSize
                    val avgB = sumB / kernelSize

                    tempPixels[rowStart + x] = (avgA shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                }
            }

            // Vertical pass
            for (x in 0 until w) {
                for (y in 0 until h) {
                    val top = max(0, y - adjustedRadius)
                    val bottom = min(h - 1, y + adjustedRadius)
                    val kernelSize = bottom - top + 1

                    var sumA = 0
                    var sumR = 0
                    var sumG = 0
                    var sumB = 0

                    for (i in top..bottom) {
                        val pixel = tempPixels[i * w + x]
                        sumA += (pixel ushr 24) and 0xFF
                        sumR += (pixel ushr 16) and 0xFF
                        sumG += (pixel ushr 8) and 0xFF
                        sumB += pixel and 0xFF
                    }

                    val avgA = sumA / kernelSize
                    val avgR = sumR / kernelSize
                    val avgG = sumG / kernelSize
                    val avgB = sumB / kernelSize

                    currentPixels[y * w + x] = (avgA shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                }
            }
        }

        workingBitmap.setPixels(currentPixels, 0, w, 0, 0, w, h)
        return workingBitmap
    }
}