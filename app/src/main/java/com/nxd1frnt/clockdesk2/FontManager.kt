package com.nxd1frnt.clockdesk2

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
import androidx.core.content.res.ResourcesCompat
import androidx.palette.graphics.Palette
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
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
    var useDynamicColor: Boolean = false
)
sealed class ColorItem {
    object Dynamic : ColorItem()
    data class Solid(val color: Int) : ColorItem()
    object AddNew : ColorItem() // TODO: COLOR PICKER
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
    initialLoggingState: Boolean
) {

    private val keyPrefixMap = mapOf(
        R.id.time_text to "time",
        R.id.date_text to "date",
        R.id.lastfm_layout to "lastfm"
    )

    private val settingsMap = mutableMapOf<Int, FontSettings>()
    private val fonts: MutableList<FontItem> = mutableListOf()

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

    fun getColorsList(): List<ColorItem> {
        val list = mutableListOf<ColorItem>()
        list.add(ColorItem.Dynamic)
        // list.add(ColorItem.AddNew)
        list.addAll(defaultColors.map { ColorItem.Solid(it) })
        return list
    }
    private fun getDefaultSettingsFor(id: Int): FontSettings {
        val size = when (id) {
            R.id.time_text -> 128f
            R.id.date_text -> 48f
            R.id.lastfm_layout -> 32f
            else -> 24f
        }
        val weight = when (id) {
            R.id.time_text -> 700 //Time is bold
            R.id.date_text -> 500 // Date is medium
            R.id.lastfm_layout -> 400 // LastFM is regular
            else -> 400
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

            // Load data. If the key is not in the file, take the value from defaults (not hard 400!)
            val rawIndex = prefs.getInt("${prefix}FontIndex", defaults.fontIndex)
            settings.fontIndex = rawIndex.coerceIn(1, fonts.lastIndex)

            settings.size = prefs.getFloat("${prefix}Size", defaults.size)
            settings.alpha = prefs.getFloat("${prefix}Alpha", defaults.alpha)

            settings.weight = prefs.getInt("${prefix}Weight", defaults.weight)
            settings.width = prefs.getInt("${prefix}Width", defaults.width)
            settings.roundness = prefs.getInt("${prefix}Roundness", defaults.roundness)
            settings.color = prefs.getInt("${prefix}Color", Color.WHITE)
            settings.useDynamicColor = prefs.getBoolean("${prefix}UseDynamicColor", false)
        }

        applyAll()
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
            editor.putInt("${prefix}FontIndex", settings.fontIndex)
            editor.putFloat("${prefix}Size", settings.size)
            editor.putFloat("${prefix}Alpha", settings.alpha)
            editor.putInt("${prefix}Weight", settings.weight)
            editor.putInt("${prefix}Width", settings.width)
            editor.putInt("${prefix}Roundness", settings.roundness)
            editor.putInt("${prefix}Color", settings.color)
            editor.putBoolean("${prefix}UseDynamicColor", settings.useDynamicColor)
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

        val finalColor = if (settings.useDynamicColor && dynamicColor != null) {
            dynamicColor!!
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
        }
    }

    private fun applyStyleToTextView(textView: TextView, settings: FontSettings, typeface: Typeface, color: Int) {
       // Apply typeface and size
        textView.typeface = typeface
        textView.textSize = settings.size
        textView.alpha = settings.alpha

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            textView.fontVariationSettings = null
            if (!isNightShiftEnabled) {
                textView.setTextColor(color)
            }
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

    fun isNightShiftEnabled() = isNightShiftEnabled
    fun setNightShiftEnabled(enabled: Boolean) { isNightShiftEnabled = enabled; saveSettings() }

    fun setAdditionalLogging(enabled: Boolean) { }

    fun updateDynamicColors(bitmap: Bitmap, onComplete: () -> Unit) {
        Palette.from(bitmap).generate { palette ->
            val accentColor = palette?.vibrantSwatch?.rgb ?: palette?.lightVibrantSwatch?.rgb ?: palette?.dominantSwatch?.rgb ?: Color.WHITE
            dynamicColor = accentColor
            applyAll()
            onComplete()
        }
    }

    fun setFontColor(view: View, color: Int) {
        updateSettings(view.id) {
            it.color = color
            it.useDynamicColor = false
        }
    }

    fun setDynamicColorEnabledForWidget(view: View) {
        updateSettings(view.id) {
            it.useDynamicColor = true
            it.color = dynamicColor ?: Color.WHITE
        }
    }

    fun getDynamicColor(): Int? = dynamicColor

    fun applyNightShiftTransition(currentTime: Date, sunTimeApi: DayTimeGetter, enabled: Boolean) {
        if (!enabled) {
            timeText.setTextColor(Color.WHITE)
            dateText.setTextColor(Color.WHITE)
            lastfmText.setTextColor(Color.WHITE)
            lastfmIcon.setColorFilter(Color.WHITE)
            return
        }
        // ... [Night Shift Logic remains same] ...
        val sunrise = sunTimeApi.sunriseTime ?: run { sunTimeApi.setDefault(); sunTimeApi.sunriseTime!! }
        val sunset = sunTimeApi.sunsetTime ?: run { sunTimeApi.setDefault(); sunTimeApi.sunsetTime!! }
        val preSunrise = Calendar.getInstance().apply { time = sunrise; add(Calendar.MINUTE, -40) }.time
        val postSunset = Calendar.getInstance().apply { time = sunset; add(Calendar.MINUTE, 30) }.time
        val fullNight = Calendar.getInstance().apply { time = postSunset; add(Calendar.MINUTE, 40) }.time
        val reddish = Color.rgb(255, 104, 104)
        val normal = Color.WHITE

        val color = when {
            currentTime.before(preSunrise) -> reddish
            currentTime.before(sunrise) -> interpolateColor(reddish, normal, (currentTime.time - preSunrise.time).toFloat() / (sunrise.time - preSunrise.time))
            currentTime.before(postSunset) -> normal
            currentTime.before(fullNight) -> interpolateColor(normal, reddish, (currentTime.time - postSunset.time).toFloat() / (fullNight.time - postSunset.time))
            else -> reddish
        }
        timeText.setTextColor(color)
        dateText.setTextColor(color)
        lastfmText.setTextColor(color)
        lastfmIcon.setColorFilter(color)
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