package com.nxd1frnt.clockdesk2.utils

import androidx.annotation.StringRes
import com.nxd1frnt.clockdesk2.R

enum class DateStyle(
    val id: String,
    @StringRes val titleRes: Int,
    val previewTop: String,
    val previewBottom: String?,
    val isTwoLine: Boolean = false,
    val isBadge: Boolean = false
) {
    STANDARD("STANDARD", R.string.date_style_standard, "Mon, Oct 4", null, isTwoLine = false, isBadge = false),
    TWO_LINE("TWO_LINE", R.string.date_style_two_line, "Mon", "Oct 4", isTwoLine = true, isBadge = false),
    CALENDAR_BADGE("CALENDAR_BADGE", R.string.date_style_calendar_badge, "OCT", "4", isTwoLine = false, isBadge = true);

    companion object {
        fun fromId(id: String?): DateStyle {
            return values().find { it.id == id } ?: STANDARD
        }
    }
}
