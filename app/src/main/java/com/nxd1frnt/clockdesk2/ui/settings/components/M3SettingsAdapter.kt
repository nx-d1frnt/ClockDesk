package com.nxd1frnt.clockdesk2.ui.settings.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.PaintDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.connect.ui.DeskNotificationHelper
import com.nxd1frnt.clockdesk2.ui.adapters.ColorAdapter
import com.nxd1frnt.clockdesk2.ui.adapters.FontAdapter
import com.nxd1frnt.clockdesk2.utils.ColorItem
import com.nxd1frnt.clockdesk2.utils.FontAxis
import com.nxd1frnt.clockdesk2.utils.SmartPixelManager
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

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
        private const val TYPE_BURN_IN_PREVIEW = 6
        private const val TYPE_SMART_PIXELS_PREVIEW = 7
        private const val TYPE_NOTIFICATION_PREVIEW = 8
        private const val TYPE_NOTIFICATION_APPEARANCE = 9
        private const val TYPE_BATTERY_STATUS = 10
        private const val TYPE_TURBULENCE_PREVIEW = 11
        private const val TYPE_LIGHT_LEVEL = 12
        private const val TYPE_UPDATE_STATUS = 13

        private const val PAYLOAD_UPDATE_PREVIEW = "payload_update_notification_preview"
    }

    private var displayItems: List<SettingsItem> = emptyList()
    private var isEditingNotificationBg: Boolean = false
    private var activeTurbulenceView: com.nxd1frnt.clockdesk2.ui.view.TurbulenceView? = null

    init {
        updateDisplayList(items)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is TurbulencePreviewViewHolder && activeTurbulenceView == holder.turbulenceView) {
            activeTurbulenceView = null
        }
    }

    fun submitList(newItems: List<SettingsItem>) {
        updateDisplayList(newItems)
        notifyDataSetChanged()
    }

    fun notifyNotificationPreviewChanged(animatePosition: Boolean = false) {
        val index = displayItems.indexOfFirst { it is SettingsItem.NotificationPreview }
        if (index != -1) {
            notifyItemChanged(index, if (animatePosition) "animate" else PAYLOAD_UPDATE_PREVIEW)
        }
    }

    fun updateLightLevel(lux: Float) {
        val index = displayItems.indexOfFirst { it is SettingsItem.LightLevel }
        if (index != -1) {
            val item = displayItems[index] as? SettingsItem.LightLevel ?: return
            item.luxValue = lux
            notifyItemChanged(index, lux)
        }
    }

    private fun updateDisplayList(rawItems: List<SettingsItem>) {
        val visibleList = rawItems.filter { item ->
            when (item) {
                is SettingsItem.Header -> true
                is SettingsItem.UpdateStatus -> true
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
            is SettingsItem.BurnInPreview -> TYPE_BURN_IN_PREVIEW
            is SettingsItem.SmartPixelsPreview -> TYPE_SMART_PIXELS_PREVIEW
            is SettingsItem.NotificationPreview -> TYPE_NOTIFICATION_PREVIEW
            is SettingsItem.NotificationAppearance -> TYPE_NOTIFICATION_APPEARANCE
            is SettingsItem.BatteryStatus -> TYPE_BATTERY_STATUS
            is SettingsItem.TurbulencePreview -> TYPE_TURBULENCE_PREVIEW
            is SettingsItem.LightLevel -> TYPE_LIGHT_LEVEL
            is SettingsItem.UpdateStatus -> TYPE_UPDATE_STATUS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_settings_header, parent, false))
            TYPE_UPDATE_STATUS -> UpdateStatusViewHolder(inflater.inflate(R.layout.item_settings_update_status, parent, false))
            TYPE_SWITCH -> SwitchViewHolder(inflater.inflate(R.layout.item_settings_switch, parent, false))
            TYPE_CLICKABLE -> ClickableViewHolder(inflater.inflate(R.layout.item_settings_clickable, parent, false))
            TYPE_CHOICE -> ChoiceViewHolder(inflater.inflate(R.layout.item_settings_clickable, parent, false))
            TYPE_SLIDER -> SliderViewHolder(inflater.inflate(R.layout.item_settings_slider, parent, false))
            TYPE_TEXT_EDIT -> TextEditViewHolder(inflater.inflate(R.layout.item_settings_clickable, parent, false))
            TYPE_BURN_IN_PREVIEW -> BurnInPreviewViewHolder(inflater.inflate(R.layout.item_settings_burn_in_preview, parent, false))
            TYPE_SMART_PIXELS_PREVIEW -> SmartPixelsPreviewViewHolder(inflater.inflate(R.layout.item_settings_smart_pixels_preview, parent, false))
            TYPE_NOTIFICATION_PREVIEW -> NotificationPreviewViewHolder(inflater.inflate(R.layout.item_settings_notification_preview, parent, false))
            TYPE_NOTIFICATION_APPEARANCE -> NotificationAppearanceViewHolder(inflater.inflate(R.layout.item_settings_notification_appearance, parent, false))
            TYPE_BATTERY_STATUS -> BatteryStatusViewHolder(inflater.inflate(R.layout.item_settings_battery_status, parent, false))
            TYPE_TURBULENCE_PREVIEW -> TurbulencePreviewViewHolder(inflater.inflate(R.layout.item_settings_turbulence_preview, parent, false))
            TYPE_LIGHT_LEVEL -> LightLevelViewHolder(inflater.inflate(R.layout.item_settings_clickable, parent, false))
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            if (holder is NotificationPreviewViewHolder) {
                val item = displayItems[position] as? SettingsItem.NotificationPreview
                if (item != null) {
                    holder.updatePreview(item, playEntrance = payloads.contains("animate"))
                    return
                }
            } else if (holder is LightLevelViewHolder) {
                val item = displayItems[position] as? SettingsItem.LightLevel
                if (item != null) {
                    val lux = payloads.firstOrNull { it is Float } as? Float ?: item.luxValue
                    holder.updateValues(item.hasSensor, lux, item.summary)
                    return
                }
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayItems[position]) {
            is SettingsItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SettingsItem.UpdateStatus -> (holder as UpdateStatusViewHolder).bind(item)
            is SettingsItem.Switch -> (holder as SwitchViewHolder).bind(item, position)
            is SettingsItem.Clickable -> (holder as ClickableViewHolder).bind(item, position)
            is SettingsItem.SingleChoice -> (holder as ChoiceViewHolder).bind(item, position)
            is SettingsItem.Slider -> (holder as SliderViewHolder).bind(item, position)
            is SettingsItem.TextEdit -> (holder as TextEditViewHolder).bind(item, position)
            is SettingsItem.BurnInPreview -> (holder as BurnInPreviewViewHolder).bind(item, position)
            is SettingsItem.SmartPixelsPreview -> (holder as SmartPixelsPreviewViewHolder).bind(item, position)
            is SettingsItem.NotificationPreview -> (holder as NotificationPreviewViewHolder).bind(item, position)
            is SettingsItem.NotificationAppearance -> (holder as NotificationAppearanceViewHolder).bind(item, position)
            is SettingsItem.BatteryStatus -> (holder as BatteryStatusViewHolder).bind(item, position)
            is SettingsItem.TurbulencePreview -> (holder as TurbulencePreviewViewHolder).bind(item, position)
            is SettingsItem.LightLevel -> (holder as LightLevelViewHolder).bind(item, position)
        }
        UiMonetThemeManager.applyLiveToViewHierarchy(holder.itemView)
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

    class UpdateStatusViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: SettingsItem.UpdateStatus) {
            itemView.findViewById<TextView>(R.id.update_card_title).text = item.title
            itemView.findViewById<TextView>(R.id.update_card_details).text = item.details
            itemView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.update_card_progress).apply {
                visibility = if (item.downloading) View.VISIBLE else View.GONE
                isIndeterminate = item.progress == null
                if (item.progress != null) progress = item.progress
            }
            itemView.findViewById<TextView>(R.id.update_card_progress_text).apply {
                text = item.progressText
                visibility = if (item.progressText == null) View.GONE else View.VISIBLE
            }
            itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.update_card_action).apply {
                text = item.actionTitle
                isEnabled = item.actionEnabled
                setOnClickListener { item.onAction() }
            }
            itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.update_card_cancel).apply {
                text = item.cancelTitle
                visibility = if (item.downloading) View.VISIBLE else View.GONE
                setOnClickListener { item.onCancel() }
            }
        }
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
                    notifyNotificationPreviewChanged()
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

            val dialog = MaterialAlertDialogBuilder(context, R.style.ClockDesk_Dialog_Theme)
                .setTitle(entry.title)
                .setSingleChoiceItems(entry.entries, currentIndex) { _, which ->
                    tempSelectedIndex = which
                }
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    if (tempSelectedIndex in entry.entryValues.indices) {
                        val newValue = entry.entryValues[tempSelectedIndex]
                        prefs.edit().putString(entry.key, newValue).apply()
                        entry.onSelectionChange?.invoke(newValue)
                        notifyNotificationPreviewChanged(animatePosition = entry.key == DeskNotificationHelper.KEY_POSITION)
                        onPreferenceChanged(entry.key, newValue)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            UiMonetThemeManager.styleAlertDialog(dialog)
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

            val isLivePreviewSlider = entry.key == DeskNotificationHelper.KEY_SCALE ||
                    entry.key == DeskNotificationHelper.KEY_BG_OPACITY ||
                    entry.key == "graphics_turbulence_grid" ||
                    entry.key == "graphics_turbulence_speed"

            slider.clearOnChangeListeners()
            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    updateLabel(value)
                    if (isLivePreviewSlider) {
                        writeSliderPreference(entry.key, value, entry.step)
                        if (entry.key == DeskNotificationHelper.KEY_SCALE ||
                            entry.key == DeskNotificationHelper.KEY_BG_OPACITY
                        ) {
                            notifyNotificationPreviewChanged()
                        }
                    }
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
                    if (entry.key == DeskNotificationHelper.KEY_SCALE ||
                        entry.key == DeskNotificationHelper.KEY_BG_OPACITY
                    ) {
                        notifyNotificationPreviewChanged()
                    }
                    if (!isLivePreviewSlider) {
                        onPreferenceChanged(entry.key, value)
                    } else if (entry.key == "graphics_turbulence_grid" || entry.key == "graphics_turbulence_speed") {
                        if (activeTurbulenceView?.isPlaying == true) {
                            val primaryColor = UiMonetThemeManager.getPalette(context).primary
                            activeTurbulenceView?.playAnimation(primaryColor) {}
                        }
                    }
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
            val palette = UiMonetThemeManager.getPalette(context)

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
            UiMonetThemeManager.styleTextInputLayout(textInputLayout, palette)
            layout.addView(textInputLayout)

            val dialog = MaterialAlertDialogBuilder(context, R.style.ClockDesk_Dialog_Theme)
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
            UiMonetThemeManager.styleAlertDialog(dialog, palette)
        }
    }

    inner class BurnInPreviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val previewCanvas: BurnInPreviewCanvas = view.findViewById(R.id.burn_in_preview_canvas)
        private val valueLabel: TextView = view.findViewById(R.id.burn_in_value_label)
        private val slider: Slider = view.findViewById(R.id.burn_in_slider)
        private val btnSubtle: MaterialButton = view.findViewById(R.id.btn_preset_subtle)
        private val btnNormal: MaterialButton = view.findViewById(R.id.btn_preset_normal)
        private val btnAggressive: MaterialButton = view.findViewById(R.id.btn_preset_aggressive)

        fun bind(entry: SettingsItem.BurnInPreview, position: Int) {
            applyCardShapeAndMargin(cardView, position)

            val currentShiftDp = prefs.getInt(entry.key, 10).toFloat().coerceIn(4f, 24f)

            previewCanvas.shiftDistanceDp = currentShiftDp
            slider.value = currentShiftDp
            valueLabel.text = "${currentShiftDp.toInt()} dp"

            val isEnabled = entry.isEnabled()
            cardView.isEnabled = isEnabled
            slider.isEnabled = isEnabled
            btnSubtle.isEnabled = isEnabled
            btnNormal.isEnabled = isEnabled
            btnAggressive.isEnabled = isEnabled
            cardView.alpha = if (isEnabled) 1.0f else 0.38f

            slider.clearOnChangeListeners()
            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    valueLabel.text = "${value.toInt()} dp"
                    previewCanvas.shiftDistanceDp = value
                }
            }

            slider.clearOnSliderTouchListeners()
            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(s: Slider) {}
                override fun onStopTrackingTouch(s: Slider) {
                    s.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val value = s.value.toInt()
                    prefs.edit().putInt(entry.key, value).apply()
                    onPreferenceChanged(entry.key, value)
                }
            })

            fun applyPreset(dpVal: Float) {
                slider.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                slider.value = dpVal
                valueLabel.text = "${dpVal.toInt()} dp"
                previewCanvas.shiftDistanceDp = dpVal
                prefs.edit().putInt(entry.key, dpVal.toInt()).apply()
                onPreferenceChanged(entry.key, dpVal.toInt())
            }

            btnSubtle.setOnClickListener { applyPreset(5f) }
            btnNormal.setOnClickListener { applyPreset(10f) }
            btnAggressive.setOnClickListener { applyPreset(20f) }
        }
    }

    inner class SmartPixelsPreviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val overlayView: View = view.findViewById(R.id.smart_pixels_overlay)
        private val loupeView: SmartPixelLoupeView = view.findViewById(R.id.smart_pixels_loupe)
        private val valueLabel: TextView = view.findViewById(R.id.smart_pixels_value_label)
        private val slider: Slider = view.findViewById(R.id.smart_pixels_slider)
        private val btnLight: MaterialButton = view.findViewById(R.id.btn_pixel_preset_light)
        private val btnBalanced: MaterialButton = view.findViewById(R.id.btn_pixel_preset_balanced)
        private val btnStrong: MaterialButton = view.findViewById(R.id.btn_pixel_preset_strong)

        fun bind(entry: SettingsItem.SmartPixelsPreview, position: Int) {
            applyCardShapeAndMargin(cardView, position)

            val currentIntensity = prefs.getInt(entry.key, 50).coerceIn(20, 80)

            fun updatePreview(intensity: Int) {
                valueLabel.text = "$intensity%"
                loupeView.intensityPercent = intensity
                val shader = SmartPixelManager.createPatternShader(intensity, blockSize = 2)
                val drawable = PaintDrawable().apply { paint.shader = shader }
                overlayView.background = drawable
            }

            updatePreview(currentIntensity)
            slider.value = currentIntensity.toFloat()

            val isEnabled = entry.isEnabled()
            cardView.isEnabled = isEnabled
            slider.isEnabled = isEnabled
            btnLight.isEnabled = isEnabled
            btnBalanced.isEnabled = isEnabled
            btnStrong.isEnabled = isEnabled
            cardView.alpha = if (isEnabled) 1.0f else 0.38f

            slider.clearOnChangeListeners()
            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    updatePreview(value.toInt())
                }
            }

            slider.clearOnSliderTouchListeners()
            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(s: Slider) {}
                override fun onStopTrackingTouch(s: Slider) {
                    s.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val value = s.value.toInt()
                    prefs.edit().putInt(entry.key, value).apply()
                    onPreferenceChanged(entry.key, value)
                }
            })

            fun applyPreset(intensityVal: Int) {
                slider.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                slider.value = intensityVal.toFloat()
                updatePreview(intensityVal)
                prefs.edit().putInt(entry.key, intensityVal).apply()
                onPreferenceChanged(entry.key, intensityVal)
            }

            btnLight.setOnClickListener { applyPreset(25) }
            btnBalanced.setOnClickListener { applyPreset(50) }
            btnStrong.setOnClickListener { applyPreset(75) }
        }
    }

    inner class NotificationPreviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val previewBanner: View = view.findViewById(R.id.preview_notification_banner)
        private val btnReplay: MaterialButton = view.findViewById(R.id.btn_replay_notification_preview)

        init {
            val iconView = previewBanner.findViewById<ImageView>(R.id.notification_app_icon)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && iconView != null) {
                iconView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: Outline) {
                        val radius = v.context.resources.displayMetrics.density * 8f
                        outline.setRoundRect(0, 0, v.width, v.height, radius)
                    }
                }
                iconView.clipToOutline = true
            }
        }

        fun bind(entry: SettingsItem.NotificationPreview, position: Int) {
            applyCardShapeAndMargin(cardView, position)
            updatePreview(entry, playEntrance = false)

            btnReplay.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                playEntranceAnimation()
            }

            previewBanner.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                playEntranceAnimation()
            }
        }

        fun updatePreview(entry: SettingsItem.NotificationPreview, playEntrance: Boolean) {
            val isEnabled = entry.isEnabled()
            cardView.isEnabled = isEnabled
            btnReplay.isEnabled = isEnabled
            cardView.alpha = if (isEnabled) 1.0f else 0.38f

            val density = context.resources.displayMetrics.density
            (previewBanner.layoutParams as? FrameLayout.LayoutParams)?.let { flp ->
                flp.gravity = DeskNotificationHelper.getBannerGravity(prefs)
                val marginPx = (6f * density).toInt()
                flp.topMargin = marginPx
                flp.bottomMargin = marginPx
                flp.marginStart = marginPx
                flp.marginEnd = marginPx
                previewBanner.layoutParams = flp
            }

            DeskNotificationHelper.applyBannerCustomization(
                context = context,
                view = previewBanner,
                prefs = prefs,
                appName = context.getString(R.string.desk_notification_preview_sample_app),
                deviceName = context.getString(R.string.desk_notification_preview_sample_device),
                title = context.getString(R.string.desk_notification_preview_sample_title),
                text = context.getString(R.string.desk_notification_preview_sample_body),
                iconBytes = null,
                notificationId = "preview_sample",
                isClearable = true,
                isPreview = true
            )

            if (playEntrance) {
                playEntranceAnimation()
            }
        }

        private fun playEntranceAnimation() {
            val isBottom = DeskNotificationHelper.isBottomPosition(prefs)
            previewBanner.animate().cancel()
            previewBanner.translationY = if (isBottom) 80f else -80f
            previewBanner.alpha = 0f
            previewBanner.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(420)
                .setInterpolator(OvershootInterpolator(0.9f))
                .start()
        }
    }

    inner class NotificationAppearanceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val fontRecyclerView: RecyclerView = view.findViewById(R.id.notification_font_recycler_view)
        private val variableAxesSection: LinearLayout = view.findViewById(R.id.notification_variable_axes_section)
        private val dynamicAxesContainer: LinearLayout = view.findViewById(R.id.notification_dynamic_axes_container)
        private val btnResetAxes: MaterialButton = view.findViewById(R.id.btn_reset_notification_axes)
        private val colorRecyclerView: RecyclerView = view.findViewById(R.id.notification_color_recycler_view)
        private val colorModeGroup: MaterialButtonToggleGroup = view.findViewById(R.id.notification_color_mode_group)
        private val btnResetColors: MaterialButton = view.findViewById(R.id.btn_reset_notification_colors)

        init {
            fontRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            colorRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            preventHorizontalIntercept(fontRecyclerView)
            preventHorizontalIntercept(colorRecyclerView)
        }

        private fun preventHorizontalIntercept(rv: RecyclerView) {
            rv.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> rv.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> rv.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    return false
                }
                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })
        }

        fun bind(entry: SettingsItem.NotificationAppearance, position: Int) {
            applyCardShapeAndMargin(cardView, position)

            val isEnabled = entry.isEnabled()
            cardView.isEnabled = isEnabled
            cardView.alpha = if (isEnabled) 1.0f else 0.38f

            setupFontCarousel(entry)
            setupVariableAxesSection()
            setupColorCarousel()
        }

        private fun setupFontCarousel(entry: SettingsItem.NotificationAppearance) {
            val fonts = DeskNotificationHelper.getFontsList(context)
            val selectedIdx = DeskNotificationHelper.resolveSelectedFontIndex(context, prefs, fonts)
            val defaultIdx = DeskNotificationHelper.getDefaultFontIndex(fonts)

            val fontAdapter = FontAdapter(
                fonts = fonts,
                onFontSelected = { fontIndex ->
                    if (fontIndex in 1..fonts.lastIndex) {
                        val id = DeskNotificationHelper.getFontIdentifier(context, fonts[fontIndex])
                        prefs.edit()
                            .putString(DeskNotificationHelper.KEY_FONT_ID, id)
                            .putInt(DeskNotificationHelper.KEY_FONT_INDEX, fontIndex)
                            .apply()
                        setupVariableAxesSection()
                        notifyNotificationPreviewChanged()
                    }
                },
                onAddFontClicked = {
                    entry.onAddFontRequested?.invoke()
                },
                onFontLongClick = { fontIndex ->
                    if (fontIndex in fonts.indices) {
                        val item = fonts[fontIndex]
                        val deletedId = DeskNotificationHelper.getFontIdentifier(context, item)
                        val dialog = MaterialAlertDialogBuilder(context, R.style.ClockDesk_Dialog_Theme)
                            .setTitle(context.getString(R.string.delete_font_title))
                            .setMessage(context.getString(R.string.delete_font_msg))
                            .setPositiveButton(context.getString(R.string.delete)) { dialog, _ ->
                                if (DeskNotificationHelper.deleteCustomFont(context, item)) {
                                    val updatedFonts = DeskNotificationHelper.getFontsList(context)
                                    val savedId = prefs.getString(DeskNotificationHelper.KEY_FONT_ID, null)
                                    if (savedId == null || savedId == deletedId) {
                                        val fallbackIdx = DeskNotificationHelper.getDefaultFontIndex(updatedFonts)
                                        prefs.edit()
                                            .putString(
                                                DeskNotificationHelper.KEY_FONT_ID,
                                                DeskNotificationHelper.DEFAULT_FONT_ID
                                            )
                                            .putInt(DeskNotificationHelper.KEY_FONT_INDEX, fallbackIdx)
                                            .apply()
                                    } else {
                                        val newIdx = DeskNotificationHelper.resolveSelectedFontIndex(context, prefs, updatedFonts)
                                        prefs.edit()
                                            .putInt(DeskNotificationHelper.KEY_FONT_INDEX, newIdx)
                                            .apply()
                                    }
                                    setupFontCarousel(entry)
                                    setupVariableAxesSection()
                                    notifyNotificationPreviewChanged()
                                }
                                dialog.dismiss()
                            }
                            .setNegativeButton(context.getString(R.string.cancel), null)
                            .show()
                        UiMonetThemeManager.styleAlertDialog(dialog)
                    }
                }
            )
            fontAdapter.selectedPosition = selectedIdx
            fontRecyclerView.adapter = fontAdapter

            val lm = fontRecyclerView.layoutManager as? LinearLayoutManager
            if (selectedIdx in 1..fonts.lastIndex && selectedIdx != defaultIdx) {
                val offsetPx = (80 * context.resources.displayMetrics.density).toInt()
                lm?.scrollToPositionWithOffset(selectedIdx, offsetPx)
            } else {
                lm?.scrollToPositionWithOffset(0, 0)
            }
        }

        private fun setupVariableAxesSection() {
            dynamicAxesContainer.removeAllViews()
            val axes = DeskNotificationHelper.getFontAxesDetails(context, prefs)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && axes.isNotEmpty()) {
                variableAxesSection.visibility = View.VISIBLE

                btnResetAxes.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    DeskNotificationHelper.resetVariationAxes(prefs)
                    setupVariableAxesSection()
                    notifyNotificationPreviewChanged()
                }

                axes.forEach { axis ->
                    val currentValue = DeskNotificationHelper.getAxisValue(prefs, axis)
                    dynamicAxesContainer.addView(createAxisSliderRow(axis, currentValue))
                }
            } else {
                variableAxesSection.visibility = View.GONE
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun createAxisSliderRow(axis: FontAxis, initialValue: Float): View {
            val density = context.resources.displayMetrics.density
            val palette = UiMonetThemeManager.getPalette(context)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
            }

            val nameText = TextView(context).apply {
                text = DeskNotificationHelper.getAxisDisplayName(context, axis.tag)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(palette.onSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f)
            }

            val valueText = TextView(context).apply {
                text = formatAxisValue(initialValue)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(palette.onSurfaceVariant)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
            }

            val slider = Slider(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.2f)
                val min = axis.minValue
                val max = axis.maxValue
                if (max > min) {
                    valueFrom = min
                    valueTo = max
                    value = initialValue.coerceIn(min, max)
                } else {
                    valueFrom = 0f
                    valueTo = 1f
                    value = 0f
                    isEnabled = false
                }

                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }

                addOnChangeListener { _, sliderValue, fromUser ->
                    if (fromUser) {
                        valueText.text = formatAxisValue(sliderValue)
                        DeskNotificationHelper.setVariationAxis(prefs, axis.tag, sliderValue)
                        notifyNotificationPreviewChanged()
                    }
                }
            }
            UiMonetThemeManager.styleSlider(slider, palette)

            row.addView(nameText)
            row.addView(slider)
            row.addView(valueText)
            return row
        }

        private fun formatAxisValue(v: Float): String {
            return if (kotlin.math.abs(v - kotlin.math.round(v)) < 0.05f) {
                kotlin.math.round(v).toInt().toString()
            } else {
                String.format("%.1f", v)
            }
        }

        private fun setupColorCarousel() {
            colorModeGroup.clearOnButtonCheckedListeners()
            colorModeGroup.check(if (isEditingNotificationBg) R.id.btn_color_mode_bg else R.id.btn_color_mode_text)
            colorModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    isEditingNotificationBg = (checkedId == R.id.btn_color_mode_bg)
                    UiMonetThemeManager.styleButtonToggleGroup(colorModeGroup, UiMonetThemeManager.getPalette(context))
                    refreshColorAdapter()
                }
            }

            btnResetColors.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                prefs.edit()
                    .putBoolean(DeskNotificationHelper.KEY_TEXT_USE_DYNAMIC, true)
                    .putString(DeskNotificationHelper.KEY_TEXT_DYNAMIC_ROLE, "default")
                    .putBoolean(DeskNotificationHelper.KEY_BG_USE_DYNAMIC, true)
                    .putString(DeskNotificationHelper.KEY_BG_DYNAMIC_ROLE, "default")
                    .apply()
                refreshColorAdapter()
                notifyNotificationPreviewChanged()
            }

            refreshColorAdapter()
        }

        private fun getCurrentColorSelection(): Triple<Int, Boolean, String?> {
            return if (isEditingNotificationBg) {
                Triple(
                    prefs.getInt(DeskNotificationHelper.KEY_BG_COLOR, Color.parseColor("#2B2930")),
                    prefs.getBoolean(DeskNotificationHelper.KEY_BG_USE_DYNAMIC, true),
                    prefs.getString(DeskNotificationHelper.KEY_BG_DYNAMIC_ROLE, "default")
                )
            } else {
                Triple(
                    prefs.getInt(DeskNotificationHelper.KEY_TEXT_COLOR, Color.WHITE),
                    prefs.getBoolean(DeskNotificationHelper.KEY_TEXT_USE_DYNAMIC, true),
                    prefs.getString(DeskNotificationHelper.KEY_TEXT_DYNAMIC_ROLE, "default")
                )
            }
        }

        private fun refreshColorAdapter() {
            val (currentColor, useDynamic, currentRole) = getCurrentColorSelection()
            val items = DeskNotificationHelper.getColorsList(context, prefs)

            val existing = colorRecyclerView.adapter as? ColorAdapter
            if (existing != null) {
                existing.updateData(items, currentColor, useDynamic, currentRole)
                return
            }

            colorRecyclerView.adapter = ColorAdapter(
                items = items,
                selectedColor = currentColor,
                useDynamic = useDynamic,
                selectedRole = currentRole,
                onColorSelected = { item ->
                    when (item) {
                        is ColorItem.AddNew -> showColorPickerDialog()
                        is ColorItem.Dynamic -> {
                            if (isEditingNotificationBg) {
                                prefs.edit()
                                    .putBoolean(DeskNotificationHelper.KEY_BG_USE_DYNAMIC, true)
                                    .putString(DeskNotificationHelper.KEY_BG_DYNAMIC_ROLE, item.roleKey)
                                    .apply()
                            } else {
                                prefs.edit()
                                    .putBoolean(DeskNotificationHelper.KEY_TEXT_USE_DYNAMIC, true)
                                    .putString(DeskNotificationHelper.KEY_TEXT_DYNAMIC_ROLE, item.roleKey)
                                    .apply()
                            }
                            refreshColorAdapter()
                            notifyNotificationPreviewChanged()
                        }
                        is ColorItem.Solid -> {
                            if (isEditingNotificationBg) {
                                prefs.edit()
                                    .putBoolean(DeskNotificationHelper.KEY_BG_USE_DYNAMIC, false)
                                    .putInt(DeskNotificationHelper.KEY_BG_COLOR, item.color)
                                    .apply()
                            } else {
                                prefs.edit()
                                    .putBoolean(DeskNotificationHelper.KEY_TEXT_USE_DYNAMIC, false)
                                    .putInt(DeskNotificationHelper.KEY_TEXT_COLOR, item.color)
                                    .apply()
                            }
                            refreshColorAdapter()
                            notifyNotificationPreviewChanged()
                        }
                    }
                },
                onColorLongClick = { item ->
                    val dialog = MaterialAlertDialogBuilder(context, R.style.ClockDesk_Dialog_Theme)
                        .setTitle(context.getString(R.string.delete_color_title))
                        .setMessage(context.getString(R.string.delete_color_msg))
                        .setPositiveButton(context.getString(R.string.delete)) { dialog, _ ->
                            if (DeskNotificationHelper.deleteCustomColor(prefs, item.color)) {
                                refreshColorAdapter()
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton(context.getString(R.string.cancel), null)
                        .show()
                    UiMonetThemeManager.styleAlertDialog(dialog)
                }
            )
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun showColorPickerDialog() {
            val (currentColor, useDynamic, currentRole) = getCurrentColorSelection()
            val initialColor = if (useDynamic) {
                DeskNotificationHelper.resolveDynamicRoleColor(
                    context,
                    prefs,
                    currentRole,
                    isBackground = isEditingNotificationBg
                )
            } else {
                currentColor
            }

            val palette = UiMonetThemeManager.getPalette(context)
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
            val colorPickerView = dialogView.findViewById<com.skydoves.colorpickerview.ColorPickerView>(R.id.color_picker_view)
            val brightnessSlideBar = dialogView.findViewById<com.skydoves.colorpickerview.sliders.BrightnessSlideBar>(R.id.brightness_slide_bar)
            val previewBox = dialogView.findViewById<View>(R.id.color_preview_box)
            val hexText = dialogView.findViewById<TextView>(R.id.hex_text)
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

            val dialog = MaterialAlertDialogBuilder(context, R.style.ClockDesk_Dialog_Theme)
                .setTitle(context.getString(R.string.custom_color))
                .setView(dialogView)
                .setPositiveButton(context.getString(R.string.apply)) { dialog, _ ->
                    DeskNotificationHelper.addCustomColor(prefs, selectedColor)
                    if (isEditingNotificationBg) {
                        prefs.edit()
                            .putBoolean(DeskNotificationHelper.KEY_BG_USE_DYNAMIC, false)
                            .putInt(DeskNotificationHelper.KEY_BG_COLOR, selectedColor)
                            .apply()
                    } else {
                        prefs.edit()
                            .putBoolean(DeskNotificationHelper.KEY_TEXT_USE_DYNAMIC, false)
                            .putInt(DeskNotificationHelper.KEY_TEXT_COLOR, selectedColor)
                            .apply()
                    }
                    refreshColorAdapter()
                    notifyNotificationPreviewChanged()
                    dialog.dismiss()
                }
                .setNegativeButton(context.getString(R.string.cancel), null)
                .show()
            UiMonetThemeManager.styleAlertDialog(dialog, palette)
        }
    }

    inner class BatteryStatusViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val iconView: ImageView = view.findViewById(R.id.battery_status_icon)
        private val titleView: TextView = view.findViewById(R.id.battery_status_title)
        private val subtitleView: TextView = view.findViewById(R.id.battery_status_subtitle)
        private val reasonView: TextView = view.findViewById(R.id.battery_status_reason)
        private val manualSwitch: MaterialSwitch = view.findViewById(R.id.battery_manual_switch)

        fun bind(entry: SettingsItem.BatteryStatus, position: Int) {
            applyCardShapeAndMargin(cardView, position)

            val batteryFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, batteryFilter)
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (scale > 0) (level.toFloat() / scale.toFloat() * 100).toInt() else 100

            val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
            val isFull = status == android.os.BatteryManager.BATTERY_STATUS_FULL

            val isManual = prefs.getBoolean("power_saver_manual", false)
            val isAutoEnabled = prefs.getBoolean("automatic_battery_saver_mode", false)
            val threshold = prefs.getInt("battery_saver_trigger", 15)
            val isSyncSystem = prefs.getBoolean("power_saver_sync_system", true)

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val isSystemPowerSave = isSyncSystem && powerManager?.isPowerSaveMode == true
            val isAutoActive = isAutoEnabled && batteryPct <= threshold && !isCharging

            val isPowerSaverActive = isManual || isAutoActive || isSystemPowerSave

            if (isPowerSaverActive) {
                titleView.text = context.getString(R.string.battery_status_active)
                iconView.setImageResource(R.drawable.ic_battery_saver)
                val primaryColor = UiMonetThemeManager.getPalette(context).primary
                iconView.setColorFilter(primaryColor)
            } else {
                titleView.text = context.getString(R.string.battery_status_inactive)
                val iconRes = when {
                    isCharging -> R.drawable.ic_battery_charging
                    isFull -> R.drawable.ic_battery_full
                    batteryPct <= threshold -> R.drawable.ic_battery_alert
                    else -> R.drawable.ic_battery_full
                }
                iconView.setImageResource(iconRes)
                iconView.clearColorFilter()
            }

            subtitleView.text = when {
                isFull -> context.getString(R.string.battery_status_full)
                isCharging -> context.getString(R.string.battery_status_charging, batteryPct)
                else -> context.getString(R.string.battery_status_discharging, batteryPct)
            }

            val reasonText = when {
                isManual -> context.getString(R.string.battery_status_reason_manual)
                isSystemPowerSave -> context.getString(R.string.battery_status_reason_system)
                isAutoActive -> context.getString(R.string.battery_status_reason_auto, threshold)
                else -> null
            }

            if (!reasonText.isNullOrBlank()) {
                reasonView.text = reasonText
                reasonView.visibility = View.VISIBLE
            } else {
                reasonView.visibility = View.GONE
            }

            manualSwitch.setOnCheckedChangeListener(null)
            manualSwitch.isChecked = isManual
            manualSwitch.setOnCheckedChangeListener { _, isChecked ->
                manualSwitch.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                prefs.edit().putBoolean("power_saver_manual", isChecked).apply()
                entry.onToggleManual?.invoke(isChecked)
                onPreferenceChanged("power_saver_manual", isChecked)
            }

            cardView.setOnClickListener {
                manualSwitch.toggle()
            }
        }
    }

    inner class TurbulencePreviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val btnTest: MaterialButton = view.findViewById(R.id.btn_test_turbulence)
        val turbulenceView: com.nxd1frnt.clockdesk2.ui.view.TurbulenceView = view.findViewById(R.id.turbulence_preview_view)

        fun bind(entry: SettingsItem.TurbulencePreview, position: Int) {
            applyCardShapeAndMargin(cardView, position)
            activeTurbulenceView = turbulenceView

            val isEnabled = entry.isEnabled()
            cardView.isEnabled = isEnabled
            btnTest.isEnabled = isEnabled
            cardView.alpha = if (isEnabled) 1.0f else 0.38f

            btnTest.setOnClickListener {
                btnTest.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val primaryColor = UiMonetThemeManager.getPalette(context).primary
                turbulenceView.playAnimation(primaryColor) {}
            }
        }
    }

    inner class LightLevelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardView = view as MaterialCardView
        private val titleView: TextView = view.findViewById(R.id.item_title)
        private val summaryView: TextView = view.findViewById(R.id.item_summary)
        private val iconView: ImageView = view.findViewById(R.id.item_icon)
        private val statusView: TextView = view.findViewById(R.id.item_status_text)
        private val chevronView: ImageView = view.findViewById(R.id.item_chevron)

        fun bind(entry: SettingsItem.LightLevel, position: Int) {
            applyCardShapeAndMargin(cardView, position)

            titleView.text = entry.title
            if (entry.iconRes != null) {
                iconView.setImageResource(entry.iconRes)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            chevronView.visibility = View.GONE
            cardView.isClickable = false
            cardView.isFocusable = false

            val isEnabled = entry.isEnabled()
            cardView.isEnabled = isEnabled
            cardView.alpha = if (isEnabled) 1.0f else 0.38f

            updateValues(entry.hasSensor, entry.luxValue, entry.summary)
        }

        fun updateValues(hasSensor: Boolean, lux: Float?, defaultSummary: String?) {
            if (!hasSensor) {
                summaryView.text = context.getString(R.string.light_sensor_unavailable)
                summaryView.visibility = View.VISIBLE
                statusView.text = "—"
                statusView.visibility = View.VISIBLE
            } else if (lux != null) {
                if (!defaultSummary.isNullOrBlank()) {
                    summaryView.text = defaultSummary
                    summaryView.visibility = View.VISIBLE
                } else {
                    summaryView.visibility = View.GONE
                }
                statusView.text = if (lux < 10f) {
                    String.format(java.util.Locale.US, "%.1f lx", lux)
                } else {
                    "${lux.toInt()} lx"
                }
                statusView.visibility = View.VISIBLE
            } else {
                summaryView.text = context.getString(R.string.light_sensor_measuring)
                summaryView.visibility = View.VISIBLE
                statusView.text = "… lx"
                statusView.visibility = View.VISIBLE
            }
        }
    }
}
