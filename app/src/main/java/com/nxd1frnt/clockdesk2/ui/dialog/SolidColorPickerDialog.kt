package com.nxd1frnt.clockdesk2.ui.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setMargins
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

object SolidColorPickerDialog {

    fun show(
        context: Context,
        initialColor: Int,
        onColorSelected: (Int) -> Unit
    ) {
        val themedContext = UiMonetThemeManager.wrapContext(context)
        val palette = UiMonetThemeManager.getPalette(themedContext)

        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_solid_color_picker, null)
        val colorPickerView = dialogView.findViewById<com.skydoves.colorpickerview.ColorPickerView>(R.id.color_picker_view)
        val brightnessSlideBar = dialogView.findViewById<com.skydoves.colorpickerview.sliders.BrightnessSlideBar>(R.id.brightness_slide_bar)
        val previewBox = dialogView.findViewById<View>(R.id.color_preview_box)
        val hexText = dialogView.findViewById<TextView>(R.id.hex_text)
        val presetsContainer = dialogView.findViewById<LinearLayout>(R.id.preset_swatches_container)

        hexText.setTextColor(palette.onSurface)

        colorPickerView.attachBrightnessSlider(brightnessSlideBar)
        colorPickerView.setInitialColor(initialColor)

        val preventDisallowIntercept: (View) -> Unit = { v ->
            v.setOnTouchListener { target, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> target.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
        preventDisallowIntercept(colorPickerView)
        preventDisallowIntercept(brightnessSlideBar)

        var selectedColor = initialColor
        previewBox.setBackgroundColor(selectedColor)
        hexText.text = String.format("#%06X", 0xFFFFFF and selectedColor)

        colorPickerView.setColorListener(ColorEnvelopeListener { envelope, _ ->
            selectedColor = envelope.color
            previewBox.setBackgroundColor(selectedColor)
            hexText.text = String.format("#%06X", 0xFFFFFF and selectedColor)
        })

        // Standard swatches: AMOLED Black, Dark Gray, Midnight Navy, Dark Forest, Deep Plum, Monet accents
        val presets = listOf(
            Color.BLACK,
            Color.parseColor("#121212"),
            Color.parseColor("#1E1E1E"),
            Color.parseColor("#0F172A"),
            Color.parseColor("#091E13"),
            Color.parseColor("#1C0A26"),
            palette.surfaceContainerHighest,
            palette.primary,
            palette.secondary,
            palette.tertiary
        )

        val dp36 = (36 * themedContext.resources.displayMetrics.density).toInt()
        val dp4 = (4 * themedContext.resources.displayMetrics.density).toInt()

        presets.forEach { presetColor ->
            val card = MaterialCardView(themedContext).apply {
                layoutParams = LinearLayout.LayoutParams(dp36, dp36).apply {
                    setMargins(dp4)
                }
                radius = (dp36 / 2f)
                cardElevation = 0f
                strokeWidth = (1.5f * themedContext.resources.displayMetrics.density).toInt()
                strokeColor = palette.outlineVariant
                setCardBackgroundColor(presetColor)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedColor = presetColor
                    previewBox.setBackgroundColor(selectedColor)
                    hexText.text = String.format("#%06X", 0xFFFFFF and selectedColor)
                    colorPickerView.setInitialColor(selectedColor)
                }
            }
            presetsContainer.addView(card)
        }

        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setTitle(themedContext.getString(R.string.background_provider_solid_color))
            .setView(dialogView)
            .setPositiveButton(themedContext.getString(R.string.apply)) { d, _ ->
                onColorSelected(selectedColor)
                d.dismiss()
            }
            .setNegativeButton(themedContext.getString(R.string.cancel)) { d, _ -> d.dismiss() }
            .show()

        UiMonetThemeManager.styleAlertDialog(dialog, palette)
    }
}
