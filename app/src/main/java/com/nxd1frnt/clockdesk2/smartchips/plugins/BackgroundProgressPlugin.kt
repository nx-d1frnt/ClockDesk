package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip
import com.nxd1frnt.clockdesk2.smartchips.setTextWithFade

class BackgroundProgressPlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "system_bg_progress"

    enum class Stage(val iconRes: Int, val defaultMessageRes: Int) {
        DOWNLOADING(R.drawable.image_refresh, R.string.stage_downloading),
        BLURRING(R.drawable.ic_blur, R.string.stage_blurring),            
        EXTRACTING_COLORS(R.drawable.ic_palette_swatch, R.string.stage_colors), 
        APPLYING_THEME(R.drawable.ic_cog, R.string.stage_theme),  
        IDLE(0, 0)
    }

companion object {
        var currentStage: Stage = Stage.IDLE
        var customMessage: String? = null
        var onGlobalStateChanged: (() -> Unit)? = null
    }

    override fun setOnStateChangeListener(listener: () -> Unit) {
        onGlobalStateChanged = listener
    }

    override fun createView(context: Context): View {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.smart_chip_layout, null, false)
        view.isClickable = false
        view.isFocusable = false
        return view
    }

   override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        if (currentStage == Stage.IDLE) return false

        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        if (iconView != null && iconView.tag != currentStage.iconRes) {
            iconView.setImageResource(currentStage.iconRes)
            iconView.tag = currentStage.iconRes
        }
        
        val text = customMessage ?: context.getString(currentStage.defaultMessageRes)
        if (textView != null) {
            textView.setTextWithFade(text)
        }

        return true
    }
}