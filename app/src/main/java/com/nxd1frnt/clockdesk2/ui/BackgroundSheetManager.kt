package com.nxd1frnt.clockdesk2.ui.settings

import android.net.Uri
import android.os.Build
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.background.BackgroundManager
import com.nxd1frnt.clockdesk2.background.BackgroundsAdapter
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import com.nxd1frnt.clockdesk2.ui.view.DynamicBackgroundView
import com.nxd1frnt.clockdesk2.utils.Logger
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter

class BackgroundSheetManager(
    private val floatingMenuView: View,
    private val mainLayout: View,
    private val backgroundCustomizationTab: View,
    private val backgroundManager: BackgroundManager,
    private val dayTimeGetter: DayTimeGetter,
    private val weatherGetter: WeatherGetter,
    private val weatherView: DynamicBackgroundView,
    private val isMusicBackgroundApplied: () -> Boolean,
    private val onAddBackgroundRequested: () -> Unit,
    private val onPreviewImage: (Uri, blur: Int) -> Unit,
    private val onPreviewBlur: (blur: Int) -> Unit,
    private val onPreviewGradient: () -> Unit,
    private val onPreviewMusicAlbumArt: (enabled: Boolean, blur: Int) -> Unit,
    private val onRestoreOriginalState: () -> Unit,
    private val onUpdateFilters: (Int?, Int?, Int?, Int?, Boolean?, Boolean?) -> Unit,
    private val onApplyCompleted: (previewUri: String?, blur: Int) -> Unit,
    private val onClearBackground: () -> Unit,
    private val onSheetStateChanged: (isHidden: Boolean) -> Unit,
    private val onCropRequested: () -> Unit,
) {

    var previewBackgroundUri: String? = null
        private set
    private var isUpdatingBackgroundUi = false
    private var isApplying = false
    private var initialMusicAlbumArtEnabled = true
    private val animationDuration = 350L
    private var backgroundsAdapter: BackgroundsAdapter? = null

    private var previewTask: Runnable? = null
    private var filterTask: Runnable? = null
    private var blurTask: Runnable? = null

    private val bgRecycler by lazy { floatingMenuView.findViewById<RecyclerView>(R.id.background_recycler_view) }
    private val bgBlurSeek by lazy { floatingMenuView.findViewById<Slider>(R.id.blur_intensity_seekbar) }
    private val bgMusicAlbumArtSwitch: MaterialSwitch? by lazy { floatingMenuView.findViewById(R.id.music_albumart_switch) }
    private val bgDimToggleGroup by lazy { floatingMenuView.findViewById<MaterialButtonToggleGroup>(R.id.dimming_toggle_group) }
    private val bgDimSeek by lazy { floatingMenuView.findViewById<Slider>(R.id.dimming_intensity_seekbar) }
    private val bgDimRangeSeek by lazy { floatingMenuView.findViewById<RangeSlider>(R.id.dimming_intensity_range_slider) }
    private val bgNightShiftSwitch: MaterialSwitch? by lazy { floatingMenuView.findViewById(R.id.background_night_shift_switch) }
    private val bgZoomSwitch: MaterialSwitch? by lazy { floatingMenuView.findViewById(R.id.background_zoom_switch) }

    private val bgApplyBtn: Button? by lazy { floatingMenuView.findViewById(R.id.apply_background_button) }
    private val bgCancelBtn: Button? by lazy { floatingMenuView.findViewById(R.id.cancel_background_button) }
    private val bgCropBtn: Button? by lazy { floatingMenuView.findViewById(R.id.crop_position_button) }

    private val bgWeatherSwitch by lazy { floatingMenuView.findViewById<MaterialSwitch>(R.id.weather_effect_switch) }
    private val bgManualWeatherSwitch by lazy { floatingMenuView.findViewById<MaterialSwitch>(R.id.manual_weather_switch) }
    private val bgManualWeatherScroll by lazy { floatingMenuView.findViewById<View>(R.id.manual_weather_scroll) }
    private val bgWeatherToggleGroup by lazy { floatingMenuView.findViewById<MaterialButtonToggleGroup>(R.id.weather_type_toggle_group) }
    private val bgIntensitySeek by lazy { floatingMenuView.findViewById<Slider>(R.id.weather_intensity_seekbar) }
    private val bgAutoWeatherCard by lazy { floatingMenuView.findViewById<View>(R.id.current_weather_card) }
    private val bgAutoWeatherIcon by lazy { floatingMenuView.findViewById<android.widget.TextView>(R.id.weather_auto_icon) }
    private val bgAutoWeatherDetail by lazy { floatingMenuView.findViewById<android.widget.TextView>(R.id.weather_auto_detail) }
    private val bgAutoWeatherTemp by lazy { floatingMenuView.findViewById<android.widget.TextView>(R.id.weather_auto_temp) }

    private val contentContainer by lazy { floatingMenuView.findViewById<ViewGroup>(R.id.settings_content_container) }
    private val tabStyle by lazy { floatingMenuView.findViewById<View>(R.id.tab_style_content) }
    private val tabWeather by lazy { floatingMenuView.findViewById<View>(R.id.tab_weather_content) }
    private val tabEffects by lazy { floatingMenuView.findViewById<View>(R.id.tab_effects_content) }
    private val bottomNavGroup by lazy { floatingMenuView.findViewById<MaterialButtonToggleGroup>(R.id.bottom_nav_group) }

    init {
        floatingMenuView.visibility = View.GONE
        initControls()
        setupNavigation()
    }

    private fun initControls() {
        bgRecycler.layoutManager = LinearLayoutManager(floatingMenuView.context, LinearLayoutManager.HORIZONTAL, false)
        bgRecycler.isNestedScrollingEnabled = false
        setupAdapter()
        setupListeners()
    }

    private fun setupAdapter() {
        backgroundsAdapter = BackgroundsAdapter(
            floatingMenuView.context,
            mutableListOf(),
            onClick = { id ->
                if (id == "__ADD__") {
                    onAddBackgroundRequested()
                    return@BackgroundsAdapter
                }
                previewBackgroundUri = id

                when (id) {
                    "__DEFAULT_GRADIENT__" -> {
                        previewTask?.let { floatingMenuView.removeCallbacks(it) }
                        onPreviewGradient()
                        updateCropButtonVisibility(false)
                    }
                    else -> {
                        previewTask?.let { floatingMenuView.removeCallbacks(it) }
                        try {
                            val uri = Uri.parse(id)
                            val intensity = bgBlurSeek.value.toInt()
                            onPreviewImage(uri, intensity)
                            updateCropButtonVisibility(true)
                        } catch (e: Exception) {
                            Logger.e("BackgroundSheetManager") { "Error selecting background: $id - ${e.message}" }
                        }
                    }
                }
            },
            onLongClick = { id ->
                val context = floatingMenuView.context
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.delete_background_title))
                    .setMessage(context.getString(R.string.delete_background_msg))
                    .setPositiveButton(context.getString(R.string.delete)) { dialog, _ ->
                        backgroundManager.removeSavedUri(id)
                        if (previewBackgroundUri == id || backgroundManager.getSavedBackgroundUri() == id) {
                            isApplying = true
                            previewBackgroundUri = null
                            onClearBackground()
                            hide()
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

    private fun triggerFilterUpdate() {
        debounceFilterUpdate {
            onUpdateFilters(
                getPreviewDimMode(),
                getPreviewDimIntensity(),
                getPreviewDimMin(),
                getPreviewDimMax(),
                bgNightShiftSwitch?.isChecked,
                bgZoomSwitch?.isChecked
            )
        }
    }

    private fun setupListeners() {
        bgBlurSeek.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingBackgroundUi) {
                debounceBlur {
                    onPreviewBlur(value.toInt())
                }
            }
        }

        bgBlurSeek.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                blurTask?.let {
                    floatingMenuView.removeCallbacks(it)
                    it.run()
                    blurTask = null
                }
            }
        })

        bgDimToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked || isUpdatingBackgroundUi) {
                if (group.checkedButtonId == View.NO_ID && !isUpdatingBackgroundUi) group.check(checkedId)
                return@addOnButtonCheckedListener
            }
            val mode = getPreviewDimMode()
            if (mode == BackgroundManager.DIM_MODE_DYNAMIC) {
                bgDimSeek.visibility = View.GONE
                bgDimRangeSeek.visibility = View.VISIBLE
            } else {
                bgDimSeek.visibility = View.VISIBLE
                bgDimRangeSeek.visibility = View.GONE
                bgDimSeek.isEnabled = (mode != BackgroundManager.DIM_MODE_OFF)
            }
            triggerFilterUpdate()
        }

        bgDimSeek.addOnChangeListener { _, _, fromUser ->
            if (fromUser) triggerFilterUpdate()
        }

        bgDimRangeSeek.addOnChangeListener { _, _, fromUser ->
            if (fromUser) triggerFilterUpdate()
        }

        bgNightShiftSwitch?.setOnCheckedChangeListener { _, _ ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener
            triggerFilterUpdate()
        }

        bgZoomSwitch?.setOnCheckedChangeListener { _, _ ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener
            triggerFilterUpdate()
        }

        bgMusicAlbumArtSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener
            onPreviewMusicAlbumArt(isChecked, bgBlurSeek.value.toInt())
        }

        bgIntensitySeek.addOnChangeListener { _, _, fromUser ->
            if (fromUser) debounceFilterUpdate { applyWeatherPreview() }
        }

        bgWeatherToggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked && !isUpdatingBackgroundUi) applyWeatherPreview()
        }

        bgWeatherSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener
            bgManualWeatherSwitch.isEnabled = isChecked
            bgManualWeatherScroll.visibility = if (isChecked && bgManualWeatherSwitch.isChecked) View.VISIBLE else View.GONE
            bgIntensitySeek.visibility = if (isChecked && bgManualWeatherSwitch.isChecked) View.VISIBLE else View.GONE
            bgAutoWeatherCard.visibility = if (isChecked && !bgManualWeatherSwitch.isChecked) View.VISIBLE else View.GONE

            if (!isChecked) {
                weatherView.forceWeather(DynamicBackgroundView.WeatherType.NONE, 0f, 0f, !dayTimeGetter.isDay(), dayTimeGetter.getDayFactor())
                triggerFilterUpdate()
            } else {
                applyWeatherPreview()
            }
        }

        bgManualWeatherSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener
            bgManualWeatherScroll.visibility = if (isChecked) View.VISIBLE else View.GONE
            bgIntensitySeek.visibility = if (isChecked) View.VISIBLE else View.GONE
            bgAutoWeatherCard.visibility = if (!isChecked && bgWeatherSwitch.isChecked) View.VISIBLE else View.GONE
            updateAutoWeatherCard()
            applyWeatherPreview()
        }

        bgCropBtn?.setOnClickListener {
            val uri = previewBackgroundUri ?: if (!isMusicBackgroundApplied()) backgroundManager.getSavedBackgroundUri() else null
            if (uri != null && uri != "__DEFAULT_GRADIENT__") {
                if (uri != backgroundManager.getSavedBackgroundUri()) {
                    backgroundManager.resetBgTransform()
                }
                onCropRequested()
            }
        }

        bgApplyBtn?.setOnClickListener {
            isApplying = true
            applyBackgroundSettings()
        }

        bgCancelBtn?.setOnClickListener {
            cancelAndHide()
        }
    }

    private fun setupNavigation() {
        bottomNavGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val transition = AutoTransition().apply {
                duration = 250L
                interpolator = OvershootInterpolator(0.8f)
            }
            TransitionManager.beginDelayedTransition(contentContainer, transition)
            tabStyle.visibility = if (checkedId == R.id.nav_style) View.VISIBLE else View.GONE
            tabWeather.visibility = if (checkedId == R.id.nav_weather) View.VISIBLE else View.GONE
            tabEffects.visibility = if (checkedId == R.id.nav_effects) View.VISIBLE else View.GONE
        }
    }

    private fun debounceBlur(action: () -> Unit) {
        blurTask?.let { floatingMenuView.removeCallbacks(it) }
        blurTask = Runnable { action() }
        floatingMenuView.postDelayed(blurTask, 60)
    }

    private fun debouncePreview(action: () -> Unit) {
        previewTask?.let { floatingMenuView.removeCallbacks(it) }
        previewTask = Runnable { action() }
        floatingMenuView.postDelayed(previewTask, 250)
    }

    private fun debounceFilterUpdate(action: () -> Unit) {
        filterTask?.let { floatingMenuView.removeCallbacks(it) }
        filterTask = Runnable { action() }
        floatingMenuView.postDelayed(filterTask, 32)
    }

    fun show() {
        isApplying = false
        initialMusicAlbumArtEnabled = backgroundManager.isMusicAlbumArtEnabled()
        loadCurrentSettings()
        scaleDownMainLayout()
        hideBackgroundTab()

        floatingMenuView.visibility = View.VISIBLE
        floatingMenuView.alpha = 1f

        val settingsCard = floatingMenuView.findViewById<View>(R.id.settings_main_card)
        val navCard = floatingMenuView.findViewById<View>(R.id.bottom_nav_card)

        val interpolator = OvershootInterpolator(0.8f)

        bgApplyBtn?.apply { alpha = 0f; translationY = -50f }
        bgCancelBtn?.apply { alpha = 0f; translationY = -50f }
        settingsCard?.apply { alpha = 0f; translationY = 100f; scaleX = 0.95f; scaleY = 0.95f }
        navCard?.apply { alpha = 0f; translationY = 100f; scaleX = 0.95f; scaleY = 0.95f }
        bgCropBtn?.apply { alpha = 0f; translationY = -50f }

        bgApplyBtn?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(animationDuration)?.setInterpolator(interpolator)?.start()
        bgCancelBtn?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(animationDuration)?.setInterpolator(interpolator)?.start()

        settingsCard?.animate()?.alpha(1f)?.translationY(0f)?.scaleX(1f)?.scaleY(1f)
            ?.setDuration(animationDuration)?.setStartDelay(50)?.setInterpolator(interpolator)?.start()

        bgCropBtn?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(animationDuration)?.setStartDelay(150)?.setInterpolator(interpolator)?.start()

        navCard?.animate()?.alpha(1f)?.translationY(0f)?.scaleX(1f)?.scaleY(1f)
            ?.setDuration(animationDuration)?.setStartDelay(100)?.setInterpolator(interpolator)?.start()

        onSheetStateChanged(false)
    }

    fun hide() {
        val settingsCard = floatingMenuView.findViewById<View>(R.id.settings_main_card)
        val navCard = floatingMenuView.findViewById<View>(R.id.bottom_nav_card)

        val interpolator = android.view.animation.AnticipateInterpolator(0.8f)

        bgApplyBtn?.animate()?.alpha(0f)?.translationY(-50f)?.setDuration(250)?.setInterpolator(interpolator)?.setStartDelay(0)?.start()
        bgCancelBtn?.animate()?.alpha(0f)?.translationY(-50f)?.setDuration(250)?.setInterpolator(interpolator)?.setStartDelay(0)?.start()

        settingsCard?.animate()?.alpha(0f)?.translationY(100f)?.scaleX(0.95f)?.scaleY(0.95f)
            ?.setDuration(250)?.setStartDelay(0)?.setInterpolator(interpolator)?.start()

        navCard?.animate()?.alpha(0f)?.translationY(100f)?.scaleX(0.95f)?.scaleY(0.95f)
            ?.setDuration(250)?.setStartDelay(0)?.setInterpolator(interpolator)?.withEndAction {
                floatingMenuView.visibility = View.GONE
                settingsCard?.animate()?.setStartDelay(0)?.setInterpolator(null)
                navCard?.animate()?.setStartDelay(0)?.setInterpolator(null)
            }?.start()
        bgCropBtn?.animate()?.alpha(0f)?.translationY(-50f)?.setDuration(250)?.setInterpolator(interpolator)?.setStartDelay(0)?.start()

        restoreMainLayoutState()
        onSheetStateChanged(true)
    }

    fun cancelAndHide() {
        if (!isApplying) {
            backgroundManager.setMusicAlbumArtEnabled(initialMusicAlbumArtEnabled)
            onRestoreOriginalState()
        }
        previewBackgroundUri = null
        isApplying = false
        hide()
    }

    fun onImageAdded(uriStr: String) {
        updateAdapterItems()
        previewBackgroundUri = uriStr
        backgroundsAdapter?.updateSelection(uriStr)
        try {
            onPreviewImage(Uri.parse(uriStr), bgBlurSeek.value.toInt())
            updateCropButtonVisibility(true)
        } catch (e: Exception) {
            Logger.e("BackgroundSheetManager") { "Error previewing added image: $uriStr - ${e.message}" }
        }
    }

    val isShowing: Boolean
        get() = floatingMenuView.visibility == View.VISIBLE

    private fun loadCurrentSettings() {
        isUpdatingBackgroundUi = true
        updateAdapterItems()

        val isMusic = isMusicBackgroundApplied()
        val savedUri = backgroundManager.getSavedBackgroundUri()

        val currentPreview = previewBackgroundUri
        if (currentPreview != null) {
            backgroundsAdapter?.selectedId = currentPreview
            updateCropButtonVisibility(currentPreview != "__DEFAULT_GRADIENT__")
        } else if (isMusic) {
            backgroundsAdapter?.selectedId = null
            updateCropButtonVisibility(false)
        } else {
            backgroundsAdapter?.selectedId = savedUri ?: "__DEFAULT_GRADIENT__"
            val hasCustom = savedUri != null && savedUri != "__DEFAULT_GRADIENT__"
            updateCropButtonVisibility(hasCustom)
        }

        bgRecycler.scrollToPosition(0)

        val blurInt = backgroundManager.getBlurIntensity()
        bgBlurSeek.value = blurInt.toFloat()

        val dimMode = backgroundManager.getDimMode()
        bgDimToggleGroup.check(when (dimMode) {
            BackgroundManager.DIM_MODE_OFF -> R.id.off_button
            BackgroundManager.DIM_MODE_CONTINUOUS -> R.id.continuous_button
            BackgroundManager.DIM_MODE_DYNAMIC -> R.id.dynamic_button
            else -> R.id.off_button
        })
        bgDimSeek.value = backgroundManager.getDimIntensity().toFloat()

        val minDim = backgroundManager.getDimMinIntensity()
        val maxDim = backgroundManager.getDimMaxIntensity()
        bgDimRangeSeek.values = listOf(minDim.toFloat(), maxDim.toFloat())

        if (dimMode == BackgroundManager.DIM_MODE_DYNAMIC) {
            bgDimSeek.visibility = View.GONE
            bgDimRangeSeek.visibility = View.VISIBLE
        } else {
            bgDimSeek.visibility = View.VISIBLE
            bgDimRangeSeek.visibility = View.GONE
            bgDimSeek.isEnabled = (dimMode != BackgroundManager.DIM_MODE_OFF)
        }

        bgNightShiftSwitch?.isChecked = backgroundManager.isNightShiftEnabled()
        bgZoomSwitch?.isChecked = backgroundManager.getZoomEnabled()
        bgMusicAlbumArtSwitch?.isChecked = backgroundManager.isMusicAlbumArtEnabled()

        val isWeatherEnabled = backgroundManager.isWeatherEffectsEnabled()
        val isManual = backgroundManager.isManualWeatherEnabled()

        bgWeatherSwitch.isChecked = isWeatherEnabled
        bgManualWeatherSwitch.isChecked = isManual
        bgManualWeatherSwitch.isEnabled = isWeatherEnabled

        bgManualWeatherScroll.visibility = if (isManual && isWeatherEnabled) View.VISIBLE else View.GONE
        bgIntensitySeek.visibility = if (isManual && isWeatherEnabled) View.VISIBLE else View.GONE
        bgAutoWeatherCard.visibility = if (!isManual && isWeatherEnabled) View.VISIBLE else View.GONE
        updateAutoWeatherCard()
        bgIntensitySeek.value = backgroundManager.getManualWeatherIntensity().toFloat()

        bgWeatherToggleGroup.check(when (backgroundManager.getManualWeatherType()) {
            DynamicBackgroundView.WeatherType.RAIN.ordinal -> R.id.btn_weather_rain
            DynamicBackgroundView.WeatherType.SNOW.ordinal -> R.id.btn_weather_snow
            DynamicBackgroundView.WeatherType.FOG.ordinal -> R.id.btn_weather_fog
            DynamicBackgroundView.WeatherType.THUNDERSTORM.ordinal -> R.id.btn_weather_thunder
            DynamicBackgroundView.WeatherType.CLOUDY.ordinal -> R.id.btn_weather_cloudy
            DynamicBackgroundView.WeatherType.CLEAR.ordinal -> R.id.btn_weather_clear
            else -> R.id.btn_weather_rain
        })

        isUpdatingBackgroundUi = false
    }

    private fun applyBackgroundSettings() {
        val blur = bgBlurSeek.value.toInt()
        backgroundManager.setBlurIntensity(blur)

        val dimMode = when (bgDimToggleGroup.checkedButtonId) {
            R.id.off_button -> BackgroundManager.DIM_MODE_OFF
            R.id.continuous_button -> BackgroundManager.DIM_MODE_CONTINUOUS
            R.id.dynamic_button -> BackgroundManager.DIM_MODE_DYNAMIC
            else -> BackgroundManager.DIM_MODE_OFF
        }
        backgroundManager.setDimMode(dimMode)

        bgNightShiftSwitch?.let { backgroundManager.setNightShiftEnabled(it.isChecked) }
        bgZoomSwitch?.let { backgroundManager.setZoomEnabled(it.isChecked) }
        bgMusicAlbumArtSwitch?.let { backgroundManager.setMusicAlbumArtEnabled(it.isChecked) }

        backgroundManager.setWeatherEffectsEnabled(bgWeatherSwitch.isChecked)
        backgroundManager.setManualWeatherEnabled(bgManualWeatherSwitch.isChecked)
        backgroundManager.setManualWeatherIntensity(bgIntensitySeek.value.toInt())
        backgroundManager.setDimIntensity(bgDimSeek.value.toInt())

        backgroundManager.setDimMinIntensity(bgDimRangeSeek.values[0].toInt())
        backgroundManager.setDimMaxIntensity(bgDimRangeSeek.values[1].toInt())
        backgroundManager.setManualWeatherType(when (bgWeatherToggleGroup.checkedButtonId) {
            R.id.btn_weather_clear -> DynamicBackgroundView.WeatherType.CLEAR.ordinal
            R.id.btn_weather_cloudy -> DynamicBackgroundView.WeatherType.CLOUDY.ordinal
            R.id.btn_weather_rain -> DynamicBackgroundView.WeatherType.RAIN.ordinal
            R.id.btn_weather_snow -> DynamicBackgroundView.WeatherType.SNOW.ordinal
            R.id.btn_weather_fog -> DynamicBackgroundView.WeatherType.FOG.ordinal
            R.id.btn_weather_thunder -> DynamicBackgroundView.WeatherType.THUNDERSTORM.ordinal
            else -> DynamicBackgroundView.WeatherType.RAIN.ordinal
        })

        onApplyCompleted(previewBackgroundUri, blur)
        hide()
    }

    private fun getPreviewDimMode(): Int {
        return when (bgDimToggleGroup.checkedButtonId) {
            R.id.off_button -> BackgroundManager.DIM_MODE_OFF
            R.id.continuous_button -> BackgroundManager.DIM_MODE_CONTINUOUS
            R.id.dynamic_button -> BackgroundManager.DIM_MODE_DYNAMIC
            else -> BackgroundManager.DIM_MODE_OFF
        }
    }

    private fun getPreviewDimIntensity(): Int {
        return bgDimSeek.value.toInt()
    }

    private fun getPreviewDimMin(): Int {
        return if (getPreviewDimMode() == BackgroundManager.DIM_MODE_DYNAMIC) {
            bgDimRangeSeek.values[0].toInt()
        } else {
            0
        }
    }

    private fun getPreviewDimMax(): Int {
        return if (getPreviewDimMode() == BackgroundManager.DIM_MODE_DYNAMIC) {
            bgDimRangeSeek.values[1].toInt()
        } else {
            bgDimSeek.value.toInt()
        }
    }

    fun applyWeatherPreview() {
        val isNight = !dayTimeGetter.isDay()
        val dayFactor = dayTimeGetter.getDayFactor()

        if (!bgWeatherSwitch.isChecked) {
            weatherView.forceWeather(DynamicBackgroundView.WeatherType.NONE, 0f, 0f, isNight, dayFactor)
            triggerFilterUpdate()
            return
        }

        if (bgManualWeatherSwitch.isChecked) {
            val typeOrdinal = when (bgWeatherToggleGroup.checkedButtonId) {
                R.id.btn_weather_clear -> DynamicBackgroundView.WeatherType.CLEAR.ordinal
                R.id.btn_weather_cloudy -> DynamicBackgroundView.WeatherType.CLOUDY.ordinal
                R.id.btn_weather_rain -> DynamicBackgroundView.WeatherType.RAIN.ordinal
                R.id.btn_weather_snow -> DynamicBackgroundView.WeatherType.SNOW.ordinal
                R.id.btn_weather_fog -> DynamicBackgroundView.WeatherType.FOG.ordinal
                R.id.btn_weather_thunder -> DynamicBackgroundView.WeatherType.THUNDERSTORM.ordinal
                else -> backgroundManager.getManualWeatherType()
            }
            val type = DynamicBackgroundView.WeatherType.values().getOrElse(typeOrdinal) { DynamicBackgroundView.WeatherType.CLEAR }
            val floatIntensity = bgIntensitySeek.value / 100f
            weatherView.forceWeather(type, floatIntensity, floatIntensity * 1.5f, isNight, dayFactor)
        } else {
            val prefs = weatherView.context.getSharedPreferences("ClockDeskPrefs", android.content.Context.MODE_PRIVATE)
            val windUnit = prefs.getString("wind_speed_unit", "kmh") ?: "kmh"
            val precipUnit = prefs.getString("precipitation_unit", "mm") ?: "mm"
            weatherView.updateFromOpenMeteoSmart(
                weatherGetter.weatherCode ?: 0,
                weatherGetter.windSpeed ?: 0.0,
                isNight,
                weatherGetter.precipitation,
                weatherGetter.cloudCover,
                weatherGetter.visibility,
                windUnit,
                precipUnit,
                dayFactor
            )
        }
        triggerFilterUpdate()
    }

    private fun updateAutoWeatherCard() {
        val code = weatherGetter.weatherCode
        val temp = weatherGetter.temperature

        if (code == null) {
            bgAutoWeatherIcon.text = "🌡️"
            bgAutoWeatherDetail.text = floatingMenuView.context.getString(R.string.weather_auto_no_data)
            bgAutoWeatherTemp.text = ""
            return
        }

        val (emoji, label) = when (code) {
            0    -> "☀️"  to floatingMenuView.context.getString(R.string.weather_clear)
            1    -> "🌤️" to floatingMenuView.context.getString(R.string.weather_clear)
            2    -> "⛅"  to floatingMenuView.context.getString(R.string.weather_cloudy)
            3    -> "☁️"  to floatingMenuView.context.getString(R.string.weather_cloudy)
            45, 48 -> "🌫️" to floatingMenuView.context.getString(R.string.weather_fog)
            51, 53, 55, 56, 57 -> "🌦️" to floatingMenuView.context.getString(R.string.weather_rain)
            61, 63, 65, 80, 81, 82 -> "🌧️" to floatingMenuView.context.getString(R.string.weather_rain)
            71, 73, 75, 77, 85, 86 -> "❄️" to floatingMenuView.context.getString(R.string.weather_snow)
            95, 96, 99 -> "⛈️" to floatingMenuView.context.getString(R.string.weather_storm)
            else -> "🌡️" to "WMO $code"
        }

        bgAutoWeatherIcon.text = emoji
        bgAutoWeatherDetail.text = label
        bgAutoWeatherTemp.text = if (temp != null) "%.0f°C".format(temp) else ""
    }

    private fun updateAdapterItems() {
        val items = mutableListOf<String>().apply {
            add("__DEFAULT_GRADIENT__")
            addAll(backgroundManager.getSavedUriSet())
            add("__ADD__")
        }
        backgroundsAdapter?.updateItems(items)
    }

    fun updateCropButtonVisibility(hasCustomBackground: Boolean) {
        bgCropBtn?.visibility = if (hasCustomBackground) View.VISIBLE else View.GONE
    }

    private fun scaleDownMainLayout() {
        val metrics = floatingMenuView.resources.displayMetrics
        val targetScale = 0.77f
        val translationY = -(metrics.heightPixels * 0.17f)

        mainLayout.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .translationY(translationY)
            .translationX(0f)
            .setDuration(animationDuration)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun restoreMainLayoutState() {
        mainLayout.animate()
            .scaleX(0.90f)
            .scaleY(0.90f)
            .translationY(0f)
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
        previewTask?.let { floatingMenuView.removeCallbacks(it) }
        filterTask?.let { floatingMenuView.removeCallbacks(it) }
        blurTask?.let { floatingMenuView.removeCallbacks(it) }

        bgRecycler.adapter = null
        backgroundsAdapter = null
    }
}