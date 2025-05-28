package com.nxd1frnt.clockdesk2

import android.content.Context
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

class FontManager(private val context: Context, private val timeText: TextView, private val dateText: TextView) {
    private val fonts = listOf(
        R.font.anton_regular,
        R.font.inflatevf,
        R.font.kanit_regular,
        R.font.sigmar_regular,
        R.font.monomakh_regular,
        R.font.orbitron_regular,
        R.font.dancingscript_regular,
        R.font.grapenuts_regular,
        R.font.madimione_regular,
        R.font.montserrat_regular,
        R.font.pressstart2p_regular,
        R.font.shafarik_regular
    )
    private var currentFontIndex = 0
    fun loadFont() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        currentFontIndex = prefs.getInt("fontIndex", 0)
        applyFont()
    }

    fun switchFont() {
        currentFontIndex = (currentFontIndex + 1) % fonts.size
        applyFont()
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putInt("fontIndex", currentFontIndex)
            apply()
        }
    }

    private fun applyFont() {
        val typeface = ResourcesCompat.getFont(context, fonts[currentFontIndex])
        timeText.typeface = typeface
        dateText.typeface = typeface
    }
}