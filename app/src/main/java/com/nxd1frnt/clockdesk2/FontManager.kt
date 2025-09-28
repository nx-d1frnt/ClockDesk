package com.nxd1frnt.clockdesk2

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import java.util.Calendar
import java.util.Date

class FontManager(private val context: Context, private val timeText: TextView, private val dateText: TextView) {
    private var timeFontIndex = 0
    private var dateFontIndex = 0
    private var timeSize = 48f // Default 48sp
    private var dateSize = 24f // Default 24sp
    private var timeAlpha = 1.0f
    private var dateAlpha = 1.0f
    private var isNightShiftEnabled = false
    private var timeAlignment: Int = timeText.textAlignment
    private var dateAlignment: Int = dateText.textAlignment
    private var timeFormatPattern: String = "HH:mm"
    private var dateFormatPattern: String = "EEE, MMM dd"
    private val fonts = listOf(
        R.font.anton_regular,
        //R.font.inflatevf,
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
        R.font.instrument_sans_bold
    )

    fun getFonts(): List<Int> = fonts

    fun loadFont() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        timeFontIndex = prefs.getInt("timeFontIndex", 0)
        dateFontIndex = prefs.getInt("dateFontIndex", 0)
        timeSize = prefs.getFloat("timeSize", 64f)
        dateSize = prefs.getFloat("dateSize", 24f)
        timeAlpha = prefs.getFloat("timeAlpha", 1.0f)
        dateAlpha = prefs.getFloat("dateAlpha", 1.0f)
        isNightShiftEnabled = prefs.getBoolean("nightShiftEnabled", false)
        // Load alignments, default to current text alignment on views
        timeAlignment = prefs.getInt("timeAlignment", timeText.textAlignment)
        dateAlignment = prefs.getInt("dateAlignment", dateText.textAlignment)
        // Load formats
        timeFormatPattern = prefs.getString("timeFormatPattern", "HH:mm") ?: "HH:mm"
        dateFormatPattern = prefs.getString("dateFormatPattern", "EEE, MMM dd") ?: "EEE, MMM dd"
        applyTimeFont()
        applyDateFont()
        Log.d("FontManager", "Loaded: timeFont=$timeFontIndex, dateFont=$dateFontIndex, timeSize=$timeSize, dateSize=$dateSize, timeAlpha=$timeAlpha, dateAlpha=$dateAlpha, timeAlign=$timeAlignment, dateAlign=$dateAlignment, timeFmt=$timeFormatPattern, dateFmt=$dateFormatPattern")
    }

    fun saveSettings() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putInt("timeFontIndex", timeFontIndex)
            putInt("dateFontIndex", dateFontIndex)
            putFloat("timeSize", timeSize)
            putFloat("dateSize", dateSize)
            putFloat("timeAlpha", timeAlpha)
            putFloat("dateAlpha", dateAlpha)
            putBoolean("nightShiftEnabled", isNightShiftEnabled)
            putInt("timeAlignment", timeAlignment)
            putInt("dateAlignment", dateAlignment)
            putString("timeFormatPattern", timeFormatPattern)
            putString("dateFormatPattern", dateFormatPattern)
            apply()
        }
    }

    fun setNightShiftEnabled(enabled: Boolean) {
        isNightShiftEnabled = enabled
        saveSettings()
        Log.d("FontManager", "Set nightShiftEnabled=$isNightShiftEnabled")
    }

    fun isNightShiftEnabled(): Boolean = isNightShiftEnabled

    fun setTimeFont(fontId: Int) {
        timeFontIndex = fonts.indexOf(fontId).takeIf { it >= 0 } ?: 0
        applyTimeFont()
        Log.d("FontManager", "Set timeFont=$timeFontIndex")
    }

    fun setDateFont(fontId: Int) {
        dateFontIndex = fonts.indexOf(fontId).takeIf { it >= 0 } ?: 0
        applyDateFont()
        Log.d("FontManager", "Set dateFont=$dateFontIndex")
    }

    fun setTimeSize(size: Float) {
        timeSize = size
        applyTimeFont()
        Log.d("FontManager", "Set timeSize=$timeSize")
    }

    fun setDateSize(size: Float) {
        dateSize = size
        applyDateFont()
        Log.d("FontManager", "Set dateSize=$dateSize")
    }

    fun setTimeAlpha(alpha: Float) {
        timeAlpha = alpha.coerceIn(0.0f, 1.0f)
        applyTimeFont()
        Log.d("FontManager", "Set timeAlpha=$timeAlpha")
    }

    fun setDateAlpha(alpha: Float) {
        dateAlpha = alpha.coerceIn(0.0f, 1.0f)
        applyDateFont()
        Log.d("FontManager", "Set dateAlpha=$dateAlpha")
    }

    fun setTimeAlignment(alignment: Int) {
        timeAlignment = alignment
        applyAlignmentToView(timeText, timeAlignment)
        saveSettings()
        Log.d("FontManager", "Set timeAlignment=$timeAlignment")
    }

    fun setDateAlignment(alignment: Int) {
        dateAlignment = alignment
        applyAlignmentToView(dateText, dateAlignment)
        saveSettings()
        Log.d("FontManager", "Set dateAlignment=$dateAlignment")
    }

    // Formatting setters/getters
    fun setTimeFormatPattern(pattern: String) {
        timeFormatPattern = pattern
        saveSettings()
        Log.d("FontManager", "Set timeFormatPattern=$timeFormatPattern")
    }

    fun setDateFormatPattern(pattern: String) {
        dateFormatPattern = pattern
        saveSettings()
        Log.d("FontManager", "Set dateFormatPattern=$dateFormatPattern")
    }

    fun getTimeFormatPattern(): String = timeFormatPattern
    fun getDateFormatPattern(): String = dateFormatPattern

    fun getTimeSize(): Float = timeSize
    fun getDateSize(): Float = dateSize
    fun getTimeAlpha(): Float = timeAlpha
    fun getDateAlpha(): Float = dateAlpha
    fun getTimeAlignment(): Int = timeAlignment
    fun getDateAlignment(): Int = dateAlignment

    private fun applyTimeFont() {
        val typeface = ResourcesCompat.getFont(context, fonts[timeFontIndex])
        timeText.typeface = typeface
        timeText.textSize = timeSize
        timeText.alpha = timeAlpha
        applyAlignmentToView(timeText, timeAlignment)

    }

    private fun applyDateFont() {
        val typeface = ResourcesCompat.getFont(context, fonts[dateFontIndex])
        dateText.typeface = typeface
        dateText.textSize = dateSize
        dateText.alpha = dateAlpha
        applyAlignmentToView(dateText, dateAlignment)
    }

    private fun applyAlignmentToView(view: TextView, alignment: Int) {
        view.textAlignment = alignment
        // If parent is a ConstraintLayout, update horizontal constraints so left/center/right move the view
        val lp = view.layoutParams
        if (lp is ConstraintLayout.LayoutParams) {
            when (alignment) {
                View.TEXT_ALIGNMENT_VIEW_START, View.TEXT_ALIGNMENT_TEXT_START -> {
                    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    lp.endToEnd = ConstraintLayout.LayoutParams.UNSET
                    lp.horizontalBias = 0f
                }
                View.TEXT_ALIGNMENT_CENTER -> {
                    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    lp.horizontalBias = 0.5f
                }
                View.TEXT_ALIGNMENT_VIEW_END, View.TEXT_ALIGNMENT_TEXT_END -> {
                    lp.startToStart = ConstraintLayout.LayoutParams.UNSET
                    lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    lp.horizontalBias = 1f
                }
                else -> {
                    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    lp.endToEnd = ConstraintLayout.LayoutParams.UNSET
                    lp.horizontalBias = 0f
                }
            }
            view.layoutParams = lp
            view.requestLayout()
        }
    }

    fun applyNightShiftTransition(currentTime: Date, sunTimeApi: SunTimeApi, enabled: Boolean) {
        if (!enabled) {
            timeText.setTextColor(Color.WHITE)
            dateText.setTextColor(Color.WHITE)
            Log.d("FontManager", "Night shift disabled, set color to white")
            return
        }

        // Ensure sun times are valid
        val sunrise = sunTimeApi.sunriseTime ?: run { sunTimeApi.setFallbackTimes(); sunTimeApi.sunriseTime!! }
        val sunset = sunTimeApi.sunsetTime ?: run { sunTimeApi.setFallbackTimes(); sunTimeApi.sunsetTime!! }

        // Define transition periods
        val preSunrise = Calendar.getInstance().apply { time = sunrise; add(Calendar.MINUTE, -40) }.time
        val postSunset = Calendar.getInstance().apply { time = sunset; add(Calendar.MINUTE, 30) }.time
        val fullNight = Calendar.getInstance().apply { time = postSunset; add(Calendar.MINUTE, 40) }.time

        val reddish = Color.rgb(255, 104, 104)
        val normal = Color.WHITE

        val color = when {
            currentTime.before(preSunrise) -> {
                Log.d("FontManager", "Night before pre-sunrise at $currentTime, using reddish")
                reddish // Full night before sunrise transition
            }
            currentTime.before(sunrise) -> {
                val factor = (currentTime.time - preSunrise.time).toFloat() / (sunrise.time - preSunrise.time)
                Log.d("FontManager", "Transitioning to day at $currentTime, factor=$factor")
                interpolateColor(reddish, normal, factor) // Transition to day
            }
            currentTime.before(postSunset) -> {
                Log.d("FontManager", "Daytime at $currentTime, using white")
                normal // Daytime
            }
            currentTime.before(fullNight) -> {
                val factor = (currentTime.time - postSunset.time).toFloat() / (fullNight.time - postSunset.time)
                Log.d("FontManager", "Transitioning to night at $currentTime, factor=$factor")
                interpolateColor(normal, reddish, factor) // Transition to night
            }
            else -> {
                Log.d("FontManager", "Full night at $currentTime, using reddish")
                reddish // Full night
            }
        }
        timeText.setTextColor(color)
        dateText.setTextColor(color)
    }

    private fun interpolateColor(color1: Int, color2: Int, factor: Float): Int {
        val clamped = factor.coerceIn(0f, 1f)
        val r = Color.red(color1) + ((Color.red(color2) - Color.red(color1)) * clamped).toInt()
        val g = Color.green(color1) + ((Color.green(color2) - Color.green(color1)) * clamped).toInt()
        val b = Color.blue(color1) + ((Color.blue(color2) - Color.blue(color1)) * clamped).toInt()
        return Color.rgb(r, g, b)
    }
}