package com.nxd1frnt.clockdesk2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.palette.graphics.Palette
import com.google.android.material.color.utilities.Scheme
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import com.nxd1frnt.clockdesk2.utils.ColorExtractor
import com.nxd1frnt.clockdesk2.utils.FontNameUtils
import com.nxd1frnt.clockdesk2.utils.FontVariationUtils
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Date

/**
 Font settings data class to hold font properties for each widget.
 Default values are set here as well.
 */
data class FontSettings(
    var fontIndex: Int = 1,
    var size: Float = 24f,
    var alpha: Float = 1.0f,
    var weight: Int = 400,
    var width: Int = 100,
    var roundness: Int = 0,
    var color: Int = Color.WHITE,
    var useDynamicColor: Boolean = false,
    var dynamicColorRole: String? = null,
    var backgroundColor: Int = Color.DKGRAY,
    var useDynamicBackgroundColor: Boolean = false,
    var dynamicBackgroundColorRole: String? = null,
    var isNightShiftEnabled: Boolean = true,
    var maxWidthPercent: Int = 100
)
sealed class ColorItem {
    data class Dynamic(
        val color: Int,
        val roleKey: String,
        val name: String
    ) : ColorItem()

    data class Solid(val color: Int) : ColorItem()
    object AddNew : ColorItem()
}

