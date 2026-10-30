package com.nxd1frnt.clockdesk2.utils

/** Stable IDs are persisted in each desk's appearance preferences. */
enum class ClockDigitAnimation(val id: String) {
    SLIDE("slide"), FADE("fade"), FLIP("flip");

    companion object {
        fun fromId(id: String?): ClockDigitAnimation = entries.firstOrNull { it.id == id } ?: SLIDE
    }
}
