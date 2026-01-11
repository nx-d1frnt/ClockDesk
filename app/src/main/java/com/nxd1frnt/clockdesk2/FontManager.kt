package com.nxd1frnt.clockdesk2

import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.palette.graphics.Palette
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import java.util.Calendar
import java.util.Date

class FontManager(
    private val context: Context,
    private val timeText: TextView,
    private val dateText: TextView,
    private val weatherText: TextView,
    private val weatherIcon: ImageView,
    private val lastfmText: TextView,
    private val lastfmIcon: ImageView,
    private val lastfmLayout: LinearLayout,
    initialLoggingState: Boolean
) {
    private var timeFontIndex = 0
    private var dateFontIndex = 0
    private var lastfmFontIndex = 0
    private var timeSize = 128f
    private var dateSize = 48f
    private var timeAlpha = 1.0f
    private var dateAlpha = 1.0f
    private var lastfmAlpha = 1.0f
    private var lastfmSize = 32f
    private var isNightShiftEnabled = false
    private var timeFormatPattern: String = "HH:mm"
    private var dateFormatPattern: String = "EEE, MMM dd"
    private var additionalLoggingEnabled = initialLoggingState

    private var isDynamicColorEnabled = false
    private var dynamicColor: Int? = null
    private val normalColor = Color.parseColor("#DEFFFFFF")
    private val fonts = listOf(
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

    fun getFonts(): List<Int> = fonts

    fun loadFont() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        timeFontIndex = prefs.getInt("timeFontIndex", 1)
        dateFontIndex = prefs.getInt("dateFontIndex", 1)
        lastfmFontIndex = prefs.getInt("lastfmFontIndex", 1)
        timeSize = prefs.getFloat("timeSize", 128f)
        dateSize = prefs.getFloat("dateSize", 48f)
        timeAlpha = prefs.getFloat("timeAlpha", 0.8f)
        dateAlpha = prefs.getFloat("dateAlpha", 0.8f)
        lastfmAlpha = prefs.getFloat("lastfmAlpha", 0.8f)
        lastfmSize = prefs.getFloat("lastfmSize", 32f)
        isNightShiftEnabled = prefs.getBoolean("nightShiftEnabled", false)
        isDynamicColorEnabled = prefs.getBoolean("use_dynamic_color", false)
        timeFormatPattern = prefs.getString("timeFormatPattern", "HH:mm") ?: "HH:mm"
        dateFormatPattern = prefs.getString("dateFormatPattern", "EEE, MMM dd") ?: "EEE, MMM dd"

        applyTimeFont()
        applyDateFont()
        applyLastfmFont()
    }

    fun saveSettings() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putInt("timeFontIndex", timeFontIndex)
            putInt("dateFontIndex", dateFontIndex)
            putInt("lastfmFontIndex", lastfmFontIndex)
            putFloat("timeSize", timeSize)
            putFloat("dateSize", dateSize)
            putFloat("timeAlpha", timeAlpha)
            putFloat("dateAlpha", dateAlpha)
            putFloat("lastfmAlpha", lastfmAlpha)
            putFloat("lastfmSize", lastfmSize)
            putBoolean("nightShiftEnabled", isNightShiftEnabled)
            putBoolean("use_dynamic_color", isDynamicColorEnabled)
            putString("timeFormatPattern", timeFormatPattern)
            putString("dateFormatPattern", dateFormatPattern)
            apply()
        }
    }

    // ... [Setters for Fonts/Sizes/Colors/NightShift/DynamicColor remain same] ...

    fun setTimeFont(fontId: Int) {
        timeFontIndex = fonts.indexOf(fontId).takeIf { it >= 0 } ?: 0
        applyTimeFont()
    }
    fun setDateFont(fontId: Int) {
        dateFontIndex = fonts.indexOf(fontId).takeIf { it >= 0 } ?: 0
        applyDateFont()
    }
    fun setLastfmFont(fontId: Int) {
        lastfmFontIndex = fonts.indexOf(fontId).takeIf { it >= 0 } ?: 0
        applyLastfmFont()
    }
    fun setTimeSize(size: Float) { timeSize = size; applyTimeFont() }
    fun setDateSize(size: Float) { dateSize = size; applyDateFont() }
    fun setLastfmSize(size: Float) { lastfmSize = size; applyLastfmFont() }
    fun setTimeAlpha(alpha: Float) { timeAlpha = alpha.coerceIn(0f, 1f); applyTimeFont() }
    fun setDateAlpha(alpha: Float) { dateAlpha = alpha.coerceIn(0f, 1f); applyDateFont() }
    fun setLastfmAlpha(alpha: Float) { lastfmAlpha = alpha.coerceIn(0f, 1f); applyLastfmFont() }

    fun setTimeFormatPattern(pattern: String) { timeFormatPattern = pattern; saveSettings() }
    fun setDateFormatPattern(pattern: String) { dateFormatPattern = pattern; saveSettings() }
    fun getTimeFormatPattern(): String = timeFormatPattern
    fun getDateFormatPattern(): String = dateFormatPattern

    fun getTimeSize() = timeSize
    fun getDateSize() = dateSize
    fun getLastfmSize() = lastfmSize
    fun getTimeAlpha() = timeAlpha
    fun getDateAlpha() = dateAlpha
    fun getLastfmAlpha() = lastfmAlpha
    fun isNightShiftEnabled() = isNightShiftEnabled
    fun setNightShiftEnabled(enabled: Boolean) { isNightShiftEnabled = enabled; saveSettings() }
    fun setAdditionalLogging(enabled: Boolean) { additionalLoggingEnabled = enabled }

    private fun applyTimeFont() {
        val typeface = ResourcesCompat.getFont(context, fonts[timeFontIndex])
        timeText.typeface = typeface
        timeText.textSize = timeSize
        timeText.alpha = timeAlpha
    }

    private fun applyDateFont() {
        val typeface = ResourcesCompat.getFont(context, fonts[dateFontIndex])
        dateText.typeface = typeface
        dateText.textSize = dateSize
        dateText.alpha = dateAlpha
    }

    private fun applyLastfmFont() {
        val typeface = ResourcesCompat.getFont(context, fonts[lastfmFontIndex])
        lastfmText.typeface = typeface
        lastfmText.textSize = lastfmSize
        lastfmText.alpha = lastfmAlpha
        lastfmIcon.alpha = lastfmAlpha
    }

    fun updateDynamicColors(bitmap: Bitmap, onComplete: () -> Unit) {
        Palette.from(bitmap).generate { palette ->
            val accentColor = palette?.vibrantSwatch?.rgb ?: palette?.lightVibrantSwatch?.rgb ?: palette?.dominantSwatch?.rgb ?: normalColor
            dynamicColor = accentColor
            onComplete()
        }
    }
    fun clearDynamicColors() { dynamicColor = null }
    fun setDynamicColorEnabled(enabled: Boolean) { isDynamicColorEnabled = enabled }
    fun isDynamicColorEnabled(): Boolean = isDynamicColorEnabled

    fun getFontIndexForView(view: View): Int {
        return when (view) {
            timeText -> timeFontIndex
            dateText -> dateFontIndex
            lastfmText, lastfmIcon, lastfmLayout -> lastfmFontIndex
            else -> -1
        }
    }
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