class FontManager(
    private val context: Context,
    private val timeText: TextView,
    private val dateText: TextView,
    private val lastfmLayout: LinearLayout,
    private val lastfmText: TextView,
    private val lastfmIcon: ImageView,
    private val weatherText: TextView,
    private val weatherIcon: ImageView,
    private val smartChipContainer: ConstraintLayout,
    initialLoggingState: Boolean
) {

    private val keyPrefixMap = mapOf(
        R.id.time_text to "time",
        R.id.date_text to "date",
        R.id.lastfm_layout to "lastfm",
        R.id.smart_chip_container to "chips"
    )

    private val settingsMap = mutableMapOf<Int, FontSettings>()
    private val fonts: MutableList<FontItem> = mutableListOf()
    @SuppressLint("RestrictedApi")
    private var currentScheme: Scheme? = null
    private val resourceFontIds = listOf(
        R.font.googlesansflex,
        R.font.anton_regular,
        R.font.kanit_regular,
        R.font.sigmar_regular,
        R.font.monomakh_regular,
        R.font.orbitron_regular,
        R.font.dancingscript_regular,
        R.font.grapenuts_regular,
        R.font.madimione_regular,
        R.font.montserrat_regular,
        R.font.pressstart2p_regular,
        R.font.shafarik_regular,
        R.font.alexandria_bold,
        R.font.m_plus_1_bold,
        R.font.knewave,
        R.font.chewy,
        R.font.capriola,
        R.font.cherry_bomb_one,
        R.font.comfortaa_bold,
        R.font.autour_one,
        R.font.fascinate,
        R.font.instrument_sans_bold,
        R.font.googlesans_bold
    )

    private var isNightShiftEnabled = false
    var isDynamicColorEnabled = false
    private var dynamicColor: Int? = null
    private var timeFormatPattern: String = "HH:mm"
    private var dateFormatPattern: String = "EEE, MMM dd"

    private val baseChipContainerSizeDp = 165f
    private val baseChipFontSizeSp = 14f

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


    init {
        rebuildFontList()
        //initialize settings map with defaults
        keyPrefixMap.keys.forEach { id ->
            settingsMap[id] = getDefaultSettingsFor(id)
        }
    }

    @SuppressLint("RestrictedApi")
    fun getColorsList(): List<ColorItem> {
        val list = mutableListOf<ColorItem>()

        currentScheme?.let { scheme ->
            list.add(ColorItem.Dynamic(scheme.primary, "primary", "Primary"))
            list.add(ColorItem.Dynamic(scheme.primaryContainer, "primary_container", "Primary Cont."))
            list.add(ColorItem.Dynamic(scheme.secondary, "secondary", "Secondary"))
            list.add(ColorItem.Dynamic(scheme.secondaryContainer, "secondary_container", "Sec. Cont."))
            list.add(ColorItem.Dynamic(scheme.tertiary, "tertiary", "Tertiary"))
            list.add(ColorItem.Dynamic(scheme.tertiaryContainer, "tertiary_container", "Ter. Cont."))
            list.add(ColorItem.Dynamic(scheme.surfaceVariant, "surface_variant", "Surface Var."))
            list.add(ColorItem.Dynamic(scheme.outline, "outline", "Outline"))
            list.add(ColorItem.Dynamic(scheme.surface, "surface", "Surface"))
            list.add(ColorItem.Dynamic(scheme.surfaceVariant, "surface_variant", "Surface Var."))
            list.add(ColorItem.Dynamic(scheme.inverseSurface, "inverse_surface", "Inv. Surface"))
            list.add(ColorItem.Dynamic(scheme.primaryContainer, "primary_container", "Pri. Cont."))
        }

        // list.add(ColorItem.AddNew)
        list.addAll(defaultColors.map { ColorItem.Solid(it) })
        return list
    }
    private fun getDefaultSettingsFor(id: Int): FontSettings {
        val size = when (id) {
            R.id.time_text -> 128f
            R.id.date_text -> 48f
            R.id.lastfm_layout -> 32f
            R.id.smart_chip_container -> 14f
            else -> 24f
        }
        val weight = when (id) {
            R.id.time_text -> 700 //Time is bold
            R.id.date_text -> 500 // Date is medium
            R.id.lastfm_layout -> 400 // LastFM is regular
            R.id.smart_chip_container -> 400 // Chips are regular
            else -> 400
        }

        val color = when (id) {
            R.id.time_text -> Color.WHITE
            R.id.date_text -> Color.WHITE
            R.id.lastfm_layout -> Color.WHITE
            R.id.smart_chip_container -> Color.WHITE
            else -> Color.WHITE
        }
        val fontIndex = if (fonts.size > 1) 1 else 0

        return FontSettings(
            fontIndex = fontIndex,
            size = size,
            alpha = 1.0f,
            weight = weight,
            width = 100,
            roundness = 100 //Google Sans Flex default
        )
    }

    fun getFonts(): List<FontItem> = fonts

    private fun getOrCreateSettings(view: View): FontSettings {
        return settingsMap.getOrPut(view.id) {
            FontSettings()
        }
    }

    private fun getResourceName(id: Int): String {
        return try {
            context.resources.getResourceEntryName(id)
        } catch (e: Exception) {
            "ID:$id"
        }
    }

    private fun rebuildFontList() {
        fonts.clear()
        fonts.add(FontItem.AddNew)

        val fontDir = File(context.filesDir, "custom_fonts")
        if (fontDir.exists()) {
            fontDir.listFiles()
                ?.filter { it.extension.equals("ttf", ignoreCase = true) || it.extension.equals("otf", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    // Parsing font name from file
                    val parsedName = FontNameUtils.getFontName(file)
                    // If parsing failed, use file name without extension
                    val displayName = parsedName ?: file.nameWithoutExtension

                    fonts.add(FontItem.CustomFont(file.absolutePath, displayName))
                }
        }

        // Parsing resource fonts
        fonts.addAll(resourceFontIds.map { resId ->
            val parsedName = FontNameUtils.getFontName {
                context.resources.openRawResource(resId)
            }

            // Fallback: use resource entry name, replace underscores with spaces and capitalize words
            val fallbackName = context.resources.getResourceEntryName(resId)
                .replace("_", " ")
                .capitalizeWords() // e.g., "googlesansflex" -> "Googlesansflex"

            FontItem.ResourceFont(resId, parsedName ?: fallbackName)
        })
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") {
        it.replaceFirstChar { char -> char.uppercase() }
    }

    fun addCustomFont(uri: Uri): Int {
        return try {
            val fontDir = File(context.filesDir, "custom_fonts")
            if (!fontDir.exists()) fontDir.mkdirs()

            val fileName = getFileName(uri) ?: "custom_${System.currentTimeMillis()}.ttf"
            val destFile = File(fontDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            rebuildFontList()
            1
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    fun deleteCustomFont(index: Int): Boolean {
        if (index !in fonts.indices) return false
        val item = fonts[index]

        if (item is FontItem.CustomFont) {
            try {
                val file = File(item.path)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        rebuildFontList()
                        return true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    private fun getTypeface(index: Int): Typeface {
        if (index !in fonts.indices) return Typeface.DEFAULT
        return try {
            when (val item = fonts[index]) {
                is FontItem.ResourceFont -> ResourcesCompat.getFont(context, item.resId) ?: Typeface.DEFAULT
                is FontItem.CustomFont -> Typeface.createFromFile(item.path)
                is FontItem.AddNew -> Typeface.DEFAULT
            }
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }

    // Used during loading to populate settings from prefs
    fun loadFont() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

        isNightShiftEnabled = prefs.getBoolean("nightShiftEnabled", false)
        isDynamicColorEnabled = prefs.getBoolean("use_dynamic_color", false)
        timeFormatPattern = prefs.getString("timeFormatPattern", "HH:mm") ?: "HH:mm"
        dateFormatPattern = prefs.getString("dateFormatPattern", "EEE, MMM dd") ?: "EEE, MMM dd"

        keyPrefixMap.forEach { (viewId, prefix) ->
            // Get default settings for this view
            val defaults = getDefaultSettingsFor(viewId)

            // Get or create settings object
            val settings = settingsMap.getOrPut(viewId) { defaults.copy() }

            val savedFontId = prefs.getString("${prefix}FontID", null)
            var foundIndex = -1

            if (savedFontId != null) {
                foundIndex = fonts.indexOfFirst { getFontIdentifier(it) == savedFontId }
            }

            if (foundIndex != -1) {
                settings.fontIndex = foundIndex
            } else {
                val rawIndex = prefs.getInt("${prefix}FontIndex", defaults.fontIndex)
                settings.fontIndex = rawIndex.coerceIn(1, if (fonts.isNotEmpty()) fonts.lastIndex else 0)
            }

            settings.size = prefs.getFloat("${prefix}Size", defaults.size)
            settings.alpha = prefs.getFloat("${prefix}Alpha", defaults.alpha)

            settings.weight = prefs.getInt("${prefix}Weight", defaults.weight)
            settings.width = prefs.getInt("${prefix}Width", defaults.width)
            settings.roundness = prefs.getInt("${prefix}Roundness", defaults.roundness)
            settings.color = prefs.getInt("${prefix}Color", Color.WHITE)
            settings.useDynamicColor = prefs.getBoolean("${prefix}UseDynamicColor", false)
            settings.dynamicColorRole = prefs.getString("${prefix}DynamicRole", "primary")
            settings.backgroundColor = prefs.getInt("${prefix}BgColor", Color.DKGRAY)
            settings.useDynamicBackgroundColor = prefs.getBoolean("${prefix}UseDynamicBgColor", false)
            settings.dynamicBackgroundColorRole = prefs.getString("${prefix}DynamicBgRole", "surface_variant")
            settings.isNightShiftEnabled = prefs.getBoolean("${prefix}isNightShiftEnabled", false)
            settings.maxWidthPercent = prefs.getInt("${prefix}MaxWidth", 100)
        }

        applyAll()
    }

    private fun getFontIdentifier(item: FontItem): String {
        return when (item) {
            is FontItem.ResourceFont -> {
                try {
                    "res:${context.resources.getResourceEntryName(item.resId)}"
                } catch (e: Exception) {
                    "res:${item.resId}"
                }
            }
            is FontItem.CustomFont -> "file:${File(item.path).name}"
            else -> "unknown"
        }
    }

    // Save current settings to SharedPreferences
    fun saveSettings() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putBoolean("nightShiftEnabled", isNightShiftEnabled)
        editor.putBoolean("use_dynamic_color", isDynamicColorEnabled)
        editor.putString("timeFormatPattern", timeFormatPattern)
        editor.putString("dateFormatPattern", dateFormatPattern)

        keyPrefixMap.forEach { (viewId, prefix) ->
            val settings = settingsMap[viewId] ?: return@forEach
            val currentFontItem = if (settings.fontIndex in fonts.indices) fonts[settings.fontIndex] else null
            val fontId = if (currentFontItem != null) getFontIdentifier(currentFontItem) else ""
            editor.putString("${prefix}FontID", fontId)
            editor.putInt("${prefix}FontIndex", settings.fontIndex)
            editor.putFloat("${prefix}Size", settings.size)
            editor.putFloat("${prefix}Alpha", settings.alpha)
            editor.putInt("${prefix}Weight", settings.weight)
            editor.putInt("${prefix}Width", settings.width)
            editor.putInt("${prefix}Roundness", settings.roundness)
            editor.putInt("${prefix}Color", settings.color)
            editor.putBoolean("${prefix}UseDynamicColor", settings.useDynamicColor)
            editor.putString("${prefix}DynamicRole", settings.dynamicColorRole)
            editor.putInt("${prefix}BgColor", settings.backgroundColor)
            editor.putBoolean("${prefix}UseDynamicBgColor", settings.useDynamicBackgroundColor)
            editor.putString("${prefix}DynamicBgRole", settings.dynamicBackgroundColorRole)
            editor.putBoolean("${prefix}isNightShiftEnabled", settings.isNightShiftEnabled)
            editor.putInt("${prefix}MaxWidth", settings.maxWidthPercent)
        }
        editor.apply()
    }

    // Getters and Setters for font properties
    fun getSettings(view: View): FontSettings? = settingsMap[view.id]

    fun setFontIndex(view: View, index: Int) {
        if (index !in fonts.indices || fonts[index] is FontItem.AddNew) return
        updateSettings(view.id) {
            it.fontIndex = index
        }
    }

    fun setFontSize(view: View, size: Float) {
        updateSettings(view.id) { it.size = size }
    }

    fun setMaxWidthPercent(view: View, percent: Int) {
        updateSettings(view.id) { it.maxWidthPercent = percent }
    }

    fun setFontAlpha(view: View, alpha: Float) {
        updateSettings(view.id) { it.alpha = alpha.coerceIn(0f, 1f) }
    }

    fun setFontVariations(view: View, weight: Int, width: Int, roundness: Int) {
        updateSettings(view.id) {
            it.weight = weight
            it.width = width
            it.roundness = roundness
        }
    }

    private inline fun updateSettings(viewId: Int, block: (FontSettings) -> Unit) {
        val settings = settingsMap[viewId] ?: return
        block(settings)
        applyToView(viewId)
    }


    private fun applyAll() {
        keyPrefixMap.keys.forEach { applyToView(it) }
    }

    private fun applyToView(viewId: Int) {
        val settings = settingsMap[viewId] ?: return
        val typeface = getTypeface(settings.fontIndex)

        val finalColor = if (settings.useDynamicColor && currentScheme != null) {
            getColorFromScheme(currentScheme!!, settings.dynamicColorRole)
        } else {
            settings.color
        }

        when (viewId) {
            R.id.time_text -> applyStyleToTextView(timeText, settings, typeface, finalColor)
            R.id.date_text -> applyStyleToTextView(dateText, settings, typeface, finalColor)
            R.id.lastfm_layout -> {
                applyStyleToTextView(lastfmText, settings, typeface, finalColor)
                lastfmIcon.alpha = settings.alpha
                lastfmIcon.setColorFilter(finalColor)
            }
            R.id.smart_chip_container -> {
                for (i in 0 until smartChipContainer.childCount) {
                    val chipView = smartChipContainer.getChildAt(i)
                    applyStyleToSmartChip(chipView, settings, typeface, finalColor)
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun getColorFromScheme(scheme: Scheme, role: String?): Int {
        return when (role) {
            "primary" -> scheme.primary
            "primary_container" -> scheme.primaryContainer
            "secondary" -> scheme.secondary
            "secondary_container" -> scheme.secondaryContainer
            "tertiary" -> scheme.tertiary
            "tertiary_container" -> scheme.tertiaryContainer
            "surface_variant" -> scheme.surfaceVariant
            "outline" -> scheme.outline
            else -> scheme.primary
        }
    }

    @SuppressLint("RestrictedApi")
    fun setDynamicScheme(seedColor: Int) {
        this.currentScheme = Scheme.dark(seedColor) // or Scheme.light(seedColor)
        applyAll()
    }

    fun applyStyleToSmartChip(view: View) {
        val settings = settingsMap[R.id.smart_chip_container] ?: return
        val typeface = getTypeface(settings.fontIndex)

        val finalColor = if (settings.useDynamicColor && currentScheme != null) {
            getColorFromScheme(currentScheme!!, settings.dynamicColorRole)
        } else {
            settings.color
        }

        applyStyleToSmartChip(view, settings, typeface, finalColor)
    }

    private fun applyStyleToSmartChip(view: View, settings: FontSettings, typeface: Typeface, color: Int) {
        val textView = view.findViewById<TextView>(R.id.chip_text)
        val iconView = view.findViewById<ImageView>(R.id.chip_icon)

        view.alpha = settings.alpha

        val scaleFactor = settings.size / baseChipFontSizeSp
        if (textView != null) {
            applyStyleToTextView(textView, settings, typeface, color)
            textView.alpha = 1.0f
        }

        if (iconView != null) {
            iconView.setColorFilter(color)
            iconView.alpha = 1.0f

            val baseIconSize = 20 * context.resources.displayMetrics.density
            val newIconSize = (baseIconSize * scaleFactor).toInt()

            val params = iconView.layoutParams
            if (params.width != newIconSize) {
                params.width = newIconSize
                params.height = newIconSize
                iconView.layoutParams = params
            }
        }

        val parentContainer = view.parent as? View
        val grandParent = parentContainer?.parent as? View // smart_chip_scrollview

        if (grandParent is android.widget.ScrollView) {
            val density = context.resources.displayMetrics.density

            val newSizePx = (baseChipContainerSizeDp * density * scaleFactor).toInt()

            val params = grandParent.layoutParams
            if (params.width != newSizePx || params.height != newSizePx) {
                params.width = newSizePx
                params.height = newSizePx
                grandParent.layoutParams = params
            }
        }

        val density = context.resources.displayMetrics.density
        val basePadV = (8 * density).toInt()
        val basePadEnd = (12 * density).toInt()
        val newPadV = (basePadV * scaleFactor).toInt()
        val newPadEnd = (basePadEnd * scaleFactor).toInt()

        val layout = textView?.parent as? View
        layout?.setPaddingRelative(newPadV, newPadV, newPadEnd, newPadV)

        val cardView = view as? com.google.android.material.card.MaterialCardView
        val bgColor = if (settings.useDynamicBackgroundColor && currentScheme != null) {
            getColorFromScheme(currentScheme!!, settings.dynamicBackgroundColorRole)
        } else {
            settings.backgroundColor
        }
        cardView?.setCardBackgroundColor(bgColor)
    }

    private fun applyStyleToTextView(textView: TextView, settings: FontSettings, typeface: Typeface, color: Int) {
       // Apply typeface and size
        textView.typeface = typeface
        textView.textSize = settings.size
        textView.alpha = settings.alpha

            textView.setTextColor(color)

        if (settings.maxWidthPercent >= 100) {
             textView.maxWidth = Int.MAX_VALUE
        } else {
             val displayMetrics = context.resources.displayMetrics
             val screenWidth = displayMetrics.widthPixels
             val targetWidthPx = (screenWidth * (settings.maxWidthPercent / 100f)).toInt()
             textView.maxWidth = targetWidthPx
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            textView.fontVariationSettings = null
            // Form the variation settings string
            val variationSettings = "'wght' ${settings.weight}, 'wdth' ${settings.width}, 'ROND' ${settings.roundness}"
            textView.fontVariationSettings = variationSettings
        }
    }

    // Get supported axes for the currently selected font in the given view
    fun getSupportedAxesForCurrentIndex(view: View): List<String> {
        val settings = getSettings(view) ?: return emptyList()
        val index = settings.fontIndex
        if (index !in fonts.indices) return emptyList()

        val item = fonts[index]
        return FontVariationUtils.scanAxes {
            try {
                when (item) {
                    is FontItem.ResourceFont -> context.resources.openRawResource(item.resId)
                    is FontItem.CustomFont -> context.contentResolver.openInputStream(Uri.fromFile(File(item.path)))
                    is FontItem.AddNew -> null
                }
            } catch (e: Exception) { null }
        }
    }

    fun getTimeFormatPattern() = timeFormatPattern
    fun setTimeFormatPattern(pattern: String) { timeFormatPattern = pattern; saveSettings() }

    fun getDateFormatPattern() = dateFormatPattern
    fun setDateFormatPattern(pattern: String) { dateFormatPattern = pattern; saveSettings() }

    fun setNightShiftEnabledForView(view: View, enabled: Boolean) {
        val settings = getOrCreateSettings(view)
        settings.isNightShiftEnabled = enabled
        saveSettings()
    }

    fun isNightShiftEnabledForView(view: View): Boolean {
        return getSettings(view)?.isNightShiftEnabled ?: true
    }

    fun isNightShiftEnabledGlobally(): Boolean = isNightShiftEnabled

    @SuppressLint("RestrictedApi")
    fun updateDynamicColors(bitmap: Bitmap, onComplete: () -> Unit) {
        ColorExtractor.extractColor(bitmap) { seedColor ->

            val scheme = Scheme.light(seedColor) // Или .dark()
            dynamicColor = scheme.secondary

            applyAll()
            onComplete()
        }
    }
    fun setDynamicColorFromSeed(seedColor: Int) {
        this.dynamicColor = seedColor
        applyAll()
    }

    fun setFontColor(view: View, color: Int) {
        updateSettings(view.id) {
            it.color = color
            it.useDynamicColor = false
        }
    }


    fun setDynamicColorForWidget(view: View, role: String) {
        updateSettings(view.id) {
            it.useDynamicColor = true
            it.dynamicColorRole = role
        }
    }

    fun setSmartChipBackgroundColor(view: View, color: Int) {
        updateSettings(view.id) {
            it.backgroundColor = color
            it.useDynamicBackgroundColor = false
        }
    }

    fun setSmartChipDynamicBackgroundColor(view: View, role: String) {
        updateSettings(view.id) {
            it.useDynamicBackgroundColor = true
            it.dynamicBackgroundColorRole = role
        }
    }

    fun getDynamicColor(): Int? = dynamicColor

    fun clearDynamicColors() {
        currentScheme = null
        dynamicColor = null
        applyAll()
    }

    private fun getTargetColorForView(viewId: Int): Int {
        val settings = settingsMap[viewId] ?: return Color.WHITE

        if (settings.useDynamicColor && currentScheme != null) {
            return getColorFromScheme(currentScheme!!, settings.dynamicColorRole)
        }
        return settings.color
    }

    fun applyNightShiftTransition(currentTime: Date, sunTimeApi: DayTimeGetter, enabled: Boolean) {
        if (!enabled) {
            applyAll()
            return
        }

        val sunrise = sunTimeApi.sunriseTime ?: run { sunTimeApi.setDefault(); sunTimeApi.sunriseTime!! }
        val sunset = sunTimeApi.sunsetTime ?: run { sunTimeApi.setDefault(); sunTimeApi.sunsetTime!! }

        val preSunrise = Calendar.getInstance().apply { time = sunrise; add(Calendar.MINUTE, -40) }.time
        val postSunset = Calendar.getInstance().apply { time = sunset; add(Calendar.MINUTE, 30) }.time
        val fullNight = Calendar.getInstance().apply { time = postSunset; add(Calendar.MINUTE, 40) }.time

        val nightFactor = when {
            currentTime.before(preSunrise) -> 1.0f
            currentTime.before(sunrise) -> {
                1.0f - ((currentTime.time - preSunrise.time).toFloat() / (sunrise.time - preSunrise.time))
            }
            currentTime.before(postSunset) -> 0.0f // День
            currentTime.before(fullNight) -> {
                (currentTime.time - postSunset.time).toFloat() / (fullNight.time - postSunset.time)
            }
            else -> 1.0f
        }

        val nightTint = Color.rgb(255, 104, 104)

        updateViewColorWithNightShift(timeText, R.id.time_text, nightFactor, nightTint)
        updateViewColorWithNightShift(dateText, R.id.date_text, nightFactor, nightTint)
        updateViewColorWithNightShift(lastfmText, R.id.lastfm_layout, nightFactor, nightTint) // ID layout, цвет text

        val iconEffectiveFactor = if (settingsMap[R.id.lastfm_layout]?.isNightShiftEnabled != false) nightFactor else 0f
        val targetIconColor = getTargetColorForView(R.id.lastfm_layout)
        val finalIconColor = interpolateColor(targetIconColor, nightTint, iconEffectiveFactor)
        lastfmIcon.setColorFilter(finalIconColor)

        val chipSettings = settingsMap[R.id.smart_chip_container]

        val chipEffectiveFactor = if (chipSettings?.isNightShiftEnabled != false) nightFactor else 0f

        val targetColor = getTargetColorForView(R.id.smart_chip_container)

        val finalChipColor = interpolateColor(targetColor, nightTint, chipEffectiveFactor)

        for (i in 0 until smartChipContainer.childCount) {
            val chipView = smartChipContainer.getChildAt(i)
            val textView = chipView.findViewById<TextView>(R.id.chip_text)
            val iconView = chipView.findViewById<ImageView>(R.id.chip_icon)

            textView?.setTextColor(finalChipColor)
            iconView?.setColorFilter(finalChipColor)
        }
    }

    private fun updateViewColorWithNightShift(textView: TextView, settingsId: Int, nightFactor: Float, nightTint: Int) {
        val settings = settingsMap[settingsId]

        val effectiveFactor = if (settings?.isNightShiftEnabled == true) nightFactor else 0f

        val targetColor = getTargetColorForView(settingsId)
        val finalColor = interpolateColor(targetColor, nightTint, effectiveFactor)

        textView.setTextColor(finalColor)
    }

    private fun interpolateColor(color1: Int, color2: Int, factor: Float): Int {
        val clamped = factor.coerceIn(0f, 1f)
        return Color.rgb(
            Color.red(color1) + ((Color.red(color2) - Color.red(color1)) * clamped).toInt(),
            Color.green(color1) + ((Color.green(color2) - Color.green(color1)) * clamped).toInt(),
            Color.blue(color1) + ((Color.blue(color2) - Color.blue(color1)) * clamped).toInt()
        )
    }
}