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
        if (desk.type == DeskType.WIDGETS) {
            try { return renderWidgets(desk) } catch (e: RuntimeException) {
                Logger.w("DeskPreviewStore") { "Could not render widget preview: ${e.message}" }
            }
        }
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

    private fun renderWidgets(desk: DeskItem): Bitmap {
        val root = LayoutInflater.from(context).inflate(R.layout.layout_desk_widgets, null, false) as ConstraintLayout
        val time = root.findViewById<TextView>(R.id.time_text)
        val date = root.findViewById<TextView>(R.id.date_text)
        val media = root.findViewById<android.view.ViewGroup>(R.id.lastfm_layout)
        val chips = root.findViewById<ConstraintLayout>(R.id.smart_chip_container)
        val preferences = DeskAppearancePreferences(context).apply { switchLayout(desk.widgetLayoutKey) }
        val fonts = FontManager(context, time, date, media, root.findViewById(R.id.now_playing_text),
            root.findViewById(R.id.lastfm_icon), root.findViewById(R.id.weather_text),
            root.findViewById(R.id.weather_icon), chips, false).apply { appearancePreferences = preferences }
        val host = object : DesktopWidgetHost {
            override val hostContext = context
            override val isEditMode = false
            override val fontManager = fonts
            override fun getPreferences() = preferences
            override fun getRestTranslationX(view: View) = 0f
            override fun onMusicArtworkChanged(artworkSource: Any?) {}
            override fun onWidgetClicked(widget: DesktopWidgetController) {}
        }
        val manager = DesktopWidgetManager(context, root, host, desk.widgetLayoutKey)
        try {
            fonts.loadFont()
            manager.bindExistingViews(time, date, media, chips, root.findViewById(R.id.weather_layout))
            root.findViewById<TextView>(R.id.now_playing_text).text = context.getString(R.string.now_playing_placeholder)
            root.findViewById<TextView>(R.id.compact_title_text).text = context.getString(R.string.now_playing_placeholder)
            root.findViewById<TextView>(R.id.expanded_title_text).text = context.getString(R.string.now_playing_placeholder)
            chips.addView(TextView(context).apply {
                id = R.id.chip_text
                text = context.getString(R.string.widget_smart_chips_title)
                fonts.applyStyleToSmartChip(this)
            })
            val screen = context.resources.displayMetrics
            val width = screen.widthPixels.coerceAtLeast(960)
            val height = screen.heightPixels.coerceAtLeast(540)
            WidgetMover(context, manager.getViews().filter { it !== chips }, root,
                DesktopWidgetManager.positionPreferencesName(desk.widgetLayoutKey), autoInitialize = false)
                .restoreImmediately(width, height)
            if (manager.getActiveInstances().isEmpty()) {
                root.findViewById<TextView>(R.id.empty_desk_prompt).apply {
                    visibility = View.VISIBLE
                    text = context.getString(R.string.desk_preview_empty)
                }
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
            }
            // Detached TextViews still need pre-draw to align horizontally scrolling text.
            fun dispatchPreDraw(view: View) {
                view.viewTreeObserver.dispatchOnPreDraw()
                if (view is android.view.ViewGroup) {
                    for (index in 0 until view.childCount) dispatchPreDraw(view.getChildAt(index))
                }
            }
            dispatchPreDraw(root)
            return render(root)
        } finally {
            manager.getControllers().forEach { it.onDestroy() }
        }
    }
}
