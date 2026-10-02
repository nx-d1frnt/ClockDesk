package com.nxd1frnt.clockdesk2.ui.settings.components

sealed class SettingsItem {
    data class Header(val title: String) : SettingsItem()
    data class CardGroup(val items: List<SettingEntry>) : SettingsItem()
}

sealed class SettingEntry {
    abstract val key: String?
    abstract val title: String
    abstract val summary: String?
    abstract val iconRes: Int?
    abstract val isVisible: () -> Boolean
    abstract val isEnabled: () -> Boolean

    data class Switch(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val iconRes: Int? = null,
        val defaultValue: Boolean = false,
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true },
        val onCheckedChange: ((Boolean) -> Unit)? = null
    ) : SettingEntry()

    data class Clickable(
        override val key: String? = null,
        override val title: String,
        override val summary: String? = null,
        override val iconRes: Int? = null,
        val statusText: String? = null,
        val showChevron: Boolean = false,
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true },
        val onClick: () -> Unit
    ) : SettingEntry()

    data class SingleChoice(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val iconRes: Int? = null,
        val entries: Array<String>,
        val entryValues: Array<String>,
        val defaultValue: String = "",
        val useSimpleSummary: Boolean = true,
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true },
        val onSelectionChange: ((String) -> Unit)? = null
    ) : SettingEntry()

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
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true },
        val onValueChange: ((Float) -> Unit)? = null
    ) : SettingEntry()

    data class TextEdit(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val iconRes: Int? = null,
        val dialogTitle: String = title,
        val inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
        val defaultValue: String = "",
        val useSimpleSummary: Boolean = true,
        override val isVisible: () -> Boolean = { true },
        override val isEnabled: () -> Boolean = { true },
        val onTextChange: ((String) -> Unit)? = null
    ) : SettingEntry()
}
