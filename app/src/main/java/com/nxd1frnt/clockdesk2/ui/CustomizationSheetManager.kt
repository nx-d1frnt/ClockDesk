package com.nxd1frnt.clockdesk2.ui

import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import com.nxd1frnt.clockdesk2.ui.adapters.ColorAdapter
import com.nxd1frnt.clockdesk2.ui.adapters.FontAdapter
import com.nxd1frnt.clockdesk2.utils.ClockManager
import com.nxd1frnt.clockdesk2.utils.ColorItem
import com.nxd1frnt.clockdesk2.utils.FontAxis
import com.nxd1frnt.clockdesk2.utils.FontManager

class CustomizationSheetManager(
    private val bottomSheetView: LinearLayout,
    private val mainLayout: View,
    private val backgroundCustomizationTab: View,
    private val fontManager: FontManager,
    private val widgetMover: WidgetMover,
    private val clockManager: ClockManager,
    private val dayTimeGetter: DayTimeGetter,
    private val onAddFontRequested: () -> Unit,
    private val onSheetStateChanged: (isHidden: Boolean) -> Unit
) {

    private val behavior: BottomSheetBehavior<LinearLayout> = BottomSheetBehavior.from(bottomSheetView)

    private var focusedView: View? = null
    private var isEditingBackground = false
    private val animationDuration = 300L

    private val bsTitle by lazy { bottomSheetView.findViewById<TextView>(R.id.customization_title) }
    private val bsSizeSeekBar by lazy { bottomSheetView.findViewById<SeekBar>(R.id.size_seekbar) }
    private val bsSizeValue by lazy { bottomSheetView.findViewById<TextView>(R.id.size_value) }

    private val bsMaxWidthContainer by lazy { bottomSheetView.findViewById<LinearLayout>(R.id.max_width_container) }
    private val bsMaxWidthSeekBar by lazy { bottomSheetView.findViewById<SeekBar>(R.id.max_width_seekbar) }
    private val bsMaxWidthValue by lazy { bottomSheetView.findViewById<TextView>(R.id.max_width_value) }

    private val bsTransparencySeekBar by lazy { bottomSheetView.findViewById<SeekBar>(R.id.transparency_seekbar) }
    private val bsTransparencyPreview by lazy { bottomSheetView.findViewById<View>(R.id.transparency_preview) }

    private val bsFontRecyclerView by lazy { bottomSheetView.findViewById<RecyclerView>(R.id.font_recycler_view) }
    private val bsColorRecyclerView by lazy { bottomSheetView.findViewById<RecyclerView>(R.id.color_recycler_view) }

    private val bsApplyButton by lazy { bottomSheetView.findViewById<Button>(R.id.apply_button) }
    private val bsCancelButton by lazy { bottomSheetView.findViewById<Button>(R.id.cancel_button) }

    private val bsNightShiftSwitch by lazy { bottomSheetView.findViewById<MaterialSwitch>(R.id.night_shift_switch) }
    private val bsEditBackgroundSwitch by lazy { bottomSheetView.findViewById<MaterialSwitch>(R.id.edit_background_switch) }
    private val bsFreeModeSwitch by lazy { bottomSheetView.findViewById<MaterialSwitch>(R.id.free_mode_switch) }
    private val bsGridSnapSwitch by lazy { bottomSheetView.findViewById<MaterialSwitch>(R.id.grid_snap_switch) }
    private val bsIgnoreCollisionSwitch by lazy { bottomSheetView.findViewById<MaterialSwitch>(R.id.ignore_collision_switch) }

    private val bsTimeFormatGroup by lazy { bottomSheetView.findViewById<RadioGroup>(R.id.time_format_radio_group) }
    private val bsShowAMPMSwitch by lazy { bottomSheetView.findViewById<MaterialSwitch>(R.id.show_am_pm_switch) }
    private val bsDateFormatGroup by lazy { bottomSheetView.findViewById<RadioGroup>(R.id.date_format_radio_group) }

    private val bsTimeFormatLabel by lazy { bottomSheetView.findViewById<TextView>(R.id.time_format_label) }
    private val bsDateFormatLabel by lazy { bottomSheetView.findViewById<TextView>(R.id.date_format_label) }
    private val bsTextGravityTitle by lazy { bottomSheetView.findViewById<TextView>(R.id.gravity_label) }
    private val bsAlignmentLabel by lazy { bottomSheetView.findViewById<TextView>(R.id.alignment_label) }
    private val bsVerticalAlignmentLabel by lazy { bottomSheetView.findViewById<TextView>(R.id.vertical_alignment_label) }
    private val bsWidgetOrderLabel by lazy { bottomSheetView.findViewById<TextView>(R.id.widget_order_label) }

    private val bsTextGravityGroup by lazy { bottomSheetView.findViewById<MaterialButtonToggleGroup>(R.id.text_gravity_toggle_group) }
    private val bsHorizontalAlignGroup by lazy { bottomSheetView.findViewById<MaterialButtonToggleGroup>(R.id.alignment_toggle_group) }
    private val bsVerticalAlignGroup by lazy { bottomSheetView.findViewById<MaterialButtonToggleGroup>(R.id.vertical_alignment_group) }

    private val bsMoveUpBtn by lazy { bottomSheetView.findViewById<Button>(R.id.move_up_button) }
    private val bsMoveDownBtn by lazy { bottomSheetView.findViewById<Button>(R.id.move_down_button) }

    private val bsVarTitle by lazy { bottomSheetView.findViewById<TextView>(R.id.variable_properties_title) }

    private val dynamicAxesContainer: LinearLayout by lazy {
        val container = LinearLayout(bottomSheetView.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val parent = bsVarTitle.parent as LinearLayout
        val index = parent.indexOfChild(bsVarTitle)
        parent.addView(container, index + 1)

        try {
            bottomSheetView.findViewById<View>(R.id.var_weight_container)?.visibility = View.GONE
            bottomSheetView.findViewById<View>(R.id.var_width_container)?.visibility = View.GONE
            bottomSheetView.findViewById<View>(R.id.var_roundness_container)?.visibility = View.GONE
        } catch (e: Exception) {}

        container
    }

    init {
        setupBehavior()
        initControls()
    }

    private fun setupBehavior() {
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        behavior.peekHeight = 0
        behavior.isHideable = true
        behavior.isDraggable = true

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    onSheetStateChanged(true)
                    restoreMainLayoutState()
                    highlightFocusedView(false)
                    focusedView = null
                } else {
                    onSheetStateChanged(false)
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    private fun initControls() {
        bsFontRecyclerView.layoutManager = LinearLayoutManager(bottomSheetView.context, LinearLayoutManager.HORIZONTAL, false)
        bsFontRecyclerView.isNestedScrollingEnabled = false
        bsColorRecyclerView.layoutManager = LinearLayoutManager(bottomSheetView.context, LinearLayoutManager.HORIZONTAL, false)

        setupSizeAndTransparency()
        setupSwitchesAndToggles()
        setupButtons()
        setupFontAdapter()
    }

    fun showForView(viewToCustomize: View) {
        focusedView = viewToCustomize
        isEditingBackground = false

        scaleDownMainLayout()
        highlightFocusedView(true)
        hideBackgroundTab()

        configureVisibilityForView(viewToCustomize)
        loadSettingsForView(viewToCustomize)

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun hide() {
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    val isShowing: Boolean
        get() = behavior.state != BottomSheetBehavior.STATE_HIDDEN && behavior.state != BottomSheetBehavior.STATE_COLLAPSED

    fun onFontAdded(newIndex: Int) {
        if (newIndex > 0) {
            bsFontRecyclerView.adapter?.notifyDataSetChanged()
            (bsFontRecyclerView.adapter as? FontAdapter)?.selectedPosition = newIndex

            focusedView?.let { view ->
                fontManager.setFontIndex(view, newIndex)
                updateVariationVisibility()
            }
        }
    }

    private fun updateVariationVisibility() {
        if (focusedView == null) return

        dynamicAxesContainer.removeAllViews()

        val axes = try { fontManager.getFontAxesDetails(focusedView!!) } catch (e: Exception) { emptyList<FontAxis>() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && axes.isNotEmpty()) {
            bsVarTitle.visibility = View.VISIBLE
            dynamicAxesContainer.visibility = View.VISIBLE

            axes.forEach { axis ->
                // Получаем текущее значение или используем default
                val currentValue = try { fontManager.getCurrentAxisValue(focusedView!!, axis.tag) } catch (e: Exception) { axis.defaultValue }
                val sliderView = createDynamicSlider(axis, currentValue ?: axis.defaultValue)
                dynamicAxesContainer.addView(sliderView)
            }
        } else {
            bsVarTitle.visibility = View.GONE
            dynamicAxesContainer.visibility = View.GONE
        }
    }

    private fun createDynamicSlider(axis: FontAxis, initialValue: Float): View {
        val context = bottomSheetView.context
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 16, 0, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        val nameText = TextView(context).apply {
            text = getAxisDisplayName(axis.tag)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        }

        val valueText = TextView(context).apply {
            text = String.format("%.1f", initialValue)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
        }

        val seekBar = SeekBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            max = 1000

            val range = axis.maxValue - axis.minValue
            progress = if (range > 0) (((initialValue - axis.minValue) / range) * 1000).toInt() else 0

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && focusedView != null) {
                        val realValue = axis.minValue + (progress / 1000f) * range
                        valueText.text = String.format("%.1f", realValue)
                        // ВАЖНО: Добавьте этот метод в FontManager для передачи любого тега
                        fontManager.setVariationAxis(focusedView!!, axis.tag, realValue)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        container.addView(nameText)
        container.addView(seekBar)
        container.addView(valueText)
        return container
    }

    private fun getAxisDisplayName(tag: String): String {
        return when (tag) {
            "wght" -> "Weight"
            "wdth" -> "Width"
            "slnt" -> "Slant"
            "ital" -> "Italic"
            "opsz" -> "Optical Size"
            "ROND" -> "Roundness"
            "GRAD" -> "Grade"
            "CASL" -> "Casual"
            "MONO" -> "Monospace"
            else -> tag
        }
    }

    private fun scaleDownMainLayout() {
        val metrics = bottomSheetView.resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val bottomSheetPx = 380f * metrics.density
        val targetScale = 0.60f
        val translationX = (screenW * (1f - targetScale) / 2f) - bottomSheetPx

        mainLayout.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .translationX(translationX)
            .setDuration(animationDuration)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun restoreMainLayoutState() {
        mainLayout.animate()
            .scaleX(0.90f)
            .scaleY(0.90f)
            .translationX(0f)
            .setDuration(animationDuration)
            .setInterpolator(OvershootInterpolator())
            .start()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            backgroundCustomizationTab.visibility = View.VISIBLE
            backgroundCustomizationTab.animate().alpha(1f).setDuration(200).start()
        } else {
            backgroundCustomizationTab.visibility = View.VISIBLE
        }
    }

    private fun hideBackgroundTab() {
        backgroundCustomizationTab.animate().alpha(0f).setDuration(200).withEndAction {
            backgroundCustomizationTab.visibility = View.GONE
        }.start()
    }

    private fun highlightFocusedView(isHighlighted: Boolean) {
        focusedView?.let { view ->
            val scale = if (isHighlighted) 1.05f else 1.0f
            view.animate().scaleX(scale).scaleY(scale).setDuration(animationDuration).start()
            if (isHighlighted) view.setBackgroundResource(R.drawable.editable_border)
        }
    }

    private fun configureVisibilityForView(view: View) {
        val isTime = view.id == R.id.time_text
        val isDate = view.id == R.id.date_text
        val isLastFm = view.id == R.id.lastfm_layout
        val isSmartChip = view.id == R.id.smart_chip_container

        bsTitle.text = bottomSheetView.context.getString(
            when {
                isTime -> R.string.customize_time
                isDate -> R.string.customize_date
                isLastFm -> R.string.customize_now_playing
                isSmartChip -> R.string.customize_smart_chips
                else -> R.string.app_name
            }
        )

        bsTimeFormatGroup.visibility = if (isTime) View.VISIBLE else View.GONE
        bsTimeFormatLabel.visibility = if (isTime) View.VISIBLE else View.GONE
        bsShowAMPMSwitch.visibility = if (isTime) View.VISIBLE else View.GONE
        if (isTime) {
            bsShowAMPMSwitch.isEnabled = !bsTimeFormatGroup.checkedRadioButtonId.equals(R.id.time_24_radio)
            bsTimeFormatGroup.check(if (fontManager.getTimeFormatPattern().contains("H")) R.id.time_24_radio else R.id.time_12_radio)
        }

        bsDateFormatGroup.visibility = if (isDate) View.VISIBLE else View.GONE
        bsDateFormatLabel.visibility = if (isDate) View.VISIBLE else View.GONE
        if (isDate) {
            val pattern = fontManager.getDateFormatPattern()
            bsDateFormatGroup.check(when (pattern) {
                "MMM dd" -> R.id.date_format_1
                "EEEE, MMMM dd, yyyy" -> R.id.date_format_3
                else -> R.id.date_format_2
            })
        }

        val showLayoutControls = isTime || isDate || isLastFm
        bsTextGravityGroup.visibility = if (showLayoutControls) View.VISIBLE else View.GONE
        bsHorizontalAlignGroup.visibility = if (showLayoutControls) View.VISIBLE else View.GONE
        bsVerticalAlignGroup.visibility = if (showLayoutControls) View.VISIBLE else View.GONE
        bsMoveUpBtn.visibility = if (showLayoutControls) View.VISIBLE else View.GONE
        bsMoveDownBtn.visibility = if (showLayoutControls) View.VISIBLE else View.GONE
        bsFreeModeSwitch.visibility = if (showLayoutControls) View.VISIBLE else View.GONE
        bsGridSnapSwitch.visibility = if (showLayoutControls) View.VISIBLE else View.GONE
        bsIgnoreCollisionSwitch.visibility = if (showLayoutControls) View.VISIBLE else View.GONE
        bsAlignmentLabel.visibility = if (showLayoutControls) View.VISIBLE else View.GONE
        bsVerticalAlignmentLabel.visibility = if (showLayoutControls) View.VISIBLE else View.GONE
        bsWidgetOrderLabel.visibility = if (showLayoutControls) View.VISIBLE else View.GONE
        bsTextGravityTitle.visibility = if (showLayoutControls) View.VISIBLE else View.GONE

        bsMaxWidthContainer.visibility = if (isLastFm) View.VISIBLE else View.GONE
        bsEditBackgroundSwitch.visibility = if (isSmartChip) View.VISIBLE else View.GONE
    }

    private fun loadSettingsForView(view: View) {
        val settings = fontManager.getSettings(view) ?: return
        val metrics = bottomSheetView.resources.displayMetrics

        val sizeOffset = 8
        val maxvalue = (metrics.widthPixels / metrics.density * 0.3).toInt()
        bsSizeSeekBar.max = maxvalue - sizeOffset
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) bsSizeSeekBar.min = 0
        bsSizeSeekBar.progress = (settings.size - sizeOffset).toInt()
        bsSizeValue.text = bottomSheetView.context.getString(R.string.size_value_format, settings.size.toInt())

        bsMaxWidthSeekBar.progress = settings.maxWidthPercent
        bsMaxWidthValue.text = "${settings.maxWidthPercent}%"
        bsTransparencySeekBar.max = 100
        bsTransparencySeekBar.progress = (settings.alpha * 100).toInt()
        bsTransparencyPreview.alpha = settings.alpha

        (bsFontRecyclerView.adapter as? FontAdapter)?.apply {
            selectedPosition = settings.fontIndex
            notifyDataSetChanged()
            if (settings.fontIndex != -1 && settings.fontIndex < itemCount) {
                bsFontRecyclerView.scrollToPosition(settings.fontIndex)
            }
        }

        updateVariationVisibility()

        bsColorRecyclerView.adapter = createColorAdapter(view)

        bsNightShiftSwitch.setOnCheckedChangeListener(null)
        bsNightShiftSwitch.isChecked = fontManager.isNightShiftEnabledForView(view)
        bsNightShiftSwitch.setOnCheckedChangeListener { _, isChecked ->
            fontManager.setNightShiftEnabledForView(view, isChecked)
            fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, true)
        }

        bsFreeModeSwitch.setOnCheckedChangeListener(null)
        bsFreeModeSwitch.isChecked = widgetMover.isFreeMovementEnabled(view)
        bsFreeModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            widgetMover.setFreeMovementEnabled(view, isChecked)
        }

        bsGridSnapSwitch.isChecked = widgetMover.isGridSnapEnabled()
        bsIgnoreCollisionSwitch.isChecked = widgetMover.isCollisionCheckEnabled()

        val savedGravity = widgetMover.getInternalGravity(view)
        bsTextGravityGroup.check(when (savedGravity) {
            widgetMover.GRAVITY_START -> R.id.gravity_left_button
            widgetMover.GRAVITY_END -> R.id.gravity_right_button
            else -> R.id.gravity_center_button
        })

        val savedValign = widgetMover.getAlignmentOnlyV(view)
        bsVerticalAlignGroup.check(when (savedValign) {
            widgetMover.ALIGN_V_TOP -> R.id.align_top_button
            widgetMover.ALIGN_V_CENTER -> R.id.align_center_vertical_button
            widgetMover.ALIGN_V_BOTTOM -> R.id.align_bottom_button
            else -> View.NO_ID
        })

        val savedHalign = widgetMover.getAlignmentOnlyH(view)
        bsHorizontalAlignGroup.check(when (savedHalign) {
            widgetMover.ALIGN_H_LEFT -> R.id.left_button
            widgetMover.ALIGN_H_CENTER -> R.id.center_button
            widgetMover.ALIGN_H_RIGHT -> R.id.right_button
            else -> View.NO_ID
        })
    }

    private fun setupSizeAndTransparency() {
        bsSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || focusedView == null) return
                val size = (progress + 8f)
                bsSizeValue.text = bottomSheetView.context.getString(R.string.size_value_format, size.toInt())
                fontManager.setFontSize(focusedView!!, size)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bsMaxWidthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || focusedView == null) return
                bsMaxWidthValue.text = "$progress%"
                if (focusedView!!.id == R.id.lastfm_layout) {
                    fontManager.setMaxWidthPercent(focusedView!!, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bsTransparencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || focusedView == null) return
                val alpha = progress / 100f
                bsTransparencyPreview.alpha = alpha
                fontManager.setFontAlpha(focusedView!!, alpha)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSwitchesAndToggles() {
        bsEditBackgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            isEditingBackground = isChecked
            focusedView?.let { bsColorRecyclerView.adapter = createColorAdapter(it) }
        }

        bsGridSnapSwitch.setOnCheckedChangeListener { _, isChecked -> widgetMover.setGridSnapEnabled(isChecked) }
        bsIgnoreCollisionSwitch.setOnCheckedChangeListener { _, isChecked -> widgetMover.setCollisionCheckEnabled(isChecked) }

        bsTextGravityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || focusedView == null) return@addOnButtonCheckedListener
            widgetMover.setTextGravity(focusedView!!, when (checkedId) {
                R.id.gravity_left_button -> widgetMover.GRAVITY_START
                R.id.gravity_right_button -> widgetMover.GRAVITY_END
                else -> widgetMover.GRAVITY_CENTER
            })
        }

        bsHorizontalAlignGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || focusedView == null) return@addOnButtonCheckedListener
            widgetMover.alignViewHorizontal(focusedView!!, when (checkedId) {
                R.id.left_button -> 0
                R.id.right_button -> 2
                else -> 1
            })
        }

        bsVerticalAlignGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || focusedView == null) return@addOnButtonCheckedListener
            widgetMover.alignViewVertical(focusedView!!, when (checkedId) {
                R.id.align_top_button -> 0
                R.id.align_bottom_button -> 2
                else -> 1
            })
        }

        bsTimeFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            if (focusedView?.id == R.id.time_text) {
                val pattern = if (checkedId == R.id.time_24_radio) "HH:mm" else if (bsShowAMPMSwitch.isChecked) "hh:mm a" else "hh:mm"
                bsShowAMPMSwitch.isEnabled = (checkedId != R.id.time_24_radio)
                fontManager.setTimeFormatPattern(pattern)
                clockManager.updateTimeText()
            }
        }

        bsShowAMPMSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (focusedView?.id == R.id.time_text) {
                fontManager.setTimeFormatPattern(if (isChecked) "hh:mm a" else "hh:mm")
                clockManager.updateTimeText()
            }
        }

        bsDateFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            if (focusedView?.id == R.id.date_text) {
                fontManager.setDateFormatPattern(when (checkedId) {
                    R.id.date_format_1 -> "MMM dd"
                    R.id.date_format_3 -> "EEEE, MMMM dd, yyyy"
                    else -> "EEE, MMM dd"
                })
                clockManager.updateDateText()
            }
        }
    }

    private fun setupButtons() {
        bsMoveUpBtn.setOnClickListener { focusedView?.let { widgetMover.moveWidgetOrder(it, true) } }
        bsMoveDownBtn.setOnClickListener { focusedView?.let { widgetMover.moveWidgetOrder(it, false) } }

        bsApplyButton.setOnClickListener {
            fontManager.saveSettings()
            hide()
        }

        bsCancelButton.setOnClickListener {
            fontManager.loadFont()
            widgetMover.restoreOrderAndPositions()
            hide()
        }
    }

    private fun setupFontAdapter() {
        val adapter = FontAdapter(
            fonts = fontManager.getFonts(),
            onFontSelected = { fontIndex ->
                focusedView?.let {
                    fontManager.setFontIndex(it, fontIndex)
                    updateVariationVisibility()
                }
            },
            onAddFontClicked = { onAddFontRequested() },
            onFontLongClick = { fontIndex ->
                val context = bottomSheetView.context
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.delete_font_title))
                    .setMessage(context.getString(R.string.delete_font_msg))
                    .setPositiveButton(context.getString(R.string.delete)) { dialog, _ ->
                        if (fontManager.deleteCustomFont(fontIndex)) {
                            bsFontRecyclerView.adapter?.notifyDataSetChanged()
                            focusedView?.let { view ->
                                (bsFontRecyclerView.adapter as? FontAdapter)?.selectedPosition = fontManager.getSettings(view)?.fontIndex ?: 1
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        )
        bsFontRecyclerView.adapter = adapter
    }

    private fun createColorAdapter(view: View): ColorAdapter {
        val settings = fontManager.getSettings(view) ?: return ColorAdapter(emptyList(), 0, false, null) {}

        val currentColor = if (isEditingBackground) settings.backgroundColor else settings.color
        val useDynamic = if (isEditingBackground) settings.useDynamicBackgroundColor else settings.useDynamicColor
        val currentRole = if (isEditingBackground) settings.dynamicBackgroundColorRole else settings.dynamicColorRole

        return ColorAdapter(
            items = fontManager.getColorsList(),
            selectedColor = currentColor,
            useDynamic = useDynamic,
            selectedRole = currentRole,
            onColorSelected = { item ->
                if (isEditingBackground) {
                    when (item) {
                        is ColorItem.Dynamic -> fontManager.setSmartChipDynamicBackgroundColor(view, item.roleKey)
                        is ColorItem.Solid -> fontManager.setSmartChipBackgroundColor(view, item.color)
                        else -> {}
                    }
                } else {
                    when (item) {
                        is ColorItem.Dynamic -> fontManager.setDynamicColorForWidget(view, item.roleKey)
                        is ColorItem.Solid -> fontManager.setFontColor(view, item.color)
                        else -> {}
                    }
                }

                fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, true)

                val updSettings = fontManager.getSettings(view)
                if (updSettings != null) {
                    val nextColor = if (isEditingBackground) updSettings.backgroundColor else updSettings.color
                    val nextDynamic = if (isEditingBackground) updSettings.useDynamicBackgroundColor else updSettings.useDynamicColor
                    val nextRole = if (isEditingBackground) updSettings.dynamicBackgroundColorRole else updSettings.dynamicColorRole
                    (bsColorRecyclerView.adapter as? ColorAdapter)?.updateSelection(nextColor, nextDynamic, nextRole)
                }
            }
        )
    }
}