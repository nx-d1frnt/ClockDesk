package com.nxd1frnt.clockdesk2.ui.settings.components

import android.text.InputType

sealed class SettingsItem {
    data class Header(val title: String) : SettingsItem()

    sealed class Entry : SettingsItem() {
        abstract val key: String?
        abstract val title: String
        abstract val summary: String?
        abstract val iconRes: Int?
        abstract val groupId: Int
        abstract val isVisible: () -> Boolean
        abstract val isEnabled: () -> Boolean
    }

    data class Switch(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val iconRes: Int? = null,
        val defaultValue: Boolean = false,
        override val groupId: Int = 0,
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true },
        val onCheckedChange: ((Boolean) -> Unit)? = null
    ) : Entry()

    data class Clickable(
        override val key: String? = null,
        override val title: String,
        override val summary: String? = null,
        override val iconRes: Int? = null,
        val statusText: String? = null,
        val showChevron: Boolean = false,
        override val groupId: Int = 0,
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true },
        val onClick: () -> Unit
    ) : Entry()

    data class SingleChoice(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val iconRes: Int? = null,
        val entries: Array<String>,
        val entryValues: Array<String>,
        val defaultValue: String = "",
        val useSimpleSummary: Boolean = true,
        override val groupId: Int = 0,
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true },
        val onSelectionChange: ((String) -> Unit)? = null
    ) : Entry()

    data class Slider(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val iconRes: Int? = null,
        val min: Float,
        val max: Float,
        val step: Float = 1f,
        val defaultValue: Float = min,
        val valueFormatter: ((Float) -> String)? = null,
        override val groupId: Int = 0,
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true },
        val onValueChange: ((Float) -> Unit)? = null
    ) : Entry()

    data class TextEdit(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val iconRes: Int? = null,
        val dialogTitle: String = title,
        val inputType: Int = InputType.TYPE_CLASS_TEXT,
        val defaultValue: String = "",
        val useSimpleSummary: Boolean = true,
        override val groupId: Int = 0,
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true },
        val onTextChange: ((String) -> Unit)? = null
    ) : Entry()

    data class BurnInPreview(
        override val key: String = "burn_in_shift_distance_dp",
        override val title: String = "",
        override val summary: String? = null,
        override val iconRes: Int? = null,
        override val groupId: Int = 0,
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true }
    ) : Entry()

    data class SmartPixelsPreview(
        override val key: String = "smart_pixels_intensity",
        override val title: String = "",
        override val summary: String? = null,
        override val iconRes: Int? = null,
        override val groupId: Int = 0,
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true }
    ) : Entry()
}

typealias SettingEntry = SettingsItem.Entry
