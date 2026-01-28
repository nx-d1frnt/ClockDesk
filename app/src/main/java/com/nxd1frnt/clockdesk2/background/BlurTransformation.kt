package com.nxd1frnt.clockdesk2.background

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.nxd1frnt.clockdesk2.utils.Logger
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BlurTransformation(
    private val context: Context,
    private val radius: Int = 25,
    private val sampling: Int = 1 // downsampling factor: 1 = original size, 2 = half size, etc.
) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("BlurTransformation(radius=$radius, sampling=$sampling)".toByteArray(Charsets.UTF_8))
    }

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        if (radius <= 0) return toTransform

        val width = toTransform.width
        val height = toTransform.height
        val scaledWidth = width / sampling
        val scaledHeight = height / sampling

        var bitmap = pool.get(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        if (bitmap.isRecycled) {
            bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        }

        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(1 / sampling.toFloat(), 1 / sampling.toFloat())
        val paint = android.graphics.Paint()
        paint.flags = android.graphics.Paint.FILTER_BITMAP_FLAG
        canvas.drawBitmap(toTransform, 0f, 0f, paint)

        //Algorithm selection based on Android version
        var applied = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                applyRenderScriptBlur(context, bitmap, radius)
                Logger.d("BlurTransformation") { "Applied RenderScript blur with radius $radius" }
                applied = true
            } catch (e: Throwable) {
                applied = false
            }
        }

        if (!applied) {
            applyKotlinBoxBlur(bitmap, radius)
            Logger.d("BlurTransformation") { "Applied Kotlin Box Blur with radius $radius" }
        }

        return bitmap
    }


    private fun applyRenderScriptBlur(context: Context, bitmap: Bitmap, radius: Int) {
        var rs: RenderScript? = null
        var input: Allocation? = null
        var output: Allocation? = null
        var script: ScriptIntrinsicBlur? = null

        try {
            rs = RenderScript.create(context)
            input = Allocation.createFromBitmap(rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT)
            output = Allocation.createTyped(rs, input.type)
            script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

            script.setRadius(radius.coerceIn(1, 25).toFloat())
            script.setInput(input)
            script.forEach(output)
            output.copyTo(bitmap)
        } finally {
            rs?.destroy()
            input?.destroy()
            output?.destroy()
            script?.destroy()
        }
    }


    // Box Blur algorithm as a fallback for devices where RenderScript is not available
    private fun applyKotlinBoxBlur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)

        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val passes = 3
        val adjustedRadius = max(1, radius / passes)

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

                    var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0

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

                    var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0

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

        bitmap.setPixels(currentPixels, 0, w, 0, 0, w, h)
    }

    override fun equals(other: Any?): Boolean {
        return other is BlurTransformation &&
                other.radius == radius &&
                other.sampling == sampling
    }

    override fun hashCode(): Int {
        return "BlurTransformation($radius,$sampling)".hashCode()
    }
}