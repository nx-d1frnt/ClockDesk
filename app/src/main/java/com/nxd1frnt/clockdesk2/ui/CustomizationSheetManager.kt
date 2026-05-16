package com.nxd1frnt.clockdesk2.ui

import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.sidesheet.SideSheetBehavior
import com.google.android.material.sidesheet.SideSheetCallback
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import com.nxd1frnt.clockdesk2.ui.adapters.ColorAdapter
import com.nxd1frnt.clockdesk2.ui.adapters.FontAdapter
import com.nxd1frnt.clockdesk2.utils.ClockManager
import com.nxd1frnt.clockdesk2.utils.ColorItem
import com.nxd1frnt.clockdesk2.utils.FontAxis
import com.nxd1frnt.clockdesk2.utils.FontManager
import java.text.SimpleDateFormat
import java.util.Locale

class CustomizationSheetManager(
    private val sideSheetView: LinearLayout,
    private val mainLayout: View,
    private val backgroundCustomizationTab: View,
    private val fontManager: FontManager,
    private val widgetMover: WidgetMover,
    private val clockManager: ClockManager,
    private val dayTimeGetter: DayTimeGetter,
    private val onAddFontRequested: () -> Unit,
    private val onSheetStateChanged: (isHidden: Boolean) -> Unit
) {

    private val behavior: SideSheetBehavior<LinearLayout> = SideSheetBehavior.from(sideSheetView)

    private var focusedView: View? = null
    private var isEditingBackground = false
    private val animationDuration = 350L

    private var calculatedTargetTx = 0f
    private var dynamicTargetScale = 0.65f

    private val bsTitle by lazy { sideSheetView.findViewById<TextView>(R.id.customization_title) }
    private val bsSizeSeekBar by lazy { sideSheetView.findViewById<Slider>(R.id.size_seekbar) }
    private val bsSizeValue by lazy { sideSheetView.findViewById<TextView>(R.id.size_value) }

    private val bsMaxWidthContainer by lazy { sideSheetView.findViewById<LinearLayout>(R.id.max_width_container) }
    private val bsMaxWidthSeekBar by lazy { sideSheetView.findViewById<Slider>(R.id.max_width_seekbar) }
    private val bsMaxWidthValue by lazy { sideSheetView.findViewById<TextView>(R.id.max_width_value) }

    private val bsTransparencySeekBar by lazy { sideSheetView.findViewById<Slider>(R.id.transparency_seekbar) }
    private val bsTransparencyPreview by lazy { sideSheetView.findViewById<View>(R.id.transparency_preview) }

    private val bsFontRecyclerView by lazy { sideSheetView.findViewById<RecyclerView>(R.id.font_recycler_view) }
    private val bsColorRecyclerView by lazy { sideSheetView.findViewById<RecyclerView>(R.id.color_recycler_view) }

    private val bsApplyButton by lazy { sideSheetView.findViewById<Button>(R.id.apply_button) }
    private val bsCancelButton by lazy { sideSheetView.findViewById<Button>(R.id.cancel_button) }

    private val bsNightShiftSwitch by lazy { sideSheetView.findViewById<MaterialSwitch>(R.id.night_shift_switch) }
    private val bsEditBackgroundSwitch by lazy { sideSheetView.findViewById<MaterialSwitch>(R.id.edit_background_switch) }
    private val bsFreeModeSwitch by lazy { sideSheetView.findViewById<MaterialSwitch>(R.id.free_mode_switch) }
    private val bsGridSnapSwitch by lazy { sideSheetView.findViewById<MaterialSwitch>(R.id.grid_snap_switch) }
    private val bsIgnoreCollisionSwitch by lazy { sideSheetView.findViewById<MaterialSwitch>(R.id.ignore_collision_switch) }

    private val bsTimeFormatGroup by lazy { sideSheetView.findViewById<RadioGroup>(R.id.time_format_radio_group) }
    private val bsShowAMPMSwitch by lazy { sideSheetView.findViewById<MaterialSwitch>(R.id.show_am_pm_switch) }
    private val bsTimeCustomInputLayout by lazy { sideSheetView.findViewById<TextInputLayout>(R.id.time_custom_input_layout) }
    private val bsTimeCustomEditText by lazy { sideSheetView.findViewById<TextInputEditText>(R.id.time_custom_edit_text) }

    private val bsDateFormatGroup by lazy { sideSheetView.findViewById<RadioGroup>(R.id.date_format_radio_group) }
    private val bsDateCustomInputLayout by lazy { sideSheetView.findViewById<TextInputLayout>(R.id.date_custom_input_layout) }
    private val bsDateCustomEditText by lazy { sideSheetView.findViewById<TextInputEditText>(R.id.date_custom_edit_text) }

    private val bsTimeFormatLabel by lazy { sideSheetView.findViewById<TextView>(R.id.time_format_label) }
    private val bsDateFormatLabel by lazy { sideSheetView.findViewById<TextView>(R.id.date_format_label) }
    private val bsTextGravityTitle by lazy { sideSheetView.findViewById<TextView>(R.id.gravity_label) }
    private val bsAlignmentLabel by lazy { sideSheetView.findViewById<TextView>(R.id.alignment_label) }
    private val bsVerticalAlignmentLabel by lazy { sideSheetView.findViewById<TextView>(R.id.vertical_alignment_label) }
    private val bsWidgetOrderLabel by lazy { sideSheetView.findViewById<TextView>(R.id.widget_order_label) }

    private val bsTextGravityGroup by lazy { sideSheetView.findViewById<MaterialButtonToggleGroup>(R.id.text_gravity_toggle_group) }
    private val bsHorizontalAlignGroup by lazy { sideSheetView.findViewById<MaterialButtonToggleGroup>(R.id.alignment_toggle_group) }
    private val bsVerticalAlignGroup by lazy { sideSheetView.findViewById<MaterialButtonToggleGroup>(R.id.vertical_alignment_group) }

    private val bsMoveUpBtn by lazy { sideSheetView.findViewById<Button>(R.id.move_up_button) }
    private val bsMoveDownBtn by lazy { sideSheetView.findViewById<Button>(R.id.move_down_button) }

    private val bsVarTitle by lazy { sideSheetView.findViewById<TextView>(R.id.variable_properties_title) }

    private val dynamicAxesContainer: LinearLayout by lazy {
        val container = LinearLayout(sideSheetView.context).apply {
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
            sideSheetView.findViewById<View>(R.id.var_weight_container)?.visibility = View.GONE
            sideSheetView.findViewById<View>(R.id.var_width_container)?.visibility = View.GONE
            sideSheetView.findViewById<View>(R.id.var_roundness_container)?.visibility = View.GONE
        } catch (e: Exception) {}

        container
    }

    private fun preventSheetDragForSlider(slider: Slider) {
        slider.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }

    private fun preventSheetDragForRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
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

    init {
        setupBehavior()
        initControls()
    }

    private fun calculateHorizontalFocus(view: View) {
        val metrics = sideSheetView.resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val isTablet = sideSheetView.resources.configuration.smallestScreenWidthDp >= 600

        dynamicTargetScale = if (isTablet) 0.85f else 0.65f

        val sheetW = if (sideSheetView.width > 0) sideSheetView.width.toFloat() else (380f * metrics.density)

        var cx = view.width / 2f
        var currentView = view
        while (currentView !== mainLayout && currentView.parent is View) {
            cx += currentView.x
            currentView = currentView.parent as View
        }

        val pivotX = screenW / 2f
        val scaledCx = pivotX + (cx - pivotX) * dynamicTargetScale

        val visibleAreaCenter = (screenW - sheetW) / 2f
        val targetTx = visibleAreaCenter - scaledCx

        calculatedTargetTx = targetTx.coerceIn(-screenW, screenW)
    }

    private val focusUpdateRunnable = Runnable {
        focusedView?.let { view ->
            executeFocusShift(view)
        }
    }

    private fun executeFocusShift(view: View) {
        if (behavior.state == SideSheetBehavior.STATE_EXPANDED) {
            calculateHorizontalFocus(view)
            mainLayout.animate()
                .translationX(calculatedTargetTx)
                .setDuration(350)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }
    }

    private fun applyRealTimeFocusUpdate(isStructuralChange: Boolean = false) {
        if (focusedView == null) return

        sideSheetView.removeCallbacks(focusUpdateRunnable)

        val delay = if (isStructuralChange) 150L else 30L
        sideSheetView.postDelayed(focusUpdateRunnable, delay)
    }

    private fun setupBehavior() {
        behavior.state = SideSheetBehavior.STATE_HIDDEN

        behavior.addCallback(object : SideSheetCallback() {
            override fun onStateChanged(sheet: View, newState: Int) {
                if (newState == SideSheetBehavior.STATE_HIDDEN) {
                    onSheetStateChanged(true)
                    sideSheetView.removeCallbacks(focusUpdateRunnable)
                    mainLayout.scaleX = 0.90f
                    mainLayout.scaleY = 0.90f
                    mainLayout.translationX = 0f
                    mainLayout.translationY = 0f
                    backgroundCustomizationTab.alpha = 1f
                    backgroundCustomizationTab.visibility = View.VISIBLE
                    highlightFocusedView(false)
                    focusedView = null
                } else {
                    onSheetStateChanged(false)
                }
            }

            override fun onSlide(sheet: View, slideOffset: Float) {
                val safeOffset = slideOffset.coerceIn(0f, 1f)
                val baseScale = 0.90f

                sheet.alpha = safeOffset
                val sheetScale = 0.95f + (0.05f * safeOffset)
                sheet.scaleX = sheetScale
                sheet.scaleY = sheetScale

                val currentScale = baseScale - ((baseScale - dynamicTargetScale) * safeOffset)
                mainLayout.scaleX = currentScale
                mainLayout.scaleY = currentScale

                mainLayout.translationX = calculatedTargetTx * safeOffset

                backgroundCustomizationTab.alpha = 1f - safeOffset
                if (safeOffset < 1f && backgroundCustomizationTab.visibility == View.GONE) {
                    backgroundCustomizationTab.visibility = View.VISIBLE
                } else if (safeOffset == 1f && backgroundCustomizationTab.visibility == View.VISIBLE) {
                    backgroundCustomizationTab.visibility = View.GONE
                }
            }
        })
    }

    private fun initControls() {
        bsFontRecyclerView.layoutManager = LinearLayoutManager(sideSheetView.context, LinearLayoutManager.HORIZONTAL, false)
        bsFontRecyclerView.isNestedScrollingEnabled = false
        preventSheetDragForRecyclerView(bsFontRecyclerView)

        bsColorRecyclerView.layoutManager = LinearLayoutManager(sideSheetView.context, LinearLayoutManager.HORIZONTAL, false)
        preventSheetDragForRecyclerView(bsColorRecyclerView)

        preventSheetDragForSlider(bsSizeSeekBar)
        preventSheetDragForSlider(bsMaxWidthSeekBar)
        preventSheetDragForSlider(bsTransparencySeekBar)

        setupSizeAndTransparency()
        setupSwitchesAndToggles()
        setupButtons()
        setupFontAdapter()
    }

    fun showForView(viewToCustomize: View) {
        focusedView = viewToCustomize
        isEditingBackground = false

        highlightFocusedView(true)
        configureVisibilityForView(viewToCustomize)
        loadSettingsForView(viewToCustomize)

        calculateHorizontalFocus(viewToCustomize)

        if (behavior.state == SideSheetBehavior.STATE_EXPANDED) {
            mainLayout.animate()
                .scaleX(dynamicTargetScale)
                .scaleY(dynamicTargetScale)
                .translationX(calculatedTargetTx)
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator(1.2f))
                .start()
        }

        behavior.state = SideSheetBehavior.STATE_EXPANDED
    }

    fun hide() {
        behavior.state = SideSheetBehavior.STATE_HIDDEN
    }

    val isShowing: Boolean
        get() = behavior.state != SideSheetBehavior.STATE_HIDDEN

    fun onFontAdded(newIndex: Int) {
        if (newIndex > 0) {
            bsFontRecyclerView.adapter?.notifyDataSetChanged()
            (bsFontRecyclerView.adapter as? FontAdapter)?.selectedPosition = newIndex
            focusedView?.let { view ->
                fontManager.setFontIndex(view, newIndex)
                updateVariationVisibility()
                applyRealTimeFocusUpdate(true)
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
        val context = sideSheetView.context
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

        val slider = Slider(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            val min = axis.minValue
            val max = axis.maxValue
            if (max > min) {
                valueFrom = min
                valueTo = max
                value = initialValue.coerceIn(min, max)
            } else {
                valueFrom = 0f; valueTo = 1f; value = 0f; isEnabled = false
            }

            addOnChangeListener { _, sliderValue, fromUser ->
                if (fromUser && focusedView != null) {
                    valueText.text = String.format("%.1f", sliderValue)
                    fontManager.setVariationAxis(focusedView!!, axis.tag, sliderValue)
                    applyRealTimeFocusUpdate(false)
                }
            }
        }

        preventSheetDragForSlider(slider)
        container.addView(nameText); container.addView(slider); container.addView(valueText)
        return container
    }

    private fun getAxisDisplayName(tag: String): String {
        return when (tag) {
            "wght" -> "Weight"; "wdth" -> "Width"; "slnt" -> "Slant"; "ital" -> "Italic"
            "opsz" -> "Optical Size"; "ROND" -> "Roundness"; "GRAD" -> "Grade"
            "CASL" -> "Casual"; "MONO" -> "Monospace"; else -> tag
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

        bsTitle.text = sideSheetView.context.getString(
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
        bsTimeCustomInputLayout.visibility = if (isTime && bsTimeFormatGroup.checkedRadioButtonId == R.id.time_custom_radio) View.VISIBLE else View.GONE
        bsShowAMPMSwitch.visibility = if (isTime && bsTimeFormatGroup.checkedRadioButtonId != R.id.time_custom_radio) View.VISIBLE else View.GONE

        bsDateFormatGroup.visibility = if (isDate) View.VISIBLE else View.GONE
        bsDateFormatLabel.visibility = if (isDate) View.VISIBLE else View.GONE
        bsDateCustomInputLayout.visibility = if (isDate && bsDateFormatGroup.checkedRadioButtonId == R.id.date_custom_radio) View.VISIBLE else View.GONE

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
        val metrics = sideSheetView.resources.displayMetrics
        val sizeOffset = 8
        val maxvalue = (metrics.widthPixels / metrics.density * 0.3f)
        val safeMax = (maxvalue - sizeOffset).coerceAtLeast(1f)

        bsSizeSeekBar.apply {
            valueFrom = 0f; valueTo = safeMax
            value = (settings.size - sizeOffset).coerceIn(0f, safeMax)
        }
        bsSizeValue.text = sideSheetView.context.getString(R.string.size_value_format, settings.size.toInt())

        bsMaxWidthSeekBar.apply {
            valueFrom = 0f; valueTo = 100f
            value = settings.maxWidthPercent.toFloat().coerceIn(0f, 100f)
        }
        bsMaxWidthValue.text = "${settings.maxWidthPercent}%"

        bsTransparencySeekBar.apply {
            valueFrom = 0f; valueTo = 100f
            value = (settings.alpha * 100).coerceIn(0f, 100f)
        }
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

        // Загрузка формата времени
        if (view.id == R.id.time_text) {
            val timePattern = fontManager.getTimeFormatPattern()
            bsTimeFormatGroup.setOnCheckedChangeListener(null)
            when (timePattern) {
                "HH:mm" -> {
                    bsTimeFormatGroup.check(R.id.time_24_radio)
                    bsShowAMPMSwitch.isChecked = false
                }
                "hh:mm a", "hh:mm" -> {
                    bsTimeFormatGroup.check(R.id.time_12_radio)
                    bsShowAMPMSwitch.isChecked = timePattern.contains("a")
                }
                else -> {
                    bsTimeFormatGroup.check(R.id.time_custom_radio)
                    bsTimeCustomEditText.setText(timePattern)
                }
            }
            setupSwitchesAndToggles() // Перепривязываем листенеры
        }

        // Загрузка формата даты
        if (view.id == R.id.date_text) {
            val datePattern = fontManager.getDateFormatPattern()
            bsDateFormatGroup.setOnCheckedChangeListener(null)
            when (datePattern) {
                "MMM dd" -> bsDateFormatGroup.check(R.id.date_format_1)
                "EEE, MMM dd" -> bsDateFormatGroup.check(R.id.date_format_2)
                "EEEE, MMMM dd, yyyy" -> bsDateFormatGroup.check(R.id.date_format_3)
                else -> {
                    bsDateFormatGroup.check(R.id.date_custom_radio)
                    bsDateCustomEditText.setText(datePattern)
                }
            }
            setupSwitchesAndToggles() // Перепривязываем листенеры
        }

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
        bsSizeSeekBar.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || focusedView == null) return@addOnChangeListener
            val size = (value + 8f)
            bsSizeValue.text = sideSheetView.context.getString(R.string.size_value_format, size.toInt())
            fontManager.setFontSize(focusedView!!, size)
            applyRealTimeFocusUpdate(false)
        }

        bsMaxWidthSeekBar.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || focusedView == null) return@addOnChangeListener
            val progress = value.toInt()
            bsMaxWidthValue.text = "$progress%"
            if (focusedView!!.id == R.id.lastfm_layout) {
                fontManager.setMaxWidthPercent(focusedView!!, progress)
                applyRealTimeFocusUpdate(false)
            }
        }

        bsTransparencySeekBar.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || focusedView == null) return@addOnChangeListener
            val alpha = value / 100f
            bsTransparencyPreview.alpha = alpha
            fontManager.setFontAlpha(focusedView!!, alpha)
        }
    }

    private fun setupSwitchesAndToggles() {
        bsEditBackgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            isEditingBackground = isChecked
            focusedView?.let { bsColorRecyclerView.adapter = createColorAdapter(it) }
        }

        bsGridSnapSwitch.setOnCheckedChangeListener { _, isChecked -> widgetMover.setGridSnapEnabled(isChecked) }
        bsIgnoreCollisionSwitch.setOnCheckedChangeListener { _, isChecked -> widgetMover.setCollisionCheckEnabled(isChecked) }

        bsHorizontalAlignGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || focusedView == null) return@addOnButtonCheckedListener
            widgetMover.alignViewHorizontal(focusedView!!, when (checkedId) {
                R.id.left_button -> 0
                R.id.right_button -> 2
                else -> 1
            })
            applyRealTimeFocusUpdate(true)
        }

        bsVerticalAlignGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || focusedView == null) return@addOnButtonCheckedListener
            widgetMover.alignViewVertical(focusedView!!, when (checkedId) {
                R.id.align_top_button -> 0
                R.id.align_bottom_button -> 2
                else -> 1
            })
        }

        bsTextGravityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || focusedView == null) return@addOnButtonCheckedListener
            widgetMover.setTextGravity(focusedView!!, when (checkedId) {
                R.id.gravity_left_button -> widgetMover.GRAVITY_START
                R.id.gravity_right_button -> widgetMover.GRAVITY_END
                else -> widgetMover.GRAVITY_CENTER
            })
            applyRealTimeFocusUpdate(true)
        }

        bsTimeFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            if (focusedView?.id == R.id.time_text) {
                if (checkedId == R.id.time_custom_radio) {
                    bsTimeCustomInputLayout.visibility = View.VISIBLE
                    bsShowAMPMSwitch.visibility = View.GONE
                    val pattern = bsTimeCustomEditText.text?.toString()?.takeIf { it.isNotBlank() } ?: "HH:mm"
                    fontManager.setTimeFormatPattern(pattern)
                } else {
                    bsTimeCustomInputLayout.visibility = View.GONE
                    bsShowAMPMSwitch.visibility = View.VISIBLE
                    val pattern = if (checkedId == R.id.time_24_radio) "HH:mm" else if (bsShowAMPMSwitch.isChecked) "hh:mm a" else "hh:mm"
                    bsShowAMPMSwitch.isEnabled = (checkedId != R.id.time_24_radio)
                    fontManager.setTimeFormatPattern(pattern)
                }
                clockManager.updateTimeText()
                applyRealTimeFocusUpdate(true)
            }
        }

        bsShowAMPMSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (focusedView?.id == R.id.time_text && bsTimeFormatGroup.checkedRadioButtonId != R.id.time_custom_radio) {
                fontManager.setTimeFormatPattern(if (isChecked) "hh:mm a" else "hh:mm")
                clockManager.updateTimeText()
                applyRealTimeFocusUpdate(true)
            }
        }

        bsTimeCustomEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (focusedView?.id == R.id.time_text && bsTimeFormatGroup.checkedRadioButtonId == R.id.time_custom_radio) {
                    val pattern = s?.toString()?.takeIf { it.isNotBlank() } ?: "HH:mm"
                    try {
                        SimpleDateFormat(pattern, Locale.getDefault()) // Валидация
                        bsTimeCustomInputLayout.error = null
                        fontManager.setTimeFormatPattern(pattern)
                        clockManager.updateTimeText()
                        applyRealTimeFocusUpdate(false)
                    } catch (e: Exception) {
                        bsTimeCustomInputLayout.error = "Invalid format"
                    }
                }
            }
        })

        bsDateFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            if (focusedView?.id == R.id.date_text) {
                if (checkedId == R.id.date_custom_radio) {
                    bsDateCustomInputLayout.visibility = View.VISIBLE
                    val pattern = bsDateCustomEditText.text?.toString()?.takeIf { it.isNotBlank() } ?: "MMM dd"
                    fontManager.setDateFormatPattern(pattern)
                } else {
                    bsDateCustomInputLayout.visibility = View.GONE
                    fontManager.setDateFormatPattern(when (checkedId) {
                        R.id.date_format_1 -> "MMM dd"
                        R.id.date_format_3 -> "EEEE, MMMM dd, yyyy"
                        else -> "EEE, MMM dd"
                    })
                }
                clockManager.updateDateText()
                applyRealTimeFocusUpdate(true)
            }
        }

        bsDateCustomEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (focusedView?.id == R.id.date_text && bsDateFormatGroup.checkedRadioButtonId == R.id.date_custom_radio) {
                    val pattern = s?.toString()?.takeIf { it.isNotBlank() } ?: "MMM dd"
                    try {
                        SimpleDateFormat(pattern, Locale.getDefault()) // Валидация
                        bsDateCustomInputLayout.error = null
                        fontManager.setDateFormatPattern(pattern)
                        clockManager.updateDateText()
                        applyRealTimeFocusUpdate(false)
                    } catch (e: Exception) {
                        bsDateCustomInputLayout.error = "Invalid format"
                    }
                }
            }
        })
    }

    private fun setupButtons() {
        bsMoveUpBtn.setOnClickListener { focusedView?.let { widgetMover.moveWidgetOrder(it, true); applyRealTimeFocusUpdate(true) } }
        bsMoveDownBtn.setOnClickListener { focusedView?.let { widgetMover.moveWidgetOrder(it, false); applyRealTimeFocusUpdate(true) } }

        bsApplyButton.setOnClickListener { fontManager.saveSettings(); hide() }
        bsCancelButton.setOnClickListener { fontManager.loadFont(); widgetMover.restoreOrderAndPositions(); hide() }
    }

    private fun setupFontAdapter() {
        val adapter = FontAdapter(
            fonts = fontManager.getFonts(),
            onFontSelected = { fontIndex ->
                focusedView?.let {
                    fontManager.setFontIndex(it, fontIndex)
                    updateVariationVisibility()
                    applyRealTimeFocusUpdate(true)
                }
            },
            onAddFontClicked = { onAddFontRequested() },
            onFontLongClick = { fontIndex ->
                val context = sideSheetView.context
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.delete_font_title))
                    .setMessage(context.getString(R.string.delete_font_msg))
                    .setPositiveButton(context.getString(R.string.delete)) { dialog, _ ->
                        if (fontManager.deleteCustomFont(fontIndex)) {
                            bsFontRecyclerView.adapter?.notifyDataSetChanged()
                            focusedView?.let { view -> (bsFontRecyclerView.adapter as? FontAdapter)?.selectedPosition = fontManager.getSettings(view)?.fontIndex ?: 1 }
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