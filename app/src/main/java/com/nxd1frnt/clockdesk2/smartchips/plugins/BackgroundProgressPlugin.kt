package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip

class BackgroundProgressPlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "system_bg_progress"

    override val priority: Int = 200

    enum class Stage(val iconRes: Int, val defaultMessageRes: Int) {
        DOWNLOADING(R.drawable.image_refresh, R.string.stage_downloading), // Нужно добавить строку
        BLURRING(R.drawable.ic_blur, R.string.stage_blurring),            // Иконка "глаз" или "фильтр"
        EXTRACTING_COLORS(R.drawable.ic_palette_swatch, R.string.stage_colors),  // Иконка "палитра"
        APPLYING_THEME(R.drawable.ic_cog, R.string.stage_theme),       // Иконка "настройки"
        IDLE(0, 0)
    }

companion object {
        var currentStage: Stage = Stage.IDLE
        // Доп. сообщение, если нужно переопределить стандартное (например, "50%")
        var customMessage: String? = null 
    }

    override fun createView(context: Context): View {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.smart_chip_layout, null, false)
        // Чип не кликабельный, просто информативный
        view.isClickable = false
        view.isFocusable = false
        return view
    }

   override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        if (currentStage == Stage.IDLE) return false

        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        // Устанавливаем иконку и текст в зависимости от стадии
        iconView.setImageResource(currentStage.iconRes)
        
        // Берем текст либо кастомный, либо дефолтный из Enum
        val text = customMessage ?: context.getString(currentStage.defaultMessageRes)
        textView.text = text

        return true
    }
}