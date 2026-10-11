package com.nxd1frnt.clockdesk2.connect.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.color.utilities.Scheme
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.adapters.FontItem
import com.nxd1frnt.clockdesk2.utils.ColorItem
import com.nxd1frnt.clockdesk2.utils.FontAxis
import com.nxd1frnt.clockdesk2.utils.FontManager
import com.nxd1frnt.clockdesk2.utils.Logger

object DeskNotificationHelper {

    const val KEY_FONT_ID = "desk_notification_font_id"
    const val KEY_FONT_INDEX = "desk_notification_font_index"
    const val KEY_VARIATION_AXES = "desk_notification_variation_axes"
    const val KEY_STYLE = "desk_notification_style"
    const val KEY_POSITION = "desk_notification_position"
    const val KEY_SCALE = "desk_notification_scale"
    const val KEY_TEXT_COLOR = "desk_notification_text_color"
    const val KEY_TEXT_USE_DYNAMIC = "desk_notification_text_use_dynamic"
    const val KEY_TEXT_DYNAMIC_ROLE = "desk_notification_text_dynamic_role"
    const val KEY_BG_COLOR = "desk_notification_bg_color"
    const val KEY_BG_USE_DYNAMIC = "desk_notification_bg_use_dynamic"
    const val KEY_BG_DYNAMIC_ROLE = "desk_notification_bg_dynamic_role"
    const val KEY_BG_OPACITY = "desk_notification_bg_opacity"
    const val KEY_MONOCHROME_ICON = "desk_notification_monochrome_icon"
    const val KEY_SHOW_DEVICE = "desk_notification_show_device"
    const val KEY_SHOW_DISMISS = "desk_notification_show_dismiss"
    const val KEY_HIDE_SENSITIVE = "desk_notification_hide_sensitive"

    const val DEFAULT_FONT_ID = "res:googlesansflex"

    private val defaultColors = listOf(
        Color.WHITE,
        Color.BLACK,
        Color.parseColor("#FF8A80"), // Red
        Color.parseColor("#FFD180"), // Orange
        Color.parseColor("#FFFF8D"), // Yellow
        Color.parseColor("#CCFF90"), // Light Green
        Color.parseColor("#A7FFEB"), // TealAccent
        Color.parseColor("#80D8FF"), // Light Blue
        Color.parseColor("#82B1FF"), // Blue
        Color.parseColor("#B388FF"), // Purple
        Color.parseColor("#F48FB1")  // Pink
    )

    fun getFontsList(context: Context): List<FontItem> {
        return FontManager.buildFontsList(context)
    }

    fun getFontIdentifier(context: Context, item: FontItem): String {
        return FontManager.getFontIdentifier(context, item)
    }

    fun getDefaultFontIndex(fonts: List<FontItem>): Int {
        val defaultIdx = fonts.indexOfFirst {
            it is FontItem.ResourceFont && it.resId == R.font.googlesansflex
        }
        return if (defaultIdx != -1) defaultIdx else 1.coerceAtMost(fonts.lastIndex)
    }

    fun resolveSelectedFontIndex(
        context: Context,
        prefs: SharedPreferences,
        fonts: List<FontItem> = getFontsList(context)
    ): Int {
        val savedFontId = prefs.getString(KEY_FONT_ID, null)
        if (!savedFontId.isNullOrBlank()) {
            val byId = fonts.indexOfFirst {
                it !is FontItem.AddNew && getFontIdentifier(context, it) == savedFontId
            }
            if (byId != -1) return byId
        }
        val savedIndex = prefs.getInt(KEY_FONT_INDEX, -1)
        if (savedIndex in 1..fonts.lastIndex) {
            return savedIndex
        }
        return getDefaultFontIndex(fonts)
    }

    fun addCustomFont(context: Context, uri: Uri): String? {
        return FontManager.importCustomFontToStorage(context, uri)
    }

    fun deleteCustomFont(context: Context, item: FontItem): Boolean {
        return FontManager.deleteCustomFontFile(context, item)
    }

