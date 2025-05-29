package com.nxd1frnt.clockdesk2

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

class FontManager(private val context: Context, private val timeText: TextView, private val dateText: TextView) {
    private var timeFontIndex = 0
    private var dateFontIndex = 0
    private var timeSize = 48f // Default 48sp
    private var dateSize = 24f // Default 24sp
    private var timeAlpha = 1.0f
    private var dateAlpha = 1.0f
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
        applyTimeFont()
        applyDateFont()
        Log.d("FontManager", "Loaded: timeFont=$timeFontIndex, dateFont=$dateFontIndex, timeSize=$timeSize, dateSize=$dateSize, timeAlpha=$timeAlpha, dateAlpha=$dateAlpha")
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
            apply()
        }
    }

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


    fun getTimeSize(): Float = timeSize
    fun getDateSize(): Float = dateSize
    fun getTimeAlpha(): Float = timeAlpha
    fun getDateAlpha(): Float = dateAlpha

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
}