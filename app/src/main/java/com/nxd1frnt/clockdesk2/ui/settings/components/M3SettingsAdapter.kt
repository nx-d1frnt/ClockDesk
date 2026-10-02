package com.nxd1frnt.clockdesk2.ui.settings.components

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.text.InputType
import android.util.TypedValue
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
    private var items: List<SettingsItem>,
    private val onPreferenceChanged: (key: String, value: Any) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_CARD_GROUP = 1
    }

    fun submitList(newItems: List<SettingsItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SettingsItem.Header -> TYPE_HEADER
            is SettingsItem.CardGroup -> TYPE_CARD_GROUP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_settings_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_CARD_GROUP -> {
                val view = inflater.inflate(R.layout.item_settings_card_group, parent, false)
                CardGroupViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SettingsItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SettingsItem.CardGroup -> (holder as CardGroupViewHolder).bind(item)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.header_title)
        fun bind(header: SettingsItem.Header) {
            titleView.text = header.title
        }
    }

    inner class CardGroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: LinearLayout = view.findViewById(R.id.items_container)

        fun bind(group: SettingsItem.CardGroup) {
            container.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)

            val visibleItems = group.items.filter { it.isVisible() }
            if (visibleItems.isEmpty()) {
                itemView.visibility = View.GONE
                itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
                return
            } else {
                itemView.visibility = View.VISIBLE
                itemView.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            visibleItems.forEachIndexed { index, entry ->
                val rowView = when (entry) {
                    is SettingEntry.Switch -> createSwitchView(inflater, container, entry)
                    is SettingEntry.Clickable -> createClickableView(inflater, container, entry)
                    is SettingEntry.SingleChoice -> createChoiceView(inflater, container, entry)
                    is SettingEntry.Slider -> createSliderView(inflater, container, entry)
                    is SettingEntry.TextEdit -> createTextEditView(inflater, container, entry)
                }

                if (rowView is MaterialCardView) {
                    val shapeStyleRes = when {
                        visibleItems.size == 1 -> R.style.ShapeAppearance_ClockDesk_Single
                        index == 0 -> R.style.ShapeAppearance_ClockDesk_Top
                        index == visibleItems.lastIndex -> R.style.ShapeAppearance_ClockDesk_Bottom
                        else -> R.style.ShapeAppearance_ClockDesk_Middle
                    }
                    rowView.shapeAppearanceModel = ShapeAppearanceModel.builder(context, shapeStyleRes, 0).build()

                    val marginBottomDp = if (index == visibleItems.lastIndex) 0 else 4
                    val marginParams = rowView.layoutParams as? ViewGroup.MarginLayoutParams
                        ?: ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    marginParams.bottomMargin = (marginBottomDp * context.resources.displayMetrics.density).toInt()
                    rowView.layoutParams = marginParams
                }

                container.addView(rowView)
            }
        }

        private fun createSwitchView(
            inflater: LayoutInflater,
            parent: ViewGroup,
            entry: SettingEntry.Switch
        ): View {
            val view = inflater.inflate(R.layout.item_settings_switch, parent, false)
            val titleView = view.findViewById<TextView>(R.id.item_title)
            val summaryView = view.findViewById<TextView>(R.id.item_summary)
            val iconView = view.findViewById<ImageView>(R.id.item_icon)
            val switchView = view.findViewById<MaterialSwitch>(R.id.item_switch)

            titleView.text = entry.title
            if (!entry.summary.isNullOrBlank()) {
                summaryView.text = entry.summary
                summaryView.visibility = View.VISIBLE
            } else {
                summaryView.visibility = View.GONE
            }

            if (entry.iconRes != null) {
                iconView.setImageResource(entry.iconRes!!)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            val isChecked = prefs.getBoolean(entry.key, entry.defaultValue)
            switchView.isChecked = isChecked

            val isEnabled = entry.isEnabled()
            view.isEnabled = isEnabled
            switchView.isEnabled = isEnabled
            view.alpha = if (isEnabled) 1.0f else 0.38f

            if (isEnabled) {
                view.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val newChecked = !switchView.isChecked
                    switchView.isChecked = newChecked
                    prefs.edit().putBoolean(entry.key, newChecked).apply()
                    entry.onCheckedChange?.invoke(newChecked)
                    onPreferenceChanged(entry.key, newChecked)
                    notifyDataSetChanged()
                }
            } else {
                view.setOnClickListener(null)
            }

            return view
        }

        private fun createClickableView(
            inflater: LayoutInflater,
            parent: ViewGroup,
            entry: SettingEntry.Clickable
        ): View {
            val view = inflater.inflate(R.layout.item_settings_clickable, parent, false)
            val titleView = view.findViewById<TextView>(R.id.item_title)
            val summaryView = view.findViewById<TextView>(R.id.item_summary)
            val iconView = view.findViewById<ImageView>(R.id.item_icon)
            val statusView = view.findViewById<TextView>(R.id.item_status_text)
            val chevronView = view.findViewById<ImageView>(R.id.item_chevron)

            titleView.text = entry.title
            if (!entry.summary.isNullOrBlank()) {
                summaryView.text = entry.summary
                summaryView.visibility = View.VISIBLE
            } else {
                summaryView.visibility = View.GONE
            }

            if (entry.iconRes != null) {
                iconView.setImageResource(entry.iconRes!!)
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
            view.isEnabled = isEnabled
            view.alpha = if (isEnabled) 1.0f else 0.38f

            if (isEnabled) {
                view.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    entry.onClick()
                }
            } else {
                view.setOnClickListener(null)
            }

            return view
        }

        private fun createChoiceView(
            inflater: LayoutInflater,
            parent: ViewGroup,
            entry: SettingEntry.SingleChoice
        ): View {
            val view = inflater.inflate(R.layout.item_settings_clickable, parent, false)
            val titleView = view.findViewById<TextView>(R.id.item_title)
            val summaryView = view.findViewById<TextView>(R.id.item_summary)
            val iconView = view.findViewById<ImageView>(R.id.item_icon)

            titleView.text = entry.title
            if (entry.iconRes != null) {
                iconView.setImageResource(entry.iconRes!!)
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
            view.isEnabled = isEnabled
            view.alpha = if (isEnabled) 1.0f else 0.38f

            if (isEnabled) {
                view.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    showSingleChoiceDialog(entry)
                }
            } else {
                view.setOnClickListener(null)
            }

            return view
        }

        private fun showSingleChoiceDialog(entry: SettingEntry.SingleChoice) {
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
                        notifyDataSetChanged()
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun createSliderView(
            inflater: LayoutInflater,
            parent: ViewGroup,
            entry: SettingEntry.Slider
        ): View {
            val view = inflater.inflate(R.layout.item_settings_slider, parent, false)
            val titleView = view.findViewById<TextView>(R.id.item_title)
            val summaryView = view.findViewById<TextView>(R.id.item_summary)
            val iconView = view.findViewById<ImageView>(R.id.item_icon)
            val valueLabel = view.findViewById<TextView>(R.id.item_value_label)
            val slider = view.findViewById<Slider>(R.id.item_slider)

            titleView.text = entry.title
            if (!entry.summary.isNullOrBlank()) {
                summaryView.text = entry.summary
                summaryView.visibility = View.VISIBLE
            } else {
                summaryView.visibility = View.GONE
            }

            if (entry.iconRes != null) {
                iconView.setImageResource(entry.iconRes!!)
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
            view.isEnabled = isEnabled
            slider.isEnabled = isEnabled
            view.alpha = if (isEnabled) 1.0f else 0.38f

            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    updateLabel(value)
                }
            }

            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) {
                    slider.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val value = slider.value
                    writeSliderPreference(entry.key, value, entry.step)
                    entry.onValueChange?.invoke(value)
                    onPreferenceChanged(entry.key, value)
                    notifyDataSetChanged()
                }
            })

            return view
        }

        private fun createTextEditView(
            inflater: LayoutInflater,
            parent: ViewGroup,
            entry: SettingEntry.TextEdit
        ): View {
            val view = inflater.inflate(R.layout.item_settings_clickable, parent, false)
            val titleView = view.findViewById<TextView>(R.id.item_title)
            val summaryView = view.findViewById<TextView>(R.id.item_summary)
            val iconView = view.findViewById<ImageView>(R.id.item_icon)

            titleView.text = entry.title
            if (entry.iconRes != null) {
                iconView.setImageResource(entry.iconRes!!)
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
            view.isEnabled = isEnabled
            view.alpha = if (isEnabled) 1.0f else 0.38f

            if (isEnabled) {
                view.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    showTextEditDialog(entry)
                }
            } else {
                view.setOnClickListener(null)
            }

            return view
        }

        private fun showTextEditDialog(entry: SettingEntry.TextEdit) {
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
                    notifyDataSetChanged()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
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
}
