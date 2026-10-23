package com.nxd1frnt.clockdesk2.desks.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import java.util.WeakHashMap

/** Foreground on wallpaper is independent of the theme used for opaque cards. */
object DeskForegroundStyle {
    private val colors = WeakHashMap<Context, Int>()

    private fun owner(context: Context): Context =
        if (context !is Activity && context is ContextWrapper && context.baseContext !== context) owner(context.baseContext) else context

    fun update(context: Context, color: Int): Boolean = colors.put(owner(context), color) != color

    fun sample(bitmap: Bitmap): IntArray = IntArray(64) { index ->
        val x = ((index % 8 + 0.5f) * bitmap.width / 8).toInt().coerceAtMost(bitmap.width - 1)
        val y = ((index / 8 + 0.5f) * bitmap.height / 8).toInt().coerceAtMost(bitmap.height - 1)
        bitmap.getPixel(x, y)
    }

    fun contrastingColor(samples: IntArray, matrix: ColorMatrix): Int {
        if (samples.isEmpty()) return Color.WHITE
        val m = matrix.array
        val luminance = samples.map { color ->
            val channels = floatArrayOf(Color.red(color).toFloat(), Color.green(color).toFloat(),
                Color.blue(color).toFloat(), Color.alpha(color).toFloat())
            fun channel(row: Int): Int = (m[row + 4] + (0..3).sumOf { (m[row + it] * channels[it]).toDouble() })
                .toInt().coerceIn(0, 255)
            ColorUtils.calculateLuminance(Color.rgb(channel(0), channel(5), channel(10)))
        }.average()
        val blackContrast = (luminance + 0.05) / 0.05
        val whiteContrast = 1.05 / (luminance + 0.05)
        return if (blackContrast >= whiteContrast) Color.BLACK else Color.WHITE
    }

    fun applyText(view: TextView) {
        val color = colors[owner(view.context)] ?: Color.WHITE
        view.setTextColor(color)
        // A small opposing halo keeps glyph edges visible over detailed photographs.
        view.setShadowLayer(2f * view.resources.displayMetrics.density, 0f, 0f,
            if (color == Color.WHITE) 0xCC000000.toInt() else 0xCCFFFFFF.toInt())
    }

    fun apply(view: View) {
        if (view is CardView || view is WebView) return
        if (view is MaterialButton) {
            if (view.backgroundTintList?.defaultColor == Color.TRANSPARENT) {
                applyText(view)
                val tint = ColorStateList.valueOf(view.currentTextColor)
                view.iconTint = tint
                if (view.strokeWidth > 0) view.strokeColor = tint
            }
            return
        }
        val background = view.background
        if (background is GradientDrawable || (background is ColorDrawable && Color.alpha(background.color) > 0)) return
        when (view) {
            is TextView -> if (background == null) applyText(view)
            is ImageView -> if (view.imageTintList != null) {
                view.imageTintList = ColorStateList.valueOf(colors[owner(view.context)] ?: Color.WHITE)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) apply(view.getChildAt(i))
        }
    }
}
