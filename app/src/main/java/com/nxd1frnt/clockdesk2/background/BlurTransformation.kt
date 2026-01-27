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
import java.security.MessageDigest

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
        // If radius is zero or negative, return the original bitmap
        if (radius <= 0) return toTransform

        val width = toTransform.width
        val height = toTransform.height
        val scaledWidth = width / sampling
        val scaledHeight = height / sampling

        var bitmap = pool.get(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)

        if (bitmap.isRecycled) {
            bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        }

        // --- Scale down the image ---
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(1 / sampling.toFloat(), 1 / sampling.toFloat())
        val paint = android.graphics.Paint()
        paint.flags = android.graphics.Paint.FILTER_BITMAP_FLAG
        canvas.drawBitmap(toTransform, 0f, 0f, paint)

        // --- Apply RenderScript blur ---
        var rs: RenderScript? = null
        var input: Allocation? = null
        var output: Allocation? = null
        var script: ScriptIntrinsicBlur? = null

        try {
            rs = RenderScript.create(context)

            input = Allocation.createFromBitmap(rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT)
            output = Allocation.createTyped(rs, input.type)

            // create the blur script
            script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

            // RenderScript supports radius 0..25.
            // If more is needed, use downsampling (reduce image size).
            script.setRadius(radius.coerceIn(1, 25).toFloat())

            script.setInput(input)
            script.forEach(output)

            output.copyTo(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // RS resource cleanup
            rs?.destroy()
            input?.destroy()
            output?.destroy()
            script?.destroy()
        }

        return bitmap
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