    fun getFontAxesDetails(
        context: Context,
        prefs: SharedPreferences,
        fonts: List<FontItem> = getFontsList(context)
    ): List<FontAxis> {
        val idx = resolveSelectedFontIndex(context, prefs, fonts)
        val item = fonts.getOrNull(idx) ?: return emptyList()
        return try {
            FontManager.getFontAxesDetailsForItem(context, item)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getVariationAxesMap(prefs: SharedPreferences): MutableMap<String, Float> {
        val axesString = prefs.getString(KEY_VARIATION_AXES, null)
        if (axesString != null) {
            val map = mutableMapOf<String, Float>()
            if (axesString.isNotBlank()) {
                axesString.split(";").forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        parts[1].toFloatOrNull()?.let { map[parts[0]] = it }
                    }
                }
            }
            return map
        }
        return mutableMapOf("wght" to 500f, "wdth" to 100f, "ROND" to 100f)
    }

    fun getAxisValue(prefs: SharedPreferences, axis: FontAxis): Float {
        val map = getVariationAxesMap(prefs)
        val raw = map[axis.tag] ?: when (axis.tag) {
            "wght" -> 500f
            "wdth" -> 100f
            "ROND" -> 100f
            else -> axis.defaultValue
        }
        return if (axis.maxValue > axis.minValue) {
            raw.coerceIn(axis.minValue, axis.maxValue)
        } else {
            axis.defaultValue
        }
    }

    fun setVariationAxis(prefs: SharedPreferences, tag: String, value: Float) {
        val map = getVariationAxesMap(prefs)
        map[tag] = value
        val axesString = map.entries.joinToString(";") { "${it.key}:${it.value}" }
        prefs.edit().putString(KEY_VARIATION_AXES, axesString).apply()
    }

    fun resetVariationAxes(prefs: SharedPreferences) {
        prefs.edit().remove(KEY_VARIATION_AXES).apply()
    }

    fun getAxisDisplayName(context: Context, tag: String): String {
        return when (tag) {
            "wght" -> context.getString(R.string.variable_properties_weight)
            "wdth" -> context.getString(R.string.variable_properties_width)
            "ROND" -> context.getString(R.string.variable_properties_roundness)
            "slnt" -> "Slant"
            "ital" -> "Italic"
            "opsz" -> "Optical Size"
            "GRAD" -> "Grade"
            "CASL" -> "Casual"
            "MONO" -> "Monospace"
            else -> tag
        }
    }

    fun resolveTypeface(context: Context, prefs: SharedPreferences): Typeface {
        val fonts = getFontsList(context)
        val idx = resolveSelectedFontIndex(context, prefs, fonts)
        val matched = fonts.getOrNull(idx)
        return try {
            when (matched) {
                is FontItem.ResourceFont -> ResourcesCompat.getFont(context, matched.resId) ?: Typeface.DEFAULT
                is FontItem.CustomFont -> Typeface.createFromFile(matched.path)
                else -> ResourcesCompat.getFont(context, R.font.googlesansflex) ?: Typeface.DEFAULT
            }
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }

    fun getCustomColors(prefs: SharedPreferences): MutableList<Int> {
        val list = mutableListOf<Int>()
        val saved = prefs.getString("custom_saved_colors", null)
        if (!saved.isNullOrBlank()) {
            saved.split(",").forEach { hex ->
                try {
                    val parsed = Color.parseColor(hex.trim())
                    if (!list.contains(parsed)) {
                        list.add(parsed)
                    }
                } catch (_: Exception) {}
            }
        }
        return list
    }

    fun addCustomColor(prefs: SharedPreferences, color: Int): Boolean {
        val list = getCustomColors(prefs)
        if (!list.contains(color)) {
            list.add(0, color)
            val hexList = list.joinToString(",") { String.format("#%08X", it) }
            prefs.edit().putString("custom_saved_colors", hexList).apply()
            return true
        }
        return false
    }

    fun deleteCustomColor(prefs: SharedPreferences, color: Int): Boolean {
        val list = getCustomColors(prefs)
        if (list.remove(color)) {
            val hexList = list.joinToString(",") { String.format("#%08X", it) }
            prefs.edit().putString("custom_saved_colors", hexList).apply()
            return true
        }
        return false
    }

    @SuppressLint("RestrictedApi")
    fun getColorsList(context: Context, prefs: SharedPreferences): List<ColorItem> {
        val list = mutableListOf<ColorItem>()
        list.add(ColorItem.AddNew)

        val roles = listOf(
            "default" to "Default Theme",
            "primary" to "Primary",
            "primary_container" to "Primary Cont.",
            "secondary" to "Secondary",
            "secondary_container" to "Sec. Cont.",
            "tertiary" to "Tertiary",
            "tertiary_container" to "Ter. Cont.",
            "surface" to "Surface",
            "surface_variant" to "Surface Var.",
            "inverse_surface" to "Inv. Surface",
            "outline" to "Outline"
        )

        for ((roleKey, roleName) in roles) {
            val roleColor = resolveDynamicRoleColor(context, prefs, roleKey, isBackground = roleKey == "default")
            list.add(ColorItem.Dynamic(roleColor, roleKey, roleName))
        }

        val customColors = getCustomColors(prefs)
        list.addAll(customColors.map { ColorItem.Solid(it, isCustom = true) })
        list.addAll(defaultColors.map { ColorItem.Solid(it, isCustom = false) })
        return list
    }

    @SuppressLint("RestrictedApi")
    fun resolveDynamicRoleColor(
        context: Context,
        prefs: SharedPreferences,
        role: String?,
        isBackground: Boolean = false
    ): Int {
        val savedSeed = prefs.getInt("dynamic_seed_color", -1)
        val scheme = if (savedSeed != -1) {
            try { Scheme.dark(savedSeed) } catch (_: Exception) { null }
        } else null

        if (role == "default" || role.isNullOrBlank()) {
            return if (isBackground) {
                MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                    scheme?.surfaceVariant ?: Color.parseColor("#2B2930")
                )
            } else {
                MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorOnSurface,
                    scheme?.onSurface ?: Color.WHITE
                )
            }
        }

