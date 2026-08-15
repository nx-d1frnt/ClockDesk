package com.nxd1frnt.clockdesk2.utils

import androidx.annotation.StringRes
import com.nxd1frnt.clockdesk2.R

enum class ClockStyle(
    val id: String,
    @StringRes val titleRes: Int,
    val previewTop: String,
    val previewBottom: String?,
    val isTwoLine: Boolean = false,
    val isAnalog: Boolean = false,
    val hasBackdrop: Boolean = true
) {
    STANDARD("STANDARD", R.string.clock_style_standard, "09:30", null, isTwoLine = false),
    TWO_LINE("TWO_LINE", R.string.clock_style_two_line, "09", "30", isTwoLine = true),
    ANALOG("ANALOG", R.string.clock_style_analog, "12", "6", isTwoLine = false, isAnalog = true, hasBackdrop = true),
    ANALOG_MINIMAL("ANALOG_MINIMAL", R.string.clock_style_analog_minimal, "12", "6", isTwoLine = false, isAnalog = true, hasBackdrop = false);

    companion object {
        fun fromId(id: String?): ClockStyle {
            return values().find { it.id == id } ?: STANDARD
        }
    }
}
