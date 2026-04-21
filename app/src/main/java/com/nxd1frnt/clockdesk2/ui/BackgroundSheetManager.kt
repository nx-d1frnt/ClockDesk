package com.nxd1frnt.clockdesk2.ui.settings

import android.net.Uri
import android.os.Build
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.background.BackgroundManager
import com.nxd1frnt.clockdesk2.background.BackgroundsAdapter
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import com.nxd1frnt.clockdesk2.ui.view.WeatherGLView
import com.nxd1frnt.clockdesk2.ui.view.WeatherView
import com.nxd1frnt.clockdesk2.utils.Logger
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter

class BackgroundSheetManager(
    private val bottomSheetView: LinearLayout,
    private val mainLayout: View,
    private val backgroundCustomizationTab: View,
    private val backgroundManager: BackgroundManager,
    private val dayTimeGetter: DayTimeGetter,
    private val weatherGetter: WeatherGetter,
    private val weatherView: WeatherGLView,
    private val isMusicBackgroundApplied: () -> Boolean,
    private val onAddBackgroundRequested: () -> Unit,
    private val onPreviewImage: (Uri, blur: Int) -> Unit,
    private val onRestoreGradient: () -> Unit,
    private val onRestoreSavedBackground: () -> Unit,
    private val onUpdateFilters: () -> Unit,
    private val onApplyCompleted: (previewUri: String?) -> Unit,
    private val onClearBackground: () -> Unit,
    private val onSheetStateChanged: (isHidden: Boolean) -> Unit
) {

    private val behavior: BottomSheetBehavior<LinearLayout> = BottomSheetBehavior.from(bottomSheetView)
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null

    // Внутреннее состояние
    var previewBackgroundUri: String? = null
        private set
    private var isUpdatingBackgroundUi = false
    private var isApplying = false
    private val animationDuration = 300L
    private var backgroundsAdapter: BackgroundsAdapter? = null

    // Задачи для предотвращения OOM и спама (Debounce)
    private var previewTask: Runnable? = null
    private var filterTask: Runnable? = null

    // Ленивая инициализация UI
    private val bgRecycler by lazy { bottomSheetView.findViewById<RecyclerView>(R.id.background_recycler_view) }
    private val bgBlurSwitch by lazy { bottomSheetView.findViewById<MaterialSwitch>(R.id.background_blur_switch) }
    private val bgBlurSeek by lazy { bottomSheetView.findViewById<SeekBar>(R.id.blur_intensity_seekbar) }
    private val bgDimToggleGroup by lazy { bottomSheetView.findViewById<MaterialButtonToggleGroup>(R.id.dimming_toggle_group) }
    private val bgDimSeek by lazy { bottomSheetView.findViewById<SeekBar>(R.id.dimming_intensity_seekbar) }
    private val bgNightShiftSwitch: MaterialSwitch? by lazy { bottomSheetView.findViewById(R.id.background_night_shift_switch) }

    private val bgClearBtn by lazy { bottomSheetView.findViewById<Button>(R.id.clear_background_button_bs) }
    private val bgApplyBtn by lazy { bottomSheetView.findViewById<Button>(R.id.apply_background_button) }
    private val bgWeatherSwitch by lazy { bottomSheetView.findViewById<MaterialSwitch>(R.id.weather_effect_switch) }
    private val bgManualWeatherSwitch by lazy { bottomSheetView.findViewById<MaterialSwitch>(R.id.manual_weather_switch) }
    private val bgManualWeatherScroll by lazy { bottomSheetView.findViewById<View>(R.id.manual_weather_scroll) }
    private val bgWeatherToggleGroup by lazy { bottomSheetView.findViewById<MaterialButtonToggleGroup>(R.id.weather_type_toggle_group) }
    private val bgIntensityContainer by lazy { bottomSheetView.findViewById<View>(R.id.weather_intensity_container) }
    private val bgIntensitySeek by lazy { bottomSheetView.findViewById<SeekBar>(R.id.weather_intensity_seekbar) }

    init {
        setupBehavior()
        initControls()
    }

    private fun setupBehavior() {
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        behavior.peekHeight = 0
        behavior.isHideable = true
        behavior.isDraggable = true

        bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_DRAGGING,
                    BottomSheetBehavior.STATE_SETTLING,
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        onSheetStateChanged(false)
                    }
                    BottomSheetBehavior.STATE_HIDDEN,
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        // Отмена (смахивание) - восстанавливаем сохраненный фон, если не нажали Apply
                        if (previewBackgroundUri != null && !isApplying) {
                            if (!isMusicBackgroundApplied()) {
                                onRestoreSavedBackground()
                            }
                        }
                        previewBackgroundUri = null
                        isApplying = false

                        restoreMainLayoutState()
                        onSheetStateChanged(true)
                    }
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        }

        behavior.addBottomSheetCallback(bottomSheetCallback!!)
    }

    private fun initControls() {
        bgRecycler.layoutManager = LinearLayoutManager(bottomSheetView.context, LinearLayoutManager.HORIZONTAL, false)
        bgRecycler.isNestedScrollingEnabled = false

        setupAdapter()
        setupListeners()
    }

    private fun setupAdapter() {
        backgroundsAdapter = BackgroundsAdapter(
            bottomSheetView.context,
            mutableListOf(),
            onClick = { id ->
                if (id == "__ADD__") {
                    onAddBackgroundRequested()
                    return@BackgroundsAdapter
                }
                previewBackgroundUri = id

                if (isMusicBackgroundApplied()) return@BackgroundsAdapter

                when (id) {
                    "__DEFAULT_GRADIENT__" -> {
                        previewTask?.let { bottomSheetView.removeCallbacks(it) }
                        onRestoreGradient()
                    }
                    else -> {
                        // Debounce (250ms) предотвращает OOM при быстром "прокликивании" тяжелых фонов пользователем
                        debouncePreview {
                            try {
                                val uri = Uri.parse(id)
                                val intensity = bgBlurSeek.progress
                                onPreviewImage(uri, intensity)
                            } catch (e: Exception) {
                                Logger.e("BackgroundSheetManager") { "Error selecting background: $id - ${e.message}" }
                            }
                        }
                    }
                }
            },
            onLongClick = { id ->
                val context = bottomSheetView.context
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.delete_background_title))
                    .setMessage(context.getString(R.string.delete_background_msg))
                    .setPositiveButton(context.getString(R.string.delete)) { dialog, _ ->
                        backgroundManager.removeSavedUri(id)
                        if (previewBackgroundUri == id || backgroundManager.getSavedBackgroundUri() == id) {
                            bgClearBtn.performClick()
                        }
                        updateAdapterItems()
                        dialog.dismiss()
                    }
                    .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        )
        bgRecycler.adapter = backgroundsAdapter
    }

    private fun setupListeners() {
        bgBlurSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener
            if (!isChecked) bgBlurSeek.progress = 0
            else if (bgBlurSeek.progress == 0) bgBlurSeek.progress = 25
        }

        bgBlurSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) bgBlurSwitch.isChecked = progress > 0
                // Размытие ресурсоемкое, поэтому применяем его немедленно только после отпускания ползунка (оптимизация Glide GlideApp)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Применяем актуальное размытие к текущему превью при отпускании ползунка
                previewBackgroundUri?.let { id ->
                    if (id != "__DEFAULT_GRADIENT__" && !isMusicBackgroundApplied()) {
                        debouncePreview {
                            onPreviewImage(Uri.parse(id), bgBlurSeek.progress)
                        }
                    }
                }
            }
        })

        bgDimToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked || isUpdatingBackgroundUi) {
                if (group.checkedButtonId == View.NO_ID && !isUpdatingBackgroundUi) group.check(checkedId)
                return@addOnButtonCheckedListener
            }
            if (checkedId == R.id.off_button) bgDimSeek.progress = 0
            debounceFilterUpdate { onUpdateFilters() }
        }

        bgDimSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) debounceFilterUpdate { onUpdateFilters() }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Night Shift Listener
        bgNightShiftSwitch?.setOnCheckedChangeListener { _, _ ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener
            // We temporarily save the value to manager so updateBackgroundFilters() picks it up correctly
            // This is acceptable because if the user cancels, the old state restores correctly.
            val wasEnabledBefore = backgroundManager.isNightShiftEnabled()
            backgroundManager.setNightShiftEnabled(bgNightShiftSwitch?.isChecked == true)
            debounceFilterUpdate {
                onUpdateFilters()
                // Revert state so that if User cancels sheet (doesn't press Apply), it ignores changes.
                backgroundManager.setNightShiftEnabled(wasEnabledBefore)
            }
        }

        bgIntensitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) debounceFilterUpdate { applyWeatherPreview() }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bgWeatherToggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked && !isUpdatingBackgroundUi) applyWeatherPreview()
        }

        bgWeatherSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener
            bgManualWeatherSwitch.isEnabled = isChecked
            bgManualWeatherScroll.visibility = if (isChecked && bgManualWeatherSwitch.isChecked) View.VISIBLE else View.GONE
            weatherView.visibility = if (isChecked) View.VISIBLE else View.GONE
            bgIntensityContainer.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (!isChecked) {
                weatherView.updateFromOpenMeteoSmart(0, 0.0, !dayTimeGetter.isDay(), null, null, null)
                onUpdateFilters()
            } else {
                applyWeatherPreview()
            }
        }

        bgManualWeatherSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener
            bgManualWeatherScroll.visibility = if (isChecked) View.VISIBLE else View.GONE
            applyWeatherPreview()
        }

        bgClearBtn.setOnClickListener {
            isApplying = true // Предотвращаем срабатывание логики отмены при закрытии
            previewBackgroundUri = null
            onClearBackground()
            hide()
        }

        bgApplyBtn.setOnClickListener {
            isApplying = true
            applyBackgroundSettings()
        }
    }


    private fun debouncePreview(action: () -> Unit) {
        previewTask?.let { bottomSheetView.removeCallbacks(it) }
        previewTask = Runnable { action() }
        bottomSheetView.postDelayed(previewTask, 250)
    }

    private fun debounceFilterUpdate(action: () -> Unit) {
        filterTask?.let { bottomSheetView.removeCallbacks(it) }
        filterTask = Runnable { action() }
        bottomSheetView.postDelayed(filterTask, 32)
    }

    fun show() {
        scaleDownMainLayout()
        hideBackgroundTab()
        loadCurrentSettings()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun hide() {
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    fun onImageAdded(uriStr: String) {
        updateAdapterItems()
        previewBackgroundUri = uriStr
        if (!isMusicBackgroundApplied()) {
            onPreviewImage(Uri.parse(uriStr), bgBlurSeek.progress)
        }
    }

    val isShowing: Boolean
        get() = behavior.state != BottomSheetBehavior.STATE_HIDDEN && behavior.state != BottomSheetBehavior.STATE_COLLAPSED

    private fun loadCurrentSettings() {
        isUpdatingBackgroundUi = true
        updateAdapterItems()

        val savedUri = backgroundManager.getSavedBackgroundUri()
        backgroundsAdapter?.selectedId = savedUri ?: "__DEFAULT_GRADIENT__"
        bgRecycler.scrollToPosition(0)

        val blurInt = backgroundManager.getBlurIntensity()
        bgBlurSeek.progress = blurInt
        bgBlurSwitch.isChecked = blurInt > 0

        bgDimToggleGroup.check(when (backgroundManager.getDimMode()) {
            BackgroundManager.DIM_MODE_OFF -> R.id.off_button
            BackgroundManager.DIM_MODE_CONTINUOUS -> R.id.continuous_button
            BackgroundManager.DIM_MODE_DYNAMIC -> R.id.dynamic_button
            else -> R.id.off_button
        })
        bgDimSeek.progress = backgroundManager.getDimIntensity()

        bgNightShiftSwitch?.isChecked = backgroundManager.isNightShiftEnabled()

        val isWeatherEnabled = backgroundManager.isWeatherEffectsEnabled()
        val isManual = backgroundManager.isManualWeatherEnabled()

        bgWeatherSwitch.isChecked = isWeatherEnabled
        bgManualWeatherSwitch.isChecked = isManual
        bgManualWeatherSwitch.isEnabled = isWeatherEnabled

        bgManualWeatherScroll.visibility = if (isManual && isWeatherEnabled) View.VISIBLE else View.GONE
        bgIntensityContainer.visibility = if (isManual && isWeatherEnabled) View.VISIBLE else View.GONE
        bgIntensitySeek.progress = backgroundManager.getManualWeatherIntensity()

        bgWeatherToggleGroup.check(when (backgroundManager.getManualWeatherType()) {
            WeatherView.WeatherType.RAIN.ordinal -> R.id.btn_weather_rain
            WeatherView.WeatherType.SNOW.ordinal -> R.id.btn_weather_snow
            WeatherView.WeatherType.FOG.ordinal -> R.id.btn_weather_fog
            WeatherView.WeatherType.THUNDERSTORM.ordinal -> R.id.btn_weather_thunder
            WeatherView.WeatherType.CLOUDY.ordinal -> R.id.btn_weather_cloudy
            WeatherView.WeatherType.CLEAR.ordinal -> R.id.btn_weather_clear
            else -> R.id.btn_weather_rain
        })

        isUpdatingBackgroundUi = false
    }

    private fun applyBackgroundSettings() {
        backgroundManager.setBlurIntensity(bgBlurSeek.progress)

        backgroundManager.setDimMode(when (bgDimToggleGroup.checkedButtonId) {
            R.id.off_button -> BackgroundManager.DIM_MODE_OFF
            R.id.continuous_button -> BackgroundManager.DIM_MODE_CONTINUOUS
            R.id.dynamic_button -> BackgroundManager.DIM_MODE_DYNAMIC
            else -> BackgroundManager.DIM_MODE_OFF
        })
        backgroundManager.setDimIntensity(bgDimSeek.progress)

        bgNightShiftSwitch?.let {
            backgroundManager.setNightShiftEnabled(it.isChecked)
        }

        backgroundManager.setWeatherEffectsEnabled(bgWeatherSwitch.isChecked)
        backgroundManager.setManualWeatherEnabled(bgManualWeatherSwitch.isChecked)
        backgroundManager.setManualWeatherIntensity(bgIntensitySeek.progress)

        backgroundManager.setManualWeatherType(when (bgWeatherToggleGroup.checkedButtonId) {
            R.id.btn_weather_clear -> WeatherView.WeatherType.CLEAR.ordinal
            R.id.btn_weather_cloudy -> WeatherView.WeatherType.CLOUDY.ordinal
            R.id.btn_weather_rain -> WeatherView.WeatherType.RAIN.ordinal
            R.id.btn_weather_snow -> WeatherView.WeatherType.SNOW.ordinal
            R.id.btn_weather_fog -> WeatherView.WeatherType.FOG.ordinal
            R.id.btn_weather_thunder -> WeatherView.WeatherType.THUNDERSTORM.ordinal
            else -> WeatherView.WeatherType.RAIN.ordinal
        })

        onApplyCompleted(previewBackgroundUri)
        hide()
    }

    private fun applyWeatherPreview() {
        val isNight = !dayTimeGetter.isDay()
        if (bgManualWeatherSwitch.isChecked) {
            val typeOrdinal = when (bgWeatherToggleGroup.checkedButtonId) {
                R.id.btn_weather_clear -> WeatherView.WeatherType.CLEAR.ordinal
                R.id.btn_weather_cloudy -> WeatherView.WeatherType.CLOUDY.ordinal
                R.id.btn_weather_rain -> WeatherView.WeatherType.RAIN.ordinal
                R.id.btn_weather_snow -> WeatherView.WeatherType.SNOW.ordinal
                R.id.btn_weather_fog -> WeatherView.WeatherType.FOG.ordinal
                R.id.btn_weather_thunder -> WeatherView.WeatherType.THUNDERSTORM.ordinal
                else -> backgroundManager.getManualWeatherType()
            }
            val type = WeatherGLView.WeatherType.values().getOrElse(typeOrdinal) { WeatherGLView.WeatherType.CLEAR }
            val floatIntensity = bgIntensitySeek.progress / 100f
            weatherView.forceWeather(type, floatIntensity, 5.0f, isNight)
        } else {
            weatherView.updateFromOpenMeteoSmart(
                weatherGetter.weatherCode ?: 0,
                weatherGetter.windSpeed ?: 0.0,
                isNight,
                weatherGetter.precipitation,
                weatherGetter.cloudCover,
                weatherGetter.visibility
            )
        }
        onUpdateFilters()
    }

    private fun updateAdapterItems() {
        val items = mutableListOf<String>().apply {
            add("__DEFAULT_GRADIENT__")
            addAll(backgroundManager.getSavedUriSet())
            add("__ADD__")
        }
        backgroundsAdapter?.updateItems(items)
    }

    private fun scaleDownMainLayout() {
        val metrics = bottomSheetView.resources.displayMetrics
        val bottomSheetPx = 380f * metrics.density
        val targetScale = 0.60f
        val translationX = (metrics.widthPixels * (1f - targetScale) / 2f) - bottomSheetPx

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

    fun onDestroy() {
        bottomSheetCallback?.let { behavior.removeBottomSheetCallback(it) }
        previewTask?.let { bottomSheetView.removeCallbacks(it) }
        filterTask?.let { bottomSheetView.removeCallbacks(it) }

        bgRecycler.adapter = null
        backgroundsAdapter = null
    }
}