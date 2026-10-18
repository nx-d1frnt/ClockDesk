package com.nxd1frnt.clockdesk2.desks.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.desks.DeskAppearancePreferences
import com.nxd1frnt.clockdesk2.desks.DeskItem
import com.nxd1frnt.clockdesk2.desks.DeskType
import com.nxd1frnt.clockdesk2.ui.WidgetMover
import com.nxd1frnt.clockdesk2.utils.FontManager
import com.nxd1frnt.clockdesk2.utils.Logger
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetManager
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetController
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetHost
import java.io.File
import java.util.concurrent.Executors

/** Thumbnail cache survives activity recreation without retaining live page views. */
class DeskPreviewStore(private val context: Context) {
    companion object {
        private val diskWriter = Executors.newSingleThreadExecutor { task ->
            Thread(task, "desk-previews").apply { isDaemon = true }
        }
    }
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val directory = File(context.cacheDir, "desk_previews").apply { mkdirs() }
    private fun file(id: String) = File(directory, id.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".png")

    fun preload(desks: List<DeskItem>) {
        diskWriter.execute {
            desks.forEach { desk ->
                if (cache.get(desk.id) == null) {
                    val target = file(desk.id)
                    if (target.exists()) {
                        try {
                            BitmapFactory.decodeFile(target.path)?.let { bmp ->
                                cache.put(desk.id, bmp)
                            }
                        } catch (e: Exception) {
                            Logger.w("DeskPreviewStore") { "Failed to preload preview: ${e.message}" }
                        }
                    }
                }
            }
        }
    }

    fun get(desk: DeskItem): Bitmap {
        cache.get(desk.id)?.let { return it }
        val bitmap = BitmapFactory.decodeFile(file(desk.id).path) ?: fallback(desk)
        cache.put(desk.id, bitmap)
        return bitmap
    }

    fun capture(id: String, view: View) {
        if (view.width <= 0 || view.height <= 0) return
        try {
            val bitmap = render(view)
            cache.put(id, bitmap)
            val target = file(id)
            diskWriter.execute {
                try {
                    val temp = File(target.path + ".tmp")
                    temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    if (!temp.renameTo(target)) temp.delete()
                } catch (e: Exception) {
                    Logger.w("DeskPreviewStore") { "Could not save preview: ${e.message}" }
                }
            }
        } catch (e: RuntimeException) {
            // Some artwork drawables use hardware bitmaps, unavailable to this canvas.
            Logger.w("DeskPreviewStore") { "Could not capture preview: ${e.message}" }
        }
    }

    fun invalidate(id: String) {
        cache.remove(id)
        val target = file(id)
        diskWriter.execute { target.delete() }
    }

    private fun render(view: View): Bitmap {
        val scale = minOf(1f, 768f / maxOf(view.width, view.height))
        val bitmap = Bitmap.createBitmap((view.width * scale).toInt().coerceAtLeast(1),
            (view.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(24, 29, 38))
            scale(scale, scale)
            view.draw(this)
        }
        return bitmap
    }

    private fun fallback(desk: DeskItem): Bitmap {
        val bitmap = Bitmap.createBitmap(768, 432, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(24, 29, 38))
            val icon = context.getDrawable(desk.type.iconResId)?.mutate()
            icon?.setTint(Color.WHITE)
            icon?.setBounds(344, 122, 424, 202)
            icon?.draw(this)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER }
            drawText(desk.title.take(36), 384f, 260f, paint)
            if (desk.type == DeskType.WEB_KIOSK) {
                paint.textSize = 20f
                paint.alpha = 160
                drawText(desk.customUrl.take(55), 384f, 300f, paint)
            }
        }
        return bitmap
    }
}