        if (scheme != null) {
            return when (role) {
                "primary" -> scheme.primary
                "primary_container" -> scheme.primaryContainer
                "secondary" -> scheme.secondary
                "secondary_container" -> scheme.secondaryContainer
                "tertiary" -> scheme.tertiary
                "tertiary_container" -> scheme.tertiaryContainer
                "surface" -> scheme.surface
                "surface_variant" -> scheme.surfaceVariant
                "inverse_surface" -> scheme.inverseSurface
                "outline" -> scheme.outline
                else -> scheme.primary
            }
        }

        return when (role) {
            "primary" -> ContextCompat.getColor(context, R.color.md_theme_primary)
            "primary_container" -> ContextCompat.getColor(context, R.color.md_theme_primaryContainer)
            "secondary" -> ContextCompat.getColor(context, R.color.md_theme_secondary)
            "secondary_container" -> ContextCompat.getColor(context, R.color.md_theme_secondaryContainer)
            "tertiary" -> ContextCompat.getColor(context, R.color.md_theme_tertiary)
            "tertiary_container" -> ContextCompat.getColor(context, R.color.md_theme_tertiaryContainer)
            "surface" -> ContextCompat.getColor(context, R.color.md_theme_surface)
            "surface_variant" -> ContextCompat.getColor(context, R.color.md_theme_surfaceVariant)
            "inverse_surface" -> ContextCompat.getColor(context, R.color.md_theme_inverseSurface)
            "outline" -> ContextCompat.getColor(context, R.color.md_theme_outline)
            else -> ContextCompat.getColor(context, R.color.md_theme_primary)
        }
    }

    fun getBannerGravity(prefs: SharedPreferences): Int {
        return when (prefs.getString(KEY_POSITION, "top_center")) {
            "top_start" -> Gravity.TOP or Gravity.START
            "top_end" -> Gravity.TOP or Gravity.END
            "bottom_center" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
    }

    fun isBottomPosition(prefs: SharedPreferences): Boolean {
        return prefs.getString(KEY_POSITION, "top_center") == "bottom_center"
    }

    fun readScalePercent(prefs: SharedPreferences): Int {
        return try {
            prefs.getInt(KEY_SCALE, 100)
        } catch (_: Exception) {
            try {
                prefs.getFloat(KEY_SCALE, 100f).toInt()
            } catch (_: Exception) {
                100
            }
        }.coerceIn(80, 140)
    }

    fun readBgOpacityPercent(prefs: SharedPreferences): Int {
        return try {
            prefs.getInt(KEY_BG_OPACITY, 100)
        } catch (_: Exception) {
            try {
                prefs.getFloat(KEY_BG_OPACITY, 100f).toInt()
            } catch (_: Exception) {
                100
            }
        }.coerceIn(40, 100)
    }

    fun applyBannerCustomization(
        context: Context,
        view: View,
        prefs: SharedPreferences,
        appName: String,
        deviceName: String?,
        title: String,
        text: String,
        iconBytes: ByteArray?,
        notificationId: String,
        isClearable: Boolean,
        isPreview: Boolean = false
    ) {
        val cardView = (view as? MaterialCardView) ?: view.findViewById(R.id.notification_card)
        val contentRow = view.findViewById<LinearLayout>(R.id.notification_content_row)
        val appNameView = view.findViewById<TextView>(R.id.notification_app_name)
        val titleView = view.findViewById<TextView>(R.id.notification_title)
        val bodyView = view.findViewById<TextView>(R.id.notification_body)
        val iconView = view.findViewById<ImageView>(R.id.notification_app_icon)
        val dismissBtn = view.findViewById<ImageButton>(R.id.btn_dismiss_notification)

        val density = context.resources.displayMetrics.density
        val style = prefs.getString(KEY_STYLE, "standard") ?: "standard"
        val scalePercent = readScalePercent(prefs)
        val scaleFactor = (scalePercent / 100f).coerceIn(0.8f, 1.4f) * (if (isPreview) 0.88f else 1.0f)

        val showDevice = prefs.getBoolean(KEY_SHOW_DEVICE, true)
        val hideSensitive = prefs.getBoolean(KEY_HIDE_SENSITIVE, false)
        val showDismiss = prefs.getBoolean(KEY_SHOW_DISMISS, true)
        val monochromeIcon = prefs.getBoolean(KEY_MONOCHROME_ICON, false)

        // 1. Header & Content Text
        val headerStr = if (showDevice && !deviceName.isNullOrEmpty()) {
            "${appName.uppercase()} • ${deviceName.uppercase()}"
        } else {
            appName.uppercase()
        }
        appNameView.text = headerStr

        val displayTitle = if (hideSensitive) appName else title
        val displayBody = if (hideSensitive) context.getString(R.string.notification_hidden_content) else text

        when (style) {
            "compact" -> {
                appNameView.visibility = View.GONE
                titleView.visibility = View.VISIBLE
                titleView.maxLines = 1
                titleView.text = if (displayTitle.isNotEmpty() && displayBody.isNotEmpty()) {
                    "$displayTitle: $displayBody"
                } else {
                    displayTitle.ifEmpty { displayBody }
                }
                bodyView.visibility = View.GONE
            }
            "expanded" -> {
                appNameView.visibility = View.VISIBLE
                titleView.text = displayTitle
                titleView.visibility = if (displayTitle.isNotEmpty()) View.VISIBLE else View.GONE
                titleView.maxLines = 2
                bodyView.text = displayBody
                bodyView.visibility = if (displayBody.isNotEmpty()) View.VISIBLE else View.GONE
                bodyView.maxLines = 4
            }
            else -> {
                appNameView.visibility = View.VISIBLE
                titleView.text = displayTitle
                titleView.visibility = if (displayTitle.isNotEmpty()) View.VISIBLE else View.GONE
                titleView.maxLines = 1
                bodyView.text = displayBody
                bodyView.visibility = if (displayBody.isNotEmpty()) View.VISIBLE else View.GONE
                bodyView.maxLines = 2
            }
        }

        // 2. Typography (Font, Variable Axes & Sizes)
        val baseTypeface = resolveTypeface(context, prefs)
        appNameView.typeface = baseTypeface
        titleView.typeface = baseTypeface
        bodyView.typeface = baseTypeface

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val supportedAxes = getFontAxesDetails(context, prefs)
                val variationSettings = if (supportedAxes.isNotEmpty()) {
                    supportedAxes.joinToString(", ") { axis ->
                        "'${axis.tag}' ${getAxisValue(prefs, axis)}"
                    }
                } else null
                listOf(appNameView, titleView, bodyView).forEach { tv ->
                    tv.fontVariationSettings = null
                    tv.fontVariationSettings = variationSettings
                }
            } catch (_: Throwable) {}
        }

        appNameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * scaleFactor)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (style == "compact") 14f else 15f) * scaleFactor)
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * scaleFactor)

        // 3. Dimensions & Paddings
        val baseIconDp = if (style == "compact") 26f else 36f
        val iconPx = (baseIconDp * scaleFactor * density).toInt()
        iconView.layoutParams = iconView.layoutParams?.apply {
            width = iconPx
            height = iconPx
        }

        val baseDismissDp = if (style == "compact") 26f else 32f
        val dismissPx = (baseDismissDp * scaleFactor * density).toInt()
        dismissBtn.layoutParams = dismissBtn.layoutParams?.apply {
            width = dismissPx
            height = dismissPx
        }

        contentRow?.let { row ->
            val padStartDp = if (style == "compact") 12f else 16f
            val padEndDp = if (style == "compact") 10f else 12f
            val padVertDp = if (style == "compact") 8f else 12f
            row.setPadding(
                (padStartDp * scaleFactor * density).toInt(),
                (padVertDp * scaleFactor * density).toInt(),
                (padEndDp * scaleFactor * density).toInt(),
                (padVertDp * scaleFactor * density).toInt()
            )
            val minWidthDp = if (isPreview) {
                if (style == "compact") 180f else 220f
            } else {
                if (style == "compact") 200f else 280f
            }
            val maxWidthDp = if (isPreview) 320f else 440f
            row.minimumWidth = (minWidthDp * scaleFactor * density).toInt()
            // Ensure maxWidth constraint doesn't clip scaled text
            val maxPx = (maxWidthDp * scaleFactor * density).toInt()
            try {
                val field = LinearLayout::class.java.getDeclaredField("mMaxWidth")
                field.isAccessible = true
                field.setInt(row, maxPx)
            } catch (_: Exception) {}
        }

        val baseCornerDp = when (style) {
            "compact" -> 32f
            "expanded" -> 24f
            else -> 28f
        }
        cardView?.radius = baseCornerDp * scaleFactor * density

        // 4. Colors & Background Opacity
        val useDynamicText = prefs.getBoolean(KEY_TEXT_USE_DYNAMIC, true)
        val textRole = prefs.getString(KEY_TEXT_DYNAMIC_ROLE, "default") ?: "default"
        val solidTextColor = prefs.getInt(KEY_TEXT_COLOR, Color.WHITE)

        val headerColor: Int
        val titleColor: Int
        val bodyColor: Int

        if (useDynamicText && textRole == "default") {
            headerColor = resolveDynamicRoleColor(context, prefs, "primary", isBackground = false)
            titleColor = resolveDynamicRoleColor(context, prefs, "default", isBackground = false)
            bodyColor = ColorUtils.setAlphaComponent(titleColor, 195)
        } else {
            val resolved = if (useDynamicText) {
                resolveDynamicRoleColor(context, prefs, textRole, isBackground = false)
            } else {
                solidTextColor
            }
            headerColor = resolved
            titleColor = resolved
            bodyColor = ColorUtils.setAlphaComponent(resolved, 200)
        }

        appNameView.setTextColor(headerColor)
        titleView.setTextColor(titleColor)
        bodyView.setTextColor(bodyColor)

        val useDynamicBg = prefs.getBoolean(KEY_BG_USE_DYNAMIC, true)
        val bgRole = prefs.getString(KEY_BG_DYNAMIC_ROLE, "default") ?: "default"
        val solidBgColor = prefs.getInt(KEY_BG_COLOR, Color.parseColor("#2B2930"))
        val bgOpacityPercent = readBgOpacityPercent(prefs)

        val baseBgColor = if (useDynamicBg) {
            resolveDynamicRoleColor(context, prefs, bgRole, isBackground = true)
        } else {
            solidBgColor
        }
        val bgAlpha = (bgOpacityPercent * 255 / 100).coerceIn(0, 255)
        val finalBgColor = ColorUtils.setAlphaComponent(baseBgColor, bgAlpha)
        cardView?.setCardBackgroundColor(finalBgColor)

        val strokeAlpha = (45 * bgOpacityPercent / 100).coerceIn(15, 70)
        val strokeColor = ColorUtils.setAlphaComponent(titleColor, strokeAlpha)
        cardView?.strokeColor = strokeColor

        // 5. Dismiss Button
        dismissBtn.visibility = if (isClearable && showDismiss) View.VISIBLE else View.GONE
        ImageViewCompat.setImageTintList(dismissBtn, ColorStateList.valueOf(bodyColor))

        // 6. Icon & Monochrome Option
        val effectiveIconTint = if (monochromeIcon) headerColor else null
        applyIcon(context, iconView, iconBytes, notificationId, effectiveIconTint)
    }

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
                ?: MaterialColors.getColor(
                    iconView,
                    androidx.appcompat.R.attr.colorPrimary,
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
