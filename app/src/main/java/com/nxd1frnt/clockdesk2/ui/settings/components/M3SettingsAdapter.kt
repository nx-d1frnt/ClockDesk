package com.nxd1frnt.clockdesk2.ui.settings.components

import android.content.Context
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nxd1frnt.clockdesk2.R

class M3SettingsAdapter(
    private val context: Context,
    private val prefs: SharedPreferences,
    items: List<SettingsItem>,
    private val onPreferenceChanged: (key: String, value: Any) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SWITCH = 1
        private const val TYPE_CLICKABLE = 2
        private const val TYPE_CHOICE = 3
        private const val TYPE_SLIDER = 4
        private const val TYPE_TEXT_EDIT = 5
    }

    private var displayItems: List<SettingsItem> = emptyList()

    init {
        updateDisplayList(items)
    }

    fun submitList(newItems: List<SettingsItem>) {
        updateDisplayList(newItems)
        notifyDataSetChanged()
    }

    private fun updateDisplayList(rawItems: List<SettingsItem>) {
        val visibleList = rawItems.filter { item ->
            when (item) {
                is SettingsItem.Header -> true
                is SettingsItem.Entry -> item.isVisible()
            }
        }

        val result = mutableListOf<SettingsItem>()
        for (i in visibleList.indices) {
            val item = visibleList[i]
            if (item is SettingsItem.Header) {
                var hasEntries = false
                for (j in (i + 1) until visibleList.size) {
                    if (visibleList[j] is SettingsItem.Header) break
                    if (visibleList[j] is SettingsItem.Entry) {
                        hasEntries = true
                        break
                    }
                }
                if (hasEntries) {
                    result.add(item)
                }
            } else {
                result.add(item)
            }
        }
        displayItems = result
    }

    override fun getItemCount(): Int = displayItems.size

    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is SettingsItem.Header -> TYPE_HEADER
            is SettingsItem.Switch -> TYPE_SWITCH
            is SettingsItem.Clickable -> TYPE_CLICKABLE
            is SettingsItem.SingleChoice -> TYPE_CHOICE
            is SettingsItem.Slider -> TYPE_SLIDER
            is SettingsItem.TextEdit -> TYPE_TEXT_EDIT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_settings_header, parent, false))
            TYPE_SWITCH -> SwitchViewHolder(inflater.inflate(R.layout.item_settings_switch, parent, false))
            TYPE_CLICKABLE -> ClickableViewHolder(inflater.inflate(R.layout.item_settings_clickable, parent, false))
            TYPE_CHOICE -> ChoiceViewHolder(inflater.inflate(R.layout.item_settings_clickable, parent, false))
            TYPE_SLIDER -> SliderViewHolder(inflater.inflate(R.layout.item_settings_slider, parent, false))
            TYPE_TEXT_EDIT -> TextEditViewHolder(inflater.inflate(R.layout.item_settings_clickable, parent, false))
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is SettingsItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SettingsItem.Switch -> (holder as SwitchViewHolder).bind(item, position)
            is SettingsItem.Clickable -> (holder as ClickableViewHolder).bind(item, position)
            is SettingsItem.SingleChoice -> (holder as ChoiceViewHolder).bind(item, position)
            is SettingsItem.Slider -> (holder as SliderViewHolder).bind(item, position)
            is SettingsItem.TextEdit -> (holder as TextEditViewHolder).bind(item, position)
        }
    }

    private fun applyCardShapeAndMargin(cardView: MaterialCardView, position: Int) {
        val currentItem = displayItems.getOrNull(position) as? SettingsItem.Entry ?: return
        val currentGroupId = currentItem.groupId

        val prevItem = displayItems.getOrNull(position - 1) as? SettingsItem.Entry
        val nextItem = displayItems.getOrNull(position + 1) as? SettingsItem.Entry

        val hasPrev = prevItem != null && prevItem.groupId == currentGroupId
        val hasNext = nextItem != null && nextItem.groupId == currentGroupId

        val shapeStyleRes = when {
            !hasPrev && !hasNext -> R.style.ShapeAppearance_ClockDesk_Single
            !hasPrev && hasNext -> R.style.ShapeAppearance_ClockDesk_Top
            hasPrev && hasNext -> R.style.ShapeAppearance_ClockDesk_Middle
            else -> R.style.ShapeAppearance_ClockDesk_Bottom
        }

        cardView.shapeAppearanceModel = ShapeAppearanceModel.builder(context, shapeStyleRes, 0).build()

        val marginBottomDp = if (hasNext) 4 else 16
        val marginParams = cardView.layoutParams as? ViewGroup.MarginLayoutParams
            ?: ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        marginParams.bottomMargin = (marginBottomDp * context.resources.displayMetrics.density).toInt()
        cardView.layoutParams = marginParams
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.header_title)
        fun bind(header: SettingsItem.Header) {
            titleView.text = header.title
        }
    }

    inner class SwitchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val titleView: TextView = view.findViewById(R.id.item_title)
        private val summaryView: TextView = view.findViewById(R.id.item_summary)
        private val iconView: ImageView = view.findViewById(R.id.item_icon)
        private val switchView: MaterialSwitch = view.findViewById(R.id.item_switch)

        fun bind(entry: SettingsItem.Switch, position: Int) {
            applyCardShapeAndMargin(cardView, position)

            titleView.text = entry.title
            if (!entry.summary.isNullOrBlank()) {
                summaryView.text = entry.summary
                summaryView.visibility = View.VISIBLE
            } else {
                summaryView.visibility = View.GONE
            }

            if (entry.iconRes != null) {
                iconView.setImageResource(entry.iconRes)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            val isChecked = prefs.getBoolean(entry.key, entry.defaultValue)
            switchView.isChecked = isChecked

            val isEnabled = entry.isEnabled()
            cardView.isEnabled = isEnabled
            switchView.isEnabled = isEnabled
            cardView.alpha = if (isEnabled) 1.0f else 0.38f

            if (isEnabled) {
                cardView.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val newChecked = !switchView.isChecked
                    switchView.isChecked = newChecked
                    prefs.edit().putBoolean(entry.key, newChecked).apply()
                    entry.onCheckedChange?.invoke(newChecked)
                    onPreferenceChanged(entry.key, newChecked)
                }
            } else {
                cardView.setOnClickListener(null)
            }
        }
    }

    inner class ClickableViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val titleView: TextView = view.findViewById(R.id.item_title)
        private val summaryView: TextView = view.findViewById(R.id.item_summary)
        private val iconView: ImageView = view.findViewById(R.id.item_icon)
        private val statusView: TextView = view.findViewById(R.id.item_status_text)
        private val chevronView: ImageView = view.findViewById(R.id.item_chevron)

        fun bind(entry: SettingsItem.Clickable, position: Int) {
            applyCardShapeAndMargin(cardView, position)

            titleView.text = entry.title
            if (!entry.summary.isNullOrBlank()) {
                summaryView.text = entry.summary
                summaryView.visibility = View.VISIBLE
            } else {
                summaryView.visibility = View.GONE
            }

            if (entry.iconRes != null) {
                iconView.setImageResource(entry.iconRes)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            if (!entry.statusText.isNullOrBlank()) {
                statusView.text = entry.statusText
                statusView.visibility = View.VISIBLE
            } else {
                statusView.visibility = View.GONE
            }

            chevronView.visibility = if (entry.showChevron) View.VISIBLE else View.GONE

            val isEnabled = entry.isEnabled()
            cardView.isEnabled = isEnabled
            cardView.alpha = if (isEnabled) 1.0f else 0.38f

            if (isEnabled) {
                cardView.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    entry.onClick()
                }
            } else {
                cardView.setOnClickListener(null)
            }
        }
    }

    inner class ChoiceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val titleView: TextView = view.findViewById(R.id.item_title)
        private val summaryView: TextView = view.findViewById(R.id.item_summary)
        private val iconView: ImageView = view.findViewById(R.id.item_icon)

        fun bind(entry: SettingsItem.SingleChoice, position: Int) {
            applyCardShapeAndMargin(cardView, position)

            titleView.text = entry.title
            if (entry.iconRes != null) {
                iconView.setImageResource(entry.iconRes)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            val currentValue = prefs.getString(entry.key, entry.defaultValue) ?: entry.defaultValue
            val selectedIndex = entry.entryValues.indexOf(currentValue).coerceAtLeast(0)

            val displaySummary = if (entry.useSimpleSummary && selectedIndex in entry.entries.indices) {
                entry.entries[selectedIndex]
            } else {
                entry.summary
            }

            if (!displaySummary.isNullOrBlank()) {
                summaryView.text = displaySummary
                summaryView.visibility = View.VISIBLE
            } else {
                summaryView.visibility = View.GONE
            }

            val isEnabled = entry.isEnabled()
            cardView.isEnabled = isEnabled
            cardView.alpha = if (isEnabled) 1.0f else 0.38f

            if (isEnabled) {
                cardView.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    showSingleChoiceDialog(entry)
                }
            } else {
                cardView.setOnClickListener(null)
            }
        }

        private fun showSingleChoiceDialog(entry: SettingsItem.SingleChoice) {
            val currentValue = prefs.getString(entry.key, entry.defaultValue) ?: entry.defaultValue
            val currentIndex = entry.entryValues.indexOf(currentValue).coerceAtLeast(0)
            var tempSelectedIndex = currentIndex

            MaterialAlertDialogBuilder(context, R.style.ClockDesk_Dialog_Theme)
                .setTitle(entry.title)
                .setSingleChoiceItems(entry.entries, currentIndex) { _, which ->
                    tempSelectedIndex = which
                }
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    if (tempSelectedIndex in entry.entryValues.indices) {
                        val newValue = entry.entryValues[tempSelectedIndex]
                        prefs.edit().putString(entry.key, newValue).apply()
                        entry.onSelectionChange?.invoke(newValue)
                        onPreferenceChanged(entry.key, newValue)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    inner class SliderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val titleView: TextView = view.findViewById(R.id.item_title)
        private val summaryView: TextView = view.findViewById(R.id.item_summary)
        private val iconView: ImageView = view.findViewById(R.id.item_icon)
        private val valueLabel: TextView = view.findViewById(R.id.item_value_label)
        private val slider: Slider = view.findViewById(R.id.item_slider)

        fun bind(entry: SettingsItem.Slider, position: Int) {
            applyCardShapeAndMargin(cardView, position)

            titleView.text = entry.title
            if (!entry.summary.isNullOrBlank()) {
                summaryView.text = entry.summary
                summaryView.visibility = View.VISIBLE
            } else {
                summaryView.visibility = View.GONE
            }

            if (entry.iconRes != null) {
                iconView.setImageResource(entry.iconRes)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            val savedValue = readSliderPreference(entry.key, entry.defaultValue)
            val clampedValue = savedValue.coerceIn(entry.min, entry.max)

            slider.valueFrom = entry.min
            slider.valueTo = entry.max
            slider.stepSize = entry.step
            slider.value = clampedValue

            fun updateLabel(v: Float) {
                valueLabel.text = entry.valueFormatter?.invoke(v) ?: if (entry.step >= 1.0f) {
                    v.toInt().toString()
                } else {
                    String.format("%.1f", v)
                }
            }

            updateLabel(clampedValue)

            val isEnabled = entry.isEnabled()
            cardView.isEnabled = isEnabled
            slider.isEnabled = isEnabled
            cardView.alpha = if (isEnabled) 1.0f else 0.38f

            slider.clearOnChangeListeners()
            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    updateLabel(value)
                }
            }

            slider.clearOnSliderTouchListeners()
            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(s: Slider) {}
                override fun onStopTrackingTouch(s: Slider) {
                    s.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val value = s.value
                    writeSliderPreference(entry.key, value, entry.step)
                    entry.onValueChange?.invoke(value)
                    onPreferenceChanged(entry.key, value)
                }
            })
        }

        private fun readSliderPreference(key: String, defaultValue: Float): Float {
            return try {
                prefs.getInt(key, defaultValue.toInt()).toFloat()
            } catch (e: Exception) {
                try {
                    prefs.getFloat(key, defaultValue)
                } catch (e2: Exception) {
                    defaultValue
                }
            }
        }

        private fun writeSliderPreference(key: String, value: Float, step: Float) {
            if (step >= 1.0f) {
                prefs.edit().putInt(key, value.toInt()).apply()
            } else {
                prefs.edit().putFloat(key, value).apply()
            }
        }
    }

    inner class TextEditViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val titleView: TextView = view.findViewById(R.id.item_title)
        private val summaryView: TextView = view.findViewById(R.id.item_summary)
        private val iconView: ImageView = view.findViewById(R.id.item_icon)

        fun bind(entry: SettingsItem.TextEdit, position: Int) {
            applyCardShapeAndMargin(cardView, position)

            titleView.text = entry.title
            if (entry.iconRes != null) {
                iconView.setImageResource(entry.iconRes)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            val currentValue = prefs.getString(entry.key, entry.defaultValue) ?: entry.defaultValue
            val displaySummary = if (entry.useSimpleSummary && currentValue.isNotBlank()) {
                currentValue
            } else {
                entry.summary
            }

            if (!displaySummary.isNullOrBlank()) {
                summaryView.text = displaySummary
                summaryView.visibility = View.VISIBLE
            } else {
                summaryView.visibility = View.GONE
            }

            val isEnabled = entry.isEnabled()
            cardView.isEnabled = isEnabled
            cardView.alpha = if (isEnabled) 1.0f else 0.38f

            if (isEnabled) {
                cardView.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    showTextEditDialog(entry)
                }
            } else {
                cardView.setOnClickListener(null)
            }
        }

        private fun showTextEditDialog(entry: SettingsItem.TextEdit) {
            val currentValue = prefs.getString(entry.key, entry.defaultValue) ?: entry.defaultValue

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (20 * context.resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, pad / 2)
            }

            val textInputLayout = TextInputLayout(context).apply {
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                hint = entry.title
            }

            val editText = TextInputEditText(context).apply {
                setText(currentValue)
                inputType = entry.inputType
                setSingleLine(true)
            }

            textInputLayout.addView(editText)
            layout.addView(textInputLayout)

            MaterialAlertDialogBuilder(context, R.style.ClockDesk_Dialog_Theme)
                .setTitle(entry.dialogTitle)
                .setView(layout)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    val text = editText.text?.toString().orEmpty().trim()
                    prefs.edit().putString(entry.key, text).apply()
                    entry.onTextChange?.invoke(text)
                    onPreferenceChanged(entry.key, text)
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
