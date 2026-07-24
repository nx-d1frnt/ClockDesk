package com.nxd1frnt.clockdesk2.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.sidesheet.SideSheetBehavior
import com.google.android.material.sidesheet.SideSheetCallback
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.background.BackgroundCropController
import com.nxd1frnt.clockdesk2.background.BackgroundManager
import com.nxd1frnt.clockdesk2.background.BlurTransformation
import com.nxd1frnt.clockdesk2.background.GradientManager
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import com.nxd1frnt.clockdesk2.daytimegetter.SunriseAPI
import com.nxd1frnt.clockdesk2.music.MusicPluginManager
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.music.PluginState
import com.nxd1frnt.clockdesk2.network.GlideApp
import com.nxd1frnt.clockdesk2.smartchips.SmartChipManager
import com.nxd1frnt.clockdesk2.smartchips.plugins.BackgroundProgressPlugin
import com.nxd1frnt.clockdesk2.ui.settings.BackgroundSheetManager
import com.nxd1frnt.clockdesk2.ui.settings.SettingsActivity
import com.nxd1frnt.clockdesk2.ui.view.DynamicBackgroundView
import com.nxd1frnt.clockdesk2.ui.view.PerformanceOverlayView
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceView
import com.nxd1frnt.clockdesk2.utils.BurnInProtectionManager
import com.nxd1frnt.clockdesk2.utils.ClockManager
import com.nxd1frnt.clockdesk2.utils.ColorExtractor
import com.nxd1frnt.clockdesk2.utils.EntranceAnimationManager
import com.nxd1frnt.clockdesk2.utils.FontManager
import com.nxd1frnt.clockdesk2.utils.GeocodingHelper
import com.nxd1frnt.clockdesk2.utils.LocationManager
import com.nxd1frnt.clockdesk2.utils.Logger
import com.nxd1frnt.clockdesk2.utils.PowerSaveObserver
import com.nxd1frnt.clockdesk2.utils.PowerStateManager
import com.nxd1frnt.clockdesk2.utils.SmartPixelManager
import com.nxd1frnt.clockdesk2.utils.calculateWeatherIntensity
import com.nxd1frnt.clockdesk2.utils.getWeatherMatrix
import com.nxd1frnt.clockdesk2.weathergetter.OpenMeteoAPI
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter

class MainActivity : AppCompatActivity(), PowerSaveObserver {
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherText: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherLayout: LinearLayout
    private lateinit var lastfmLayout: LinearLayout
    private lateinit var lastfmIcon: ImageView
    private lateinit var nowPlayingTextView: TextView
    private lateinit var backgroundLayout: LinearLayout
    private lateinit var backgroundImageView: ImageView
    private lateinit var dynamicBackgroundView: DynamicBackgroundView
    private lateinit var turbulenceOverlay: TurbulenceView
    private lateinit var performanceOverlay: PerformanceOverlayView
    private lateinit var settingsButton: Button
    private lateinit var debugButton: Button
    private lateinit var backgroundButton: Button
    private lateinit var backgroundCustomizationTab: FloatingActionButton
    private lateinit var mainLayout: ConstraintLayout
    private lateinit var editModeBlurLayer: ImageView

    // UI Elements
    private lateinit var sideSheet: LinearLayout
    private lateinit var sideSheetBehavior: SideSheetBehavior<LinearLayout>
    private lateinit var backgroundBottomSheet: View

    private lateinit var tutorialLayout: ConstraintLayout
    private lateinit var tutorialFinger: ImageView
    private lateinit var tutorialText: TextView
    private lateinit var smartPixelManager: SmartPixelManager
    private lateinit var smartPixelOverlay: View
    private lateinit var customizationSheetManager: CustomizationSheetManager
    private lateinit var backgroundSheetManager: BackgroundSheetManager
    private lateinit var tutorialManager: TutorialManager

    // Core Managers
    private lateinit var clockManager: ClockManager
    private lateinit var gradientManager: GradientManager
    private lateinit var fontManager: FontManager
    private lateinit var locationManager: LocationManager
    private lateinit var weatherGetter: WeatherGetter
    private lateinit var dayTimeGetter: DayTimeGetter
    private lateinit var backgroundManager: BackgroundManager
    private lateinit var cropController: BackgroundCropController

    private lateinit var smartChipManager: SmartChipManager
    private lateinit var chipContainer: ConstraintLayout
    private lateinit var widgetMover: WidgetMover
    private lateinit var burnInProtectionManager: BurnInProtectionManager
    private lateinit var powerStateManager: PowerStateManager
    private lateinit var sensorManager: SensorManager
    private lateinit var nowPlayingExpandableManager: com.nxd1frnt.clockdesk2.music.ui.NowPlayingExpandableManager

    // State Variables
    private var musicManager: MusicPluginManager? = null
    private var currentMusicState: PluginState = PluginState.Idle
    private var lastTrackInfo: String? = null
    private var wasMusicBackgroundApplied = false
    private var currentAppliedArtworkSource: Any? = null
    private var isUpdatingBackgroundUi = false
    private var isEditMode = false
    private var isCropModeActive = false
    private var isScaleAnimating = false
    private var isLaunchingFilePicker = false
    private var isDemoMode = false
    private var isTutorialRunning = false
    private var isNightShiftEnabled = false
    private var isPowerSavingMode = false
    private var isAutoPowerSavingActive = false
    private var isBottomSheetInitializing = false
    private var hasCustomImageBackground = false
    private var previewBackgroundUri: String? = null
    private var lastBackgroundSource: Any? = null
    private var lastBlurIntensity: Int? = null
    private var isAdvancedGraphicsEnabled = false
    private var isGraphicsTransitionsEnabled = true
    private var isGraphicsTurbulenceEnabled = true
    private var isGraphicsEditBlurEnabled = true
    private var graphicsRenderScale = 100
    private var graphicsWeatherScale = 40
    private var enableAdditionalLogging = false
    private var lastIsNight: Boolean? = null

    private var focusedView: View? = null
    private val editModeTimeout = 10000L // 10 seconds
    private val animationDuration = 300L // 300ms
    private val handler = Handler(Looper.getMainLooper())
    private val permissionRequestCode = 100
    private val PICK_BG_REQUEST = 300
    private val PICK_FONT_REQUEST = 400
    private val minPowerSaveBrightness = 0.01f

    private var isResumed = false
    private enum class NightDimState { NORMAL, DIMMED }
    private var currentNightDimState = NightDimState.NORMAL
    private var pendingNightDimState: NightDimState? = null
    private val dimDebounceHandler = Handler(Looper.getMainLooper())
    private var lastSeenLux: Float? = null
    private val dimDebounceRunnable = Runnable {
        pendingNightDimState?.let { targetState ->
            currentNightDimState = targetState
            applyNightDimMode(targetState)
            pendingNightDimState = null
        }
    }

    private var lightSensor: Sensor? = null
    private lateinit var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener
    private var pendingRestoreRunnable: Runnable? = null
    private var pendingBackgroundRestoreRunnable: Runnable? = null
    private lateinit var entranceAnimationManager: EntranceAnimationManager
    private var isWidgetLayoutComplete = false
    private var isBackgroundReady = false
    private var entranceAnimationPlayed = false
    private val ENTRANCE_ANIMATION_TIMEOUT = 2500L
    private val MIN_LOADER_DURATION = 800L
    private var activityStartTime = 0L
    private var showMediaIcon = false

    private val entranceAnimationRunnable = Runnable {
        checkAndPlayEntranceAnimation(force = true)
    }

    private val editModeTimeoutRunnable = Runnable {
        if (sideSheetBehavior.state != SideSheetBehavior.STATE_HIDDEN ||
            (::backgroundSheetManager.isInitialized && backgroundSheetManager.isShowing)
        ) {
            return@Runnable
        }

        if (isEditMode && !isDemoMode) {
            exitEditMode()
        }
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                val lux = event.values[0]
                lastSeenLux = lux

                if (isPowerSavingMode) {
                    val layoutParams = window.attributes
                    val newBrightness = when {
                        lux <= 20 -> minPowerSaveBrightness
                        lux <= 500 -> 0.1f
                        lux <= 5000 -> 0.25f
                        else -> 0.4f
                    }

                    if (layoutParams.screenBrightness != newBrightness) {
                        layoutParams.screenBrightness = newBrightness
                        window.attributes = layoutParams
                    }
                    return
                }

                // Smart Night Dimming logic
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                val brightnessMode = prefs.getString("brightness_mode", "system") ?: "system"
                if (brightnessMode == "smart_night") {
                    val threshold = prefs.getInt("smart_night_lux_threshold", 5)
                    val hysteresis = 3 // 3 lux hysteresis

                    val candidateState = when (currentNightDimState) {
                        NightDimState.NORMAL -> {
                            if (lux < threshold) NightDimState.DIMMED else NightDimState.NORMAL
                        }
                        NightDimState.DIMMED -> {
                            if (lux >= threshold + hysteresis) NightDimState.NORMAL else NightDimState.DIMMED
                        }
                    }

                    if (candidateState != currentNightDimState) {
                        if (candidateState != pendingNightDimState) {
                            dimDebounceHandler.removeCallbacks(dimDebounceRunnable)
                            pendingNightDimState = candidateState
                            dimDebounceHandler.postDelayed(dimDebounceRunnable, 2500L) // 2.5 seconds debounce
                        }
                    } else {
                        // Candidate matches current state; cancel any pending transition to a different state
                        if (pendingNightDimState != null) {
                            dimDebounceHandler.removeCallbacks(dimDebounceRunnable)
                            pendingNightDimState = null
                        }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun checkAndPlayEntranceAnimation(force: Boolean = false) {
        if (entranceAnimationPlayed) return
        if (force || (isWidgetLayoutComplete && isBackgroundReady)) {
            entranceAnimationPlayed = true
            handler.removeCallbacks(entranceAnimationRunnable)

            val timePassed = System.currentTimeMillis() - activityStartTime
            val delay = if (force) 0L else maxOf(0L, MIN_LOADER_DURATION - timePassed)

            mainLayout.postDelayed({
                entranceAnimationManager.play()
                handler.postDelayed({
                    setupMusicSystem()
                    widgetMover.restoreOrderAndPositions()
                }, 850)
            }, delay)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        activityStartTime = System.currentTimeMillis()
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)

        setupWindowFlags()
        setContentView(R.layout.activity_main)

        initViews()

        sideSheetBehavior = SideSheetBehavior.from(sideSheet).apply {
            state = SideSheetBehavior.STATE_HIDDEN
        }

        handler.postDelayed(entranceAnimationRunnable, ENTRANCE_ANIMATION_TIMEOUT)

        initCoreManagers()
        initUIManagers()
        setupListeners()

        val skipAnimation = savedInstanceState != null
        loadSavedBackground(skipAnimation)
        checkForFirstLaunchAnimation()
        setupSideSheet()
        restoreSavedWeatherState()
        startUpdates()
    }

    private fun setupWindowFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun initViews() {
        timeText = findViewById(R.id.time_text)
        dateText = findViewById(R.id.date_text)
        weatherText = findViewById(R.id.weather_text)
        weatherIcon = findViewById(R.id.weather_icon)
        weatherLayout = findViewById(R.id.weather_layout)
        lastfmLayout = findViewById(R.id.lastfm_layout)
        lastfmIcon = findViewById(R.id.lastfm_icon)
        nowPlayingTextView = findViewById(R.id.now_playing_text)
        backgroundLayout = findViewById(R.id.background_layout)
        backgroundImageView = findViewById(R.id.background_image_view)
        dynamicBackgroundView = findViewById(R.id.dynamic_background_view)
        turbulenceOverlay = findViewById(R.id.turbulence_overlay)
        performanceOverlay = findViewById(R.id.performance_overlay)
        settingsButton = findViewById(R.id.settings_button)
        debugButton = findViewById(R.id.demo_button)
        backgroundButton = findViewById(R.id.background_button)
        backgroundCustomizationTab = findViewById(R.id.background_customization_fab)
        mainLayout = findViewById(R.id.main_layout)
        sideSheet = findViewById(R.id.side_sheet)
        backgroundBottomSheet = findViewById(R.id.background_bottom_sheet)
        tutorialLayout = findViewById(R.id.tutorial_overlay_root)
        tutorialFinger = findViewById(R.id.tutorial_finger_icon)
        tutorialText = findViewById(R.id.tutorial_text)
        chipContainer = findViewById(R.id.smart_chip_container)
        smartPixelOverlay = findViewById(R.id.smart_pixel_overlay)
        editModeBlurLayer = findViewById(R.id.edit_mode_blur_layer)

        editModeBlurLayer.setColorFilter(Color.parseColor("#C5000000"), PorterDuff.Mode.SRC_OVER)

        nowPlayingExpandableManager = com.nxd1frnt.clockdesk2.music.ui.NowPlayingExpandableManager(
            activity = this,
            mainLayout = mainLayout,
            lastfmLayout = lastfmLayout,
            timeText = timeText,
            musicManagerProvider = { musicManager },
            fontManagerProvider = { fontManager }
        )

        settingsButton.alpha = 0f
        settingsButton.visibility = View.GONE
        debugButton.alpha = 0f
        debugButton.visibility = View.GONE
        backgroundCustomizationTab.alpha = 0f
        backgroundCustomizationTab.visibility = View.GONE

        entranceAnimationManager = EntranceAnimationManager(
            rootView = mainLayout,
            widgets = listOf(timeText, dateText, chipContainer, lastfmLayout)
        )
        entranceAnimationManager.prepareViews()

        turbulenceOverlay.playAnimation(Color.parseColor("#5A7184")) {}
    }

    private fun initCoreManagers() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        enableAdditionalLogging = prefs.getBoolean("additional_logging", false)
        Logger.isLoggingEnabled = enableAdditionalLogging
        showMediaIcon = prefs.getBoolean("show_media_icon", false)
        isAdvancedGraphicsEnabled = prefs.getBoolean("advanced_graphics", false)
        isGraphicsTransitionsEnabled = prefs.getBoolean("graphics_enable_transitions", true)
        isGraphicsTurbulenceEnabled = prefs.getBoolean("graphics_enable_turbulence", true)
        isGraphicsEditBlurEnabled = prefs.getBoolean("graphics_enable_edit_blur", true)
        graphicsRenderScale = prefs.getInt("graphics_render_scale", 100)
        graphicsWeatherScale = prefs.getInt("graphics_weather_scale", 40)

        Thread {
            val fogBitmap = BitmapFactory.decodeResource(resources, R.drawable.fog)
            val cloudsBitmap = BitmapFactory.decodeResource(resources, R.drawable.clouds)
            handler.post {
                if (!isDestroyed && !isFinishing) {
                    dynamicBackgroundView.setFogTextures(fogBitmap, cloudsBitmap)
                    val targetScale = if (isAdvancedGraphicsEnabled) graphicsRenderScale / 100f else 0.5f
                    dynamicBackgroundView.setRenderScale(targetScale)
                    val targetWeatherScale = if (isAdvancedGraphicsEnabled) graphicsWeatherScale / 100f else 0.4f
                    dynamicBackgroundView.weatherResolutionScale = targetWeatherScale
                }
            }
        }.start()

        backgroundManager = BackgroundManager(this)
        locationManager = LocationManager(this, permissionRequestCode)
        dayTimeGetter = SunriseAPI(this, locationManager)

        weatherGetter = OpenMeteoAPI(this, locationManager) {
            runOnUiThread {
//                if (weatherGetter.temperature != null) {
//                    weatherText.text = "${weatherGetter.temperature}°C"
//                }

                val code = weatherGetter.weatherCode ?: 0
                val wind = weatherGetter.windSpeed ?: 0.0
                val isNight = !dayTimeGetter.isDay()
                val precip = weatherGetter.precipitation
                val clouds = weatherGetter.cloudCover
                val vis = weatherGetter.visibility

                if (backgroundManager.isWeatherEffectsEnabled() && !backgroundManager.isManualWeatherEnabled()) {
                    val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                    val windUnit = prefs.getString("wind_speed_unit", "kmh") ?: "kmh"
                    val precipUnit = prefs.getString("precipitation_unit", "mm") ?: "mm"
                    dynamicBackgroundView.updateFromOpenMeteoSmart(
                        code, wind, isNight,
                        precip, clouds, vis,
                        windUnit, precipUnit
                    )
                }

                if (hasCustomImageBackground) {
                    updateBackgroundFilters()
                }
            }
        }

        gradientManager = GradientManager(
            dynamicBackgroundView,
            dayTimeGetter,
            locationManager,
            handler,
            isCustomBackgroundActive = {
                hasCustomImageBackground || (::backgroundSheetManager.isInitialized && 
                        backgroundSheetManager.isShowing && 
                        backgroundSheetManager.previewBackgroundUri != null && 
                        backgroundSheetManager.previewBackgroundUri != "__DEFAULT_GRADIENT__")
            }
        )

        fontManager = FontManager(
            this,
            timeText,
            dateText,
            lastfmLayout,
            nowPlayingTextView,
            lastfmIcon,
            weatherText,
            weatherIcon,
            chipContainer,
            enableAdditionalLogging
        )

        fontManager.onApplyToViewListener = { viewId ->
            if (viewId == R.id.time_text && ::nowPlayingExpandableManager.isInitialized && nowPlayingExpandableManager.isExpanded) {
                nowPlayingExpandableManager.reapplyScale()
            }
        }

        smartChipManager = SmartChipManager(
            this,
            chipContainer,
            prefs,
            fontManager
        )
        lifecycle.addObserver(smartChipManager)

        clockManager = ClockManager(
            timeText,
            dateText,
            handler,
            fontManager,
            dayTimeGetter,
            locationManager,
            { _, _, _ ->
                if (isDemoMode) {
                    Logger.d("MainActivity") { "debug sun times callback (demo mode)" }
                }
            },
            { currentTime ->
                try {
                    gradientManager.updateSimulatedTime(currentTime)
                } catch (e: Exception) {
                    Logger.w("MainActivity") { "Failed to update gradient simulated time: ${e.message}" }
                }

                val isNight = !dayTimeGetter.isDay()
                if (lastIsNight == null || lastIsNight != isNight) {
                    lastIsNight = isNight
                    if (::backgroundSheetManager.isInitialized && backgroundSheetManager.isShowing) {
                        backgroundSheetManager.applyWeatherPreview()
                    } else {
                        restoreSavedWeatherState()
                    }
                    updateSensorRegistration()
                    if (!isNight) {
                        applyNightDimMode(NightDimState.NORMAL)
                    }
                }

                try {
                    if (dynamicBackgroundView.visibility == View.VISIBLE) {
                        val mode = backgroundManager.getDimMode()
                        if (mode == BackgroundManager.Companion.DIM_MODE_DYNAMIC || backgroundManager.isNightShiftEnabled()) {
                            updateBackgroundFilters()
                        }
                    }
                } catch (e: Exception) {
                    Logger.w("MainActivity") { "Failed to update dynamic dimming: ${e.message}" }
                }
            },
            prefs,
            enableAdditionalLogging
        )

        burnInProtectionManager = BurnInProtectionManager(listOf(timeText, dateText, lastfmLayout, chipContainer))

        widgetMover = WidgetMover(this, listOf(lastfmLayout, dateText, timeText), mainLayout)

        widgetMover.onInitialLayoutComplete = {
            isWidgetLayoutComplete = true
            checkAndPlayEntranceAnimation()
        }

        smartPixelManager = SmartPixelManager(this, smartPixelOverlay, timeoutMs = 10000L)
        if (prefs.getBoolean("smart_pixels_enabled", false)) smartPixelManager.start()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        powerStateManager = PowerStateManager(this)
        powerStateManager.registerObserver(this)
        powerStateManager.registerObserver(clockManager)
        powerStateManager.registerObserver(weatherGetter)

        fontManager.loadFont()
        setupPreferencesListener(prefs)
    }

    private fun initUIManagers() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)

        // 1. Customization Sheet
        customizationSheetManager = CustomizationSheetManager(
            sideSheetView = sideSheet,
            mainLayout = mainLayout,
            backgroundCustomizationTab = backgroundCustomizationTab,
            fontManager = fontManager,
            widgetMover = widgetMover,
            clockManager = clockManager,
            dayTimeGetter = dayTimeGetter,
            onAddFontRequested = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf"))
                    }
                } else {
                    Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                }
                try {
                    isLaunchingFilePicker = true
                    startActivityForResult(intent, PICK_FONT_REQUEST)
                } catch (e: ActivityNotFoundException) { /* Handle error */ }
            },
            onSheetStateChanged = { isHidden ->
                if (isHidden) {
                    resetEditModeTimeout()
                } else {
                    stopHideUiTimer()
                }
            }
        )

        // 2. Background Sheet
        backgroundSheetManager = BackgroundSheetManager(
            floatingMenuView = backgroundBottomSheet,
            mainLayout = mainLayout,
            backgroundCustomizationTab = backgroundCustomizationTab,
            backgroundManager = backgroundManager,
            dayTimeGetter = dayTimeGetter,
            weatherGetter = weatherGetter,
            weatherView = dynamicBackgroundView,
            isMusicBackgroundApplied = { wasMusicBackgroundApplied },
            onAddBackgroundRequested = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                } else {
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                }
                try {
                    isLaunchingFilePicker = true
                    startActivityForResult(intent, PICK_BG_REQUEST)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_SHORT).show()
                }
            },
            onPreviewImage = { uri, blur ->
                applyImageBackground(uri, blur)
            },
            onRestoreGradient = {
                restoreGradientBackground()
                restoreSavedWeatherState()
            },
            onRestoreSavedBackground = {
                restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                restoreSavedWeatherState()
            },
            onUpdateFilters = { previewMode, previewIntensity, previewMin, previewMax ->
                if (dynamicBackgroundView.visibility == View.VISIBLE) {
                    updateBackgroundFilters(previewMode, previewIntensity, previewMin, previewMax)
                }
            },
            onApplyCompleted = { previewUri ->
                if (wasMusicBackgroundApplied) {
                    when (previewUri) {
                        "__DEFAULT_GRADIENT__" -> {
                            backgroundManager.setSavedBackgroundUri(null)
                            fontManager.clearDynamicColors()
                            fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, true)
                        }
                        null -> { }
                        else -> backgroundManager.setSavedBackgroundUri(previewUri)
                    }
                    restoreSavedWeatherState()
                    Toast.makeText(this, getString(R.string.settings_saved_music_active), Toast.LENGTH_LONG).show()
                    return@BackgroundSheetManager
                }

                val intensity = backgroundManager.getBlurIntensity()
                when (previewUri) {
                    "__DEFAULT_GRADIENT__" -> {
                        backgroundManager.setSavedBackgroundUri(null)
                        setCustomBackground(false)
                        fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, true)
                        dynamicBackgroundView.visibility = View.VISIBLE
                        backgroundImageView.visibility = View.GONE
                        backgroundManager.clearDim()
                        gradientManager.startUpdates()
                    }
                    null -> {
                        backgroundManager.getSavedBackgroundUri()?.let {
                            try {
                                applyImageBackground(Uri.parse(it), intensity)
                                setCustomBackground(true)
                                updateBackgroundFilters()
                            } catch (e: Exception) {
                                Logger.w("MainActivity") { "Failed to re-apply existing background" }
                            }
                        }
                    }
                    else -> {
                        backgroundManager.setSavedBackgroundUri(previewUri)
                        try {
                            applyImageBackground(Uri.parse(previewUri), intensity)
                            setCustomBackground(true)
                            updateBackgroundFilters()
                        } catch (e: Exception) {
                            Logger.w("MainActivity") { "Failed to apply new background" }
                        }
                    }
                }
                restoreSavedWeatherState()
            },
            onClearBackground = {
                backgroundManager.setSavedBackgroundUri(null)
                setCustomBackground(false)
                backgroundImageView.setImageDrawable(null)
                fontManager.clearDynamicColors()
                fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try { backgroundImageView.setRenderEffect(null) } catch (_: Throwable) {}
                }
                backgroundImageView.visibility = View.GONE
                dynamicBackgroundView.visibility = View.VISIBLE
                backgroundManager.clearDim()
                gradientManager.startUpdates()
                lastBackgroundSource = null
                lastBlurIntensity = null
                restoreSavedWeatherState()
            },
            onSheetStateChanged = { isHidden ->
                if (isHidden) resetEditModeTimeout() else stopHideUiTimer()
            },
            onCropRequested = {
                isCropModeActive = true
                backgroundSheetManager.hide()
                cropController.enter()
                stopHideUiTimer()
            }
        )

        val overlayView = findViewById<View>(R.id.crop_overlay)
        cropController = BackgroundCropController(
            dynamicBackgroundView = dynamicBackgroundView,
            overlayRoot = overlayView,
            backgroundManager = backgroundManager,
            onApply = {
                isCropModeActive = false
                if (dynamicBackgroundView.visibility == View.VISIBLE) {
                    updateBackgroundFilters()
                }
                resetEditModeTimeout()
            },
            onCancel = {
                isCropModeActive = false
                resetEditModeTimeout()
            }
        )

    // 3. Tutorial Manager
        tutorialManager = TutorialManager(
            tutorialLayout = tutorialLayout,
            tutorialFinger = tutorialFinger,
            tutorialText = tutorialText,
            mainLayout = mainLayout,
            timeText = timeText,
            prefs = prefs,
            toggleEditModeAction = { toggleEditMode() },
            showCustomizationAction = { view -> customizationSheetManager.showForView(view) },
            hideBottomSheetAction = { customizationSheetManager.hide() },
            requestLocationPermissionAction = {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    permissionRequestCode
                )
            },
            onTutorialFinished = { checkLocationPermissionsAndLoadData() }
        )

        sideSheet.bringToFront()
        backgroundBottomSheet.bringToFront()
    }

    private fun setupListeners() {
        mainLayout.setOnLongClickListener { toggleEditMode(); true }

        widgetMover.setOnInteractionListener { isInteracting ->
            if (isInteracting) {
                handler.removeCallbacks(editModeTimeoutRunnable)
            } else {
                if (isEditMode) {
                    resetEditModeTimeout()
                }
            }
        }

        timeText.setOnClickListener {
            if (isEditMode) {
                customizationSheetManager.showForView(it)
                resetEditModeTimeout()
            }
        }
        dateText.setOnClickListener {
            if (isEditMode) {
                customizationSheetManager.showForView(it)
                resetEditModeTimeout()
            }
        }
        lastfmLayout.setOnClickListener {
            if (isEditMode) {
                customizationSheetManager.showForView(it)
                resetEditModeTimeout()
            } else {
                nowPlayingExpandableManager.toggle(currentMusicState)
            }
        }
        chipContainer.setOnClickListener {
            if (isEditMode) {
                customizationSheetManager.showForView(it)
                resetEditModeTimeout()
            }
        }
        weatherLayout.setOnClickListener {
            if (isEditMode) {
                customizationSheetManager.showForView(it)
                resetEditModeTimeout()
            } else {
                com.nxd1frnt.clockdesk2.smartchips.ui.WeatherDialog.show(this)
            }
        }

        backgroundCustomizationTab.setOnClickListener {
            backgroundSheetManager.show()
            if (!isDemoMode) {
                handler.removeCallbacks(editModeTimeoutRunnable)
                handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
            }
        }

        debugButton.setOnClickListener {
            isDemoMode = !isDemoMode
            clockManager.toggleDebugMode(isDemoMode)
            gradientManager.toggleDebugMode(isDemoMode)

            if (hasCustomImageBackground) {
                gradientManager.stopUpdates()
                val uriStr = backgroundManager.getSavedBackgroundUri()
                uriStr?.let {
                    try {
                        val uri = Uri.parse(it)
                        applyImageBackground(uri, backgroundManager.getBlurIntensity())
                    } catch (e: Exception) {
                        Logger.w("MainActivity"){"Failed to reapply custom background: ${e.message}"}
                    }
                }
            }

            if (!isDemoMode) {
                handler.removeCallbacks(editModeTimeoutRunnable)
                handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
            }
        }

//        debugButton.setOnLongClickListener {
//            throw RuntimeException("Test crash triggered by user long-pressing the Demo button.")
//        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            exitEditMode()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::nowPlayingExpandableManager.isInitialized && nowPlayingExpandableManager.isExpanded) {
                    nowPlayingExpandableManager.collapse()
                    return
                }

                if (::backgroundSheetManager.isInitialized && backgroundSheetManager.isShowing) {
                    backgroundSheetManager.cancelAndHide()
                    return
                }

                if (sideSheetBehavior.state != SideSheetBehavior.STATE_HIDDEN) {
                    fontManager.loadFont()
                    widgetMover.restoreOrderAndPositions()
                    customizationSheetManager.hide()
                    return
                }

                if (tutorialManager.handleBackPressed()) {
                    return
                }

                if (isEditMode) {
                    exitEditMode()
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    private fun setupPreferencesListener(prefs: SharedPreferences) {
        preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val chipKeys = setOf("show_battery_alert") + smartChipManager.externalPlugins.map { it.preferenceKey }
            if (chipKeys.contains(key)) smartChipManager.onPreferencesChanged()

            when (key) {
                "show_performance_overlay" -> runOnUiThread {
                    val showOverlay = prefs.getBoolean("show_performance_overlay", false)
                    togglePerformanceOverlay(showOverlay)
                }
                "automatic_battery_saver_mode", "battery_saver_trigger", "power_saver_manual", "power_saver_sync_system",
                "battery_alert_show_low", "battery_alert_low_threshold", "battery_alert_show_charging",
                "battery_alert_show_full", "battery_alert_show_saver",
                "weather_alert_enable_storms", "weather_alert_enable_wind", "weather_alert_enable_worsening",
                "weather_alert_enable_uv", "weather_alert_wind_threshold", "weather_alert_uv_threshold",
                "weather_alert_forecast_hours" -> smartChipManager.onPreferencesChanged()
                "additional_logging" -> {
                    enableAdditionalLogging = prefs.getBoolean("additional_logging", false)
                    Logger.isLoggingEnabled = enableAdditionalLogging
                }
                "graphics_render_scale" -> runOnUiThread {
                    graphicsRenderScale = prefs.getInt("graphics_render_scale", 100)
                    if (::dynamicBackgroundView.isInitialized) {
                        val targetScale = if (isAdvancedGraphicsEnabled) graphicsRenderScale / 100f else 0.5f
                        dynamicBackgroundView.setRenderScale(targetScale)
                    }
                }
                "graphics_weather_scale" -> runOnUiThread {
                    graphicsWeatherScale = prefs.getInt("graphics_weather_scale", 40)
                    if (::dynamicBackgroundView.isInitialized) {
                        val targetWeatherScale = if (isAdvancedGraphicsEnabled) graphicsWeatherScale / 100f else 0.4f
                        dynamicBackgroundView.weatherResolutionScale = targetWeatherScale
                    }
                }
                "lastfm_albumart_background" -> runOnUiThread { handleMusicStateUpdate(currentMusicState) }
                "show_media_icon" -> runOnUiThread {
                    showMediaIcon = prefs.getBoolean("show_media_icon", false)
                    if (!showMediaIcon) {
                        lastfmIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.music_note))
                    } else {
                        updateSourceIcon(MusicTrack(
                            title = "",
                            artist = "",
                            album = "",
                            sourcePackageName = (currentMusicState as? PluginState.Playing)?.track?.sourcePackageName,
                            sourceIconBitmap = (currentMusicState as? PluginState.Playing)?.track?.sourceIconBitmap
                        ))
                    }
                    handleMusicStateUpdate(currentMusicState)
                }
                "brightness_mode" -> runOnUiThread {
                    updateSensorRegistration()
                    val mode = prefs.getString("brightness_mode", "system") ?: "system"
                    if (mode == "system") {
                        cancelPendingDimTransition()
                        currentNightDimState = NightDimState.NORMAL
                        applyNightDimMode(NightDimState.NORMAL)
                    } else {
                        reEvaluateSmartNightDimming()
                    }
                }
                "smart_night_min_brightness" -> runOnUiThread {
                    if (!isPowerSavingMode && prefs.getString("brightness_mode", "system") == "smart_night" && currentNightDimState == NightDimState.DIMMED) {
                        val minBrightPct = prefs.getInt("smart_night_min_brightness", 5) / 100f
                        applyBrightnessOverride(minBrightPct.coerceIn(0.01f, 1.0f))
                    }
                }
                "smart_night_lux_threshold" -> runOnUiThread {
                    if (!isPowerSavingMode && prefs.getString("brightness_mode", "system") == "smart_night") {
                        reEvaluateSmartNightDimming()
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (::smartPixelManager.isInitialized) {
            smartPixelManager.onUserInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }


    private fun setCustomBackground(hasCustom: Boolean) {
        hasCustomImageBackground = hasCustom
        if (::backgroundSheetManager.isInitialized) {
            backgroundSheetManager.updateCropButtonVisibility(hasCustom)
        }
        if (!hasCustom) {
            lastBackgroundSource = null
            lastBlurIntensity = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        weatherGetter.stopUpdates()
        musicManager?.destroy()
        pendingRestoreRunnable?.let { handler.removeCallbacks(it) }
        if (::backgroundSheetManager.isInitialized) backgroundSheetManager.onDestroy()
        getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        sensorManager.unregisterListener(sensorEventListener)
    }

    private fun setupMusicSystem() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        musicManager = MusicPluginManager(this, prefs) { state ->
            runOnUiThread {
                handleMusicStateUpdate(state)
            }
        }
    }

    private fun updateSourceIcon(track: MusicTrack) {
        if (!showMediaIcon) {
            lastfmIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.music_note))
            val tintColor = fontManager.getFinalColorForView(R.id.lastfm_layout)
            lastfmIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            lastfmIcon.visibility = View.VISIBLE
            return
        }

        val duration = 300L
//        lastfmIcon.animate()
//            .alpha(0f)
//            .setDuration(duration)
//            .withEndAction {
//                // if (isDestroyed || isFinishing) return@withEndAction

                var iconApplied = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    lastfmIcon.clipToOutline = false
                }

                if (track.sourceIconBitmap != null) {
                    lastfmIcon.setImageBitmap(track.sourceIconBitmap)

                    val tintColor = fontManager.getFinalColorForView(R.id.lastfm_layout)
                    lastfmIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        lastfmIcon.imageAlpha = 255
                    }
                    iconApplied = true
                } else if (!track.sourcePackageName.isNullOrEmpty()) {
                    try {
                        val icon = packageManager.getApplicationIcon(track.sourcePackageName)
                        var monochromeDrawable: Drawable? = null

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && icon is android.graphics.drawable.AdaptiveIconDrawable) {
                            monochromeDrawable = icon.monochrome
                        }

                        if (monochromeDrawable != null) {
                            lastfmIcon.setImageDrawable(monochromeDrawable)

                            val tintColor = fontManager.getFinalColorForView(R.id.lastfm_layout)
                            lastfmIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                lastfmIcon.imageAlpha = 255
                            }
                        } else {
                            lastfmIcon.setImageDrawable(icon)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                lastfmIcon.outlineProvider = object : ViewOutlineProvider() {
                                    override fun getOutline(view: View, outline: Outline) {
                                        outline.setOval(0, 0, view.width, view.height)
                                    }
                                }
                                lastfmIcon.clipToOutline = true
                            }

                            val matrix = ColorMatrix().apply { setSaturation(0f) }
                            lastfmIcon.colorFilter = ColorMatrixColorFilter(matrix)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                lastfmIcon.imageAlpha = 200
                            }
                        }
                        iconApplied = true
                    } catch (e: PackageManager.NameNotFoundException) {
                        Logger.w("MainActivity") { "Couldn't find icon for package: ${track.sourcePackageName}" }
                    }
                }

                if (!iconApplied) {
                    val tintColor = fontManager.getFinalColorForView(R.id.lastfm_layout)
                    lastfmIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                    lastfmIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.music_note))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        lastfmIcon.imageAlpha = 255
                    }
                }

//                lastfmIcon.animate()
//                    .alpha(1f)
//                    .setDuration(duration)
//                    .start()
            //}.start()
    }

    private fun areArtworkSourcesEqual(source1: Any?, source2: Any?): Boolean {
        if (source1 === source2) return true
        if (source1 == null || source2 == null) return false

        if (source1 is String && source2 is String) {
            return source1 == source2
        }
        if (source1 is Uri && source2 is Uri) {
            return source1 == source2
        }

        if (source1 is Bitmap && source2 is Bitmap) {
            if (source1.isRecycled || source2.isRecycled) return false
            if (source1.width != source2.width || source1.height != source2.height) return false
            return runCatching { source1.sameAs(source2) }.getOrDefault(false)
        }

        return false
    }

    private fun handleMusicStateUpdate(state: PluginState) {
        currentMusicState = state
        if (isEditMode) {
            if (state is PluginState.Playing) {
                val track = state.track
                val trackInfoText = "${track.artist} - ${track.title}"
                nowPlayingTextView.text = trackInfoText
                val isTextDifferent = trackInfoText != lastTrackInfo
                lastTrackInfo = trackInfoText

                val validBitmap = track.artworkBitmap != null && !track.artworkBitmap.isRecycled
                val newArtSource: Any? = if (validBitmap) track.artworkBitmap else if (!track.artworkUrl.isNullOrEmpty()) track.artworkUrl else null
                val isArtChanged = !areArtworkSourcesEqual(newArtSource, currentAppliedArtworkSource)

                if (isTextDifferent || isArtChanged || (!wasMusicBackgroundApplied && newArtSource != null)) {
                    handleBackgroundUpdate(track)
                }
                if (isTextDifferent) {
                    updateSourceIcon(track)
                }
            } else {
                nowPlayingTextView.text = getString(R.string.now_playing_placeholder)
                lastTrackInfo = null
            }
            return
        }

        if (::nowPlayingExpandableManager.isInitialized) {
            nowPlayingExpandableManager.updateContent(state)
        }

        when (state) {
            is PluginState.Playing -> {
                pendingRestoreRunnable?.let { handler.removeCallbacks(it) }
                pendingRestoreRunnable = null

                val track = state.track
                val trackInfoText = "${track.artist} - ${track.title}"
                val isTextDifferent = trackInfoText != lastTrackInfo

                val validBitmap = track.artworkBitmap != null && !track.artworkBitmap.isRecycled
                val newArtSource: Any? = if (validBitmap) track.artworkBitmap else if (!track.artworkUrl.isNullOrEmpty()) track.artworkUrl else null
                val isArtChanged = !areArtworkSourcesEqual(newArtSource, currentAppliedArtworkSource)

                if (isTextDifferent || isArtChanged || (!wasMusicBackgroundApplied && newArtSource != null)) {
                    handleBackgroundUpdate(track)
                }

                if (isTextDifferent) {
                    lastTrackInfo = trackInfoText

                    if (nowPlayingExpandableManager.isExpanded) {
                        nowPlayingTextView.text = trackInfoText
                        updateSourceIcon(track)
                    } else {
                        lastfmLayout.animate().cancel()
                        updateSourceIcon(track)

                        if (lastfmLayout.visibility != View.VISIBLE || lastfmLayout.alpha < 1f) {
                            lastfmLayout.visibility = View.VISIBLE
                            lastfmLayout.alpha = 0f
                            lastfmLayout.translationX = 10f
                            nowPlayingTextView.text = trackInfoText
                            nowPlayingTextView.isSelected = true

                            lastfmLayout.animate()
                                .alpha(1f)
                                .translationX(0f)
                                .setDuration(500)
                                .setListener(null)
                                .start()
                        } else {
                            lastfmLayout.animate()
                                .alpha(0f)
                                .translationX(-10f)
                                .setDuration(500)
                                .setListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        if (!isEditMode) {
                                            nowPlayingTextView.text = trackInfoText
                                            nowPlayingTextView.isSelected = true
                                            lastfmLayout.animate()
                                                .alpha(1f)
                                                .translationX(0f)
                                                .setDuration(500)
                                                .setListener(null)
                                                .start()
                                        }
                                    }
                                })
                                .start()
                        }
                    }
                }
            }

            is PluginState.Idle, is PluginState.Disabled -> {
                if (pendingRestoreRunnable == null) {
                    val runnable = Runnable {
                        performMusicIdleState()
                        pendingRestoreRunnable = null
                    }
                    pendingRestoreRunnable = runnable
                    handler.postDelayed(runnable, 800) // 800ms delay
                }
            }
        }
    }

    private fun performMusicIdleState() {
        pendingBackgroundRestoreRunnable?.let { handler.removeCallbacks(it) }
        pendingBackgroundRestoreRunnable = null

        lastfmLayout.animate().cancel()

        if (!isEditMode && lastfmLayout.visibility == View.VISIBLE) {
            lastfmLayout.animate()
                .alpha(0f)
                .translationX(10f)
                .setDuration(500)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isEditMode) {
                            lastfmLayout.visibility = View.GONE
                        }
                    }
                })
                .start()
        } else if (isEditMode) {
            lastfmLayout.visibility = View.VISIBLE
            lastfmLayout.alpha = 1f
        }

        if (wasMusicBackgroundApplied) {
            restoreUserBackground(backgroundManager.getSavedBackgroundUri())
            wasMusicBackgroundApplied = false
            currentAppliedArtworkSource = null
        }
        lastTrackInfo = null
    }

    private fun handleBackgroundUpdate(track: MusicTrack) {
        val blurIntensity = backgroundManager.getBlurIntensity()
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val musicBgEnabled = prefs.getBoolean("lastfm_albumart_background", true)

        if (!musicBgEnabled) {
            pendingBackgroundRestoreRunnable?.let { handler.removeCallbacks(it) }
            pendingBackgroundRestoreRunnable = null
            if (wasMusicBackgroundApplied) {
                restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                wasMusicBackgroundApplied = false
                currentAppliedArtworkSource = null
            }
            return
        }

        val validBitmap = track.artworkBitmap != null && !track.artworkBitmap.isRecycled
        if (validBitmap) {
            pendingBackgroundRestoreRunnable?.let { handler.removeCallbacks(it) }
            pendingBackgroundRestoreRunnable = null

            Logger.d("MainActivity") { "Applying bitmap album art background" }
            applyBitmapBackground(track.artworkBitmap!!, blurIntensity)
            wasMusicBackgroundApplied = true
            currentAppliedArtworkSource = track.artworkBitmap
        } else if (!track.artworkUrl.isNullOrEmpty()) {
            pendingBackgroundRestoreRunnable?.let { handler.removeCallbacks(it) }
            pendingBackgroundRestoreRunnable = null

            Logger.d("MainActivity") { "Applying URL album art background: ${track.artworkUrl}" }
            applyImageBackground(Uri.parse(track.artworkUrl), blurIntensity)
            wasMusicBackgroundApplied = true
            currentAppliedArtworkSource = track.artworkUrl
        } else {
            if (wasMusicBackgroundApplied && pendingBackgroundRestoreRunnable == null) {
                Logger.d("MainActivity") { "No artwork on track, scheduling background restore in 400ms" }
                val runnable = Runnable {
                    pendingBackgroundRestoreRunnable = null
                    if (wasMusicBackgroundApplied && currentMusicState is PluginState.Playing) {
                        val currentTrack = (currentMusicState as PluginState.Playing).track
                        val isStillMissing = currentTrack.artworkBitmap == null && currentTrack.artworkUrl.isNullOrEmpty()
                        if (isStillMissing) {
                            Logger.d("MainActivity") { "Restoring user default background after 400ms grace period" }
                            restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                            wasMusicBackgroundApplied = false
                            currentAppliedArtworkSource = null
                        }
                    }
                }
                pendingBackgroundRestoreRunnable = runnable
                handler.postDelayed(runnable, 400)
            }
        }
    }




    private fun checkForFirstLaunchAnimation() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)
        enableAdditionalLogging = prefs.getBoolean("additional_logging", false)

        if (isFirstLaunch) {
            tutorialManager.start()
        } else {
            checkLocationPermissionsAndLoadData()
        }
    }


    private fun checkLocationPermissionsAndLoadData() {
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasCoarse || hasFine) {
            loadCoordinatesAndFetchData()
        } else {
            val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
            val rationaleShown = prefs.getBoolean("location_permission_rationale_shown", false)
            if (!rationaleShown) {
                showLocationRationaleDialog()
            } else {
                loadCoordinatesAndFetchData()
                showManualLocationOptionDialog()
            }
        }
    }

    private fun restoreSavedWeatherState() {
        val isEnabled = backgroundManager.isWeatherEffectsEnabled()
        val isNight = !dayTimeGetter.isDay()
        lastIsNight = isNight

        if (!isEnabled) {
            dynamicBackgroundView.forceWeather(DynamicBackgroundView.WeatherType.NONE, 0f, 0f, isNight)
            return
        }

        if (backgroundManager.isManualWeatherEnabled()) {
            val typeOrdinal = backgroundManager.getManualWeatherType()
            val type = DynamicBackgroundView.WeatherType.values().getOrElse(typeOrdinal) { DynamicBackgroundView.WeatherType.CLEAR }
            val intensity = backgroundManager.getManualWeatherIntensity() / 100f
            dynamicBackgroundView.forceWeather(type, intensity, intensity * 1.5f, isNight)
        } else {
            val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
            val code = weatherGetter.weatherCode ?: 0
            val wind = weatherGetter.windSpeed ?: 0.0
            val precip = weatherGetter.precipitation
            val clouds = weatherGetter.cloudCover
            val vis = weatherGetter.visibility
            val windUnit = prefs.getString("wind_speed_unit", "kmh") ?: "kmh"
            val precipUnit = prefs.getString("precipitation_unit", "mm") ?: "mm"
            dynamicBackgroundView.updateFromOpenMeteoSmart(
                code, wind, isNight,
                precip, clouds, vis,
                windUnit, precipUnit
            )
        }
    }

    private fun showLocationRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.location_permission_title))
            .setMessage(getString(R.string.location_permission_message))
            .setPositiveButton(getString(R.string.location_permission_grant)) { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    permissionRequestCode
                )
            }
            .setNegativeButton(getString(R.string.location_permission_manual)) { _, _ ->
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean("location_permission_rationale_shown", true).apply()
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNeutralButton(getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean("location_permission_rationale_shown", true).apply()
                loadCoordinatesAndFetchData()
                showManualLocationOptionDialog()
            }
            .setOnCancelListener {
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean("location_permission_rationale_shown", true).apply()
                loadCoordinatesAndFetchData()
                showManualLocationOptionDialog()
            }
            .show()
    }

    private fun showManualLocationOptionDialog() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val promptShown = prefs.getBoolean("manual_location_prompt_shown", false)
        if (promptShown) return

        prefs.edit().putBoolean("manual_location_prompt_shown", true).apply()

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.location_permission_title))
            .setMessage(getString(R.string.location_search_city_prompt))
            .setPositiveButton(getString(android.R.string.yes)) { _, _ ->
                showCitySearchDialog()
            }
            .setNegativeButton(getString(android.R.string.no)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showCitySearchDialog() {
        com.nxd1frnt.clockdesk2.ui.dialog.CitySearchDialog.show(this) { selected ->
            val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
            prefs.edit()
                .putString("location_mode", "city")
                .putString("location_city_name", selected.name)
                .putString("resolved_latitude", selected.latitude.toString())
                .putString("resolved_longitude", selected.longitude.toString())
                .putString("resolved_city_display_name", selected.displayName)
                .apply()

            Toast.makeText(this, getString(R.string.city_resolved_format, selected.displayName, selected.latitude, selected.longitude), Toast.LENGTH_LONG).show()
            loadCoordinatesAndFetchData()
        }
    }

    private fun loadCoordinatesAndFetchData() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val mode = prefs.getString("location_mode", "auto") ?: "auto"
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (mode == "auto" && !hasCoarse && !hasFine) {
            weatherGetter.stopUpdates()
//            runOnUiThread {
//                weatherText.text = getString(R.string.no_location_tap_to_set)
//                weatherIcon.setImageResource(R.drawable.ic_weather_unknown)
//                weatherLayout.visibility = View.VISIBLE
//            }
            // Load coordinates for daytime calculations anyway (using fallback)
            locationManager.loadCoordinates { lat, lon ->
                dayTimeGetter.fetch(lat, lon) {
                    if (!hasCustomImageBackground) gradientManager.updateGradient()
                    if (isNightShiftEnabled) {
                        fontManager.applyNightShiftTransition(
                            clockManager.getCurrentTime(),
                            dayTimeGetter,
                            isNightShiftEnabled
                        )
                    }
                    if (dynamicBackgroundView.visibility == View.VISIBLE) {
                        updateBackgroundFilters()
                    }
                }
            }
            return
        }

        locationManager.loadCoordinates { lat, lon ->
            dayTimeGetter.fetch(lat, lon) {
                if (!hasCustomImageBackground) gradientManager.updateGradient()
                if (isNightShiftEnabled) {
                    fontManager.applyNightShiftTransition(
                        clockManager.getCurrentTime(),
                        dayTimeGetter,
                        isNightShiftEnabled
                    )
                }
                if (dynamicBackgroundView.visibility == View.VISIBLE) {
                    updateBackgroundFilters()
                }
            }
            weatherGetter.startUpdates(lat, lon)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FONT_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val newIndex = fontManager.addCustomFont(uri)
                customizationSheetManager.onFontAdded(newIndex)
            }
        }
        if (requestCode == PICK_BG_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            } catch (e: Exception) {
                // ignore
            }
            val uriStr = uri.toString()
            backgroundManager.addSavedUri(uriStr)
            backgroundSheetManager.onImageAdded(uriStr)
        }
    }

    private fun loadSavedBackground(skipAnimation: Boolean = false) {
        val uriStr = backgroundManager.getSavedBackgroundUri()
        val blurIntensity = backgroundManager.getBlurIntensity()
        if (uriStr != null) {
            try {
                val uri = Uri.parse(uriStr)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                } catch (e: Exception) {

                }
                applyImageBackground(uri, blurIntensity, skipAnimation) {
                    isBackgroundReady = true
                    checkAndPlayEntranceAnimation()
                }
                setCustomBackground(true)
                Logger.d("MainActivity") {"Loaded custom background: $uriStr (blurIntensity=$blurIntensity)"}
                updateBackgroundFilters()
            } catch (e: Exception) {
                backgroundManager.setSavedBackgroundUri(null)
                setCustomBackground(false)
                Logger.w("MainActivity") {"Failed to load saved background: ${e.message}"}

                isBackgroundReady = true
                checkAndPlayEntranceAnimation()
            }
        } else {
            backgroundImageView.visibility = View.GONE
            setCustomBackground(false)
            gradientManager.startUpdates()
            updateBackgroundFilters()

            isBackgroundReady = true
            checkAndPlayEntranceAnimation()
        }
    }

    fun applyImageBackground(uri: Uri, blurIntensity: Int = 0, skipAnimation: Boolean = false, onComplete: (() -> Unit)? = null) {
        loadBackgroundInternal(uri, blurIntensity, skipAnimation, onComplete)
    }

    fun applyBitmapBackground(bitmap: Bitmap, blurIntensity: Int = 0, skipAnimation: Boolean = false, onComplete: (() -> Unit)? = null) {
        loadBackgroundInternal(bitmap, blurIntensity, skipAnimation, onComplete)
    }

    private fun loadBackgroundInternal(model: Any, blurIntensity: Int, skipAnimation: Boolean = false, onComplete: (() -> Unit)? = null) {
        if (isFinishing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed) return
        
        if (areArtworkSourcesEqual(lastBackgroundSource, model) && lastBlurIntensity == blurIntensity) {
            onComplete?.invoke()
            return
        }
        
        val isSourceChanged = !areArtworkSourcesEqual(lastBackgroundSource, model)
        
        lastBackgroundSource = model
        lastBlurIntensity = blurIntensity
        
        try {
            val targetMode = backgroundManager.getDimMode()
            val targetIntensity = backgroundManager.getDimIntensity()
            val effectiveIntensity = getEffectiveDimIntensity(targetMode, targetIntensity)
            val targetZoom = calculateZoom(effectiveIntensity)

            gradientManager.stopUpdates()

            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0F141A"), Color.parseColor("#171E28"))
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                backgroundLayout.background = gradientDrawable
            } else {
                @Suppress("DEPRECATION")
                backgroundLayout.setBackgroundDrawable(gradientDrawable)
            }

            val animAllowed = isAdvancedGraphicsEnabled && isGraphicsTransitionsEnabled && isSourceChanged && !skipAnimation
            if (animAllowed) {
                isScaleAnimating = true
                dynamicBackgroundView.animate()
                    .scaleX(targetZoom + 0.2f)
                    .scaleY(targetZoom + 0.2f)
                    .setDuration(400)
                    .start()
            } else {
                dynamicBackgroundView.scaleX = targetZoom + 0.2f
                dynamicBackgroundView.scaleY = targetZoom + 0.2f
            }

            if (dynamicBackgroundView.visibility != View.VISIBLE) {
                dynamicBackgroundView.alpha = 0f
                dynamicBackgroundView.visibility = View.VISIBLE
                dynamicBackgroundView.animate().alpha(1f).setDuration(700).start()
            }

            val metrics = resources.displayMetrics
            val maxDim = 1080

            val blurScaleFactor = if (blurIntensity <= 0) 1.0f else {
                val normalized = blurIntensity.coerceIn(0, 100) / 100f
                1.0f - (normalized * 0.75f)
            }

            val targetW = (minOf(metrics.widthPixels, maxDim) * blurScaleFactor).toInt().coerceAtLeast(64)
            val targetH = (minOf(metrics.heightPixels, maxDim) * blurScaleFactor).toInt().coerceAtLeast(64)

            var req = RequestOptions()
                .override(targetW, targetH)
                .downsample(DownsampleStrategy.CENTER_INSIDE)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)

            if (blurIntensity > 0) {
                req = req.transform(
                    BlurTransformation(this, blurIntensity, 1) {
                        if (!isDestroyed && !isFinishing) {
                            updateBackgroundProgress(BackgroundProgressPlugin.Stage.BLURRING)
                        }
                    }
                )
            }

            val mainTarget = object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    bitmap: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.EXTRACTING_COLORS)

                    Thread {
                        ColorExtractor.extractColor(bitmap) { seedColor ->
                            handler.post {
                                updateBackgroundProgress(BackgroundProgressPlugin.Stage.APPLYING_THEME)

                                fontManager.setDynamicScheme(seedColor)
                                fontManager.setDynamicColorFromSeed(fontManager.getDynamicScheme().secondary)

                                val noiseColor = fontManager.getDynamicScheme().primary

                                if (isAdvancedGraphicsEnabled && isGraphicsTurbulenceEnabled && isSourceChanged && !skipAnimation) {
                                    turbulenceOverlay.playAnimation(noiseColor) {}
                                }

                                val bgOffsetX = backgroundManager.getBgOffsetX()
                                val bgOffsetY = backgroundManager.getBgOffsetY()
                                val bgScale   = backgroundManager.getBgScale()

                                // 1. Запускаем кинематографичный GLSL-переход (Crossfade)
                                val duration = if (skipAnimation || !isAdvancedGraphicsEnabled || !isGraphicsTransitionsEnabled) 0L else if (isSourceChanged) 2000L else 300L
                                dynamicBackgroundView.transitionTo(bitmap, duration, bgScale, bgOffsetX, bgOffsetY)

                                val currentTargetMode = backgroundManager.getDimMode()
                                val currentTargetIntensity = backgroundManager.getDimIntensity()
                                val currentEffectiveIntensity = getEffectiveDimIntensity(currentTargetMode, currentTargetIntensity)
                                val finalZoom = calculateZoom(currentEffectiveIntensity)

                                fontManager.applyNightShiftTransition(
                                    clockManager.getCurrentTime(),
                                    dayTimeGetter,
                                    true
                                )

                                onComplete?.invoke()

                                updateBackgroundFilters()

                                if (isAdvancedGraphicsEnabled && isGraphicsTransitionsEnabled && !skipAnimation) {
                                    isScaleAnimating = true
                                    dynamicBackgroundView.animate()
                                        .scaleX(finalZoom)
                                        .scaleY(finalZoom)
                                        .setDuration(1200)
                                        .setListener(object : AnimatorListenerAdapter() {
                                            override fun onAnimationEnd(animation: Animator) {
                                                isScaleAnimating = false
                                                handler.postDelayed({
                                                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE)
                                                }, 500)
                                            }
                                        }).start()
                                } else {
                                    dynamicBackgroundView.scaleX = finalZoom
                                    dynamicBackgroundView.scaleY = finalZoom
                                    isScaleAnimating = false
                                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE)
                                }
                            }
                        }
                    }.start()
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE, "Failed to load")
                    Logger.w("MainActivity") {"Glide failed to load background: $model"}
                    isScaleAnimating = false
                    handler.postDelayed({
                        updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE)
                    }, 2000)

                    onComplete?.invoke()

                    try {
                        val savedUri = backgroundManager.getSavedBackgroundUri()
                        if (model.toString() != savedUri) {
                            restoreUserBackground(savedUri)
                        }
                    } catch (e: Exception) {
                        Logger.e("MainActivity"){"Failed to restore user background: ${e.message}"}
                    }
                }

                override fun onLoadStarted(placeholder: Drawable?) {
                    super.onLoadStarted(placeholder)
                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.DOWNLOADING)
                }
            }

            GlideApp.with(this)
                .asBitmap()
                .load(model)
                .apply(req)
                .into(mainTarget)

            if (isAdvancedGraphicsEnabled) {
                applyEditModeBlurLayer(model)
            } else {
                editModeBlurLayer.setImageDrawable(null)
                editModeBlurLayer.visibility = View.GONE
            }

        } catch (e: Exception) {
            updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE, "Failed to load")
            handler.postDelayed({
                updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE)
            }, 2000)
            Logger.e("MainActivity"){"loadBackgroundInternal failed: ${e.message}"}
            onComplete?.invoke()
        }
    }

    private fun applyEditModeBlurLayer(model: Any) {
        editModeBlurLayer.visibility = View.VISIBLE
        val ambientReq = RequestOptions()
            .transform(FitCenter(), BlurTransformation(this, 20, 3))
            .override(200, 400)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

        val editTarget = object : CustomTarget<Drawable>() {
            override fun onResourceReady(
                resource: Drawable,
                transition: Transition<in Drawable>?
            ) {
                editModeBlurLayer.setImageDrawable(resource)
                updateBackgroundFilters()
            }
            override fun onLoadCleared(placeholder: Drawable?) {
                editModeBlurLayer.setImageDrawable(null)
            }
        }

        GlideApp.with(this)
            .load(model)
            .apply(ambientReq)
            .into(editTarget)
    }

    private fun getEffectiveDimIntensity(
        mode: Int,
        userIntensity: Int,
        previewDimMin: Int? = null,
        previewDimMax: Int? = null
    ): Int {
        val intensity = when (mode) {
            BackgroundManager.Companion.DIM_MODE_OFF -> 0
            BackgroundManager.Companion.DIM_MODE_DYNAMIC -> {
                try {
                    backgroundManager.computeEffectiveDimIntensity(
                        clockManager.getCurrentTime(),
                        dayTimeGetter,
                        overrideMode = mode,
                        overrideMin = previewDimMin,
                        overrideMax = previewDimMax
                    )
                } catch (e: Exception) {
                    (previewDimMax ?: backgroundManager.getDimMaxIntensity()).coerceIn(0, 50)
                }
            }
            else -> userIntensity.coerceIn(0, 50) // CONTINUOUS
        }
        return intensity.coerceIn(0, 50)
    }

    private fun calculateZoom(effectiveIntensity: Int): Float {
        if (!backgroundManager.getZoomEnabled()) {
            return 1.0f
        }
        return 1.0f + (effectiveIntensity.coerceIn(0, 50) / 50f) * 0.2f
    }

    private fun updateBackgroundFilters(
        previewDimMode: Int? = null,
        previewDimIntensity: Int? = null,
        previewDimMinIntensity: Int? = null,
        previewDimMaxIntensity: Int? = null
    ) {
        if (dynamicBackgroundView.visibility != View.VISIBLE) return

        val dimMode = previewDimMode ?: backgroundManager.getDimMode()
        // For continuous mode preview, use the single-slider value (previewDimIntensity).
        // For dynamic mode preview, use max (previewDimMaxIntensity) so the helper can
        // interpolate between min and max correctly.
        // Fall back to saved prefs when no preview overrides are given.
        val dimIntensity = when {
            previewDimMode == BackgroundManager.Companion.DIM_MODE_CONTINUOUS && previewDimIntensity != null -> previewDimIntensity
            previewDimMode == BackgroundManager.Companion.DIM_MODE_DYNAMIC && previewDimMaxIntensity != null -> previewDimMaxIntensity
            else -> backgroundManager.getDimIntensity()
        }
        val effectiveDim = getEffectiveDimIntensity(
            dimMode,
            dimIntensity,
            previewDimMinIntensity,
            previewDimMaxIntensity
        )

        val maxDarkness = 0.8f
        val dimFactor = (effectiveDim / 50f) * maxDarkness

        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val dimBackground = prefs.getBoolean("power_saver_dim_background", true)
        val finalDimFactor = if (isPowerSavingMode && dimBackground) {
            val dimLevel = prefs.getInt("power_saver_dim_level", 85) / 100f
            dimLevel
        } else {
            dimFactor
        }
        val brightness = 1.0f - finalDimFactor

        val isWeatherEnabled = backgroundManager.isWeatherEffectsEnabled()
        val isNight = !dayTimeGetter.isDay()
        val baseWeatherMatrix = ColorMatrix()

        if (isWeatherEnabled) {
            val isManual = backgroundManager.isManualWeatherEnabled()
            var wmoCode = 0
            var rawIntensity = 0f

            if (isManual) {
                val typeOrdinal = backgroundManager.getManualWeatherType()
                val type = DynamicBackgroundView.WeatherType.values()[typeOrdinal]
                wmoCode = when (type) {
                    DynamicBackgroundView.WeatherType.CLEAR -> 0
                    DynamicBackgroundView.WeatherType.CLOUDY -> 3
                    DynamicBackgroundView.WeatherType.FOG -> 45
                    DynamicBackgroundView.WeatherType.RAIN -> 63
                    DynamicBackgroundView.WeatherType.SNOW -> 73
                    DynamicBackgroundView.WeatherType.THUNDERSTORM -> 95
                    else -> 0
                }
                rawIntensity = backgroundManager.getManualWeatherIntensity() / 100f
            } else {
                wmoCode = weatherGetter.weatherCode ?: 0
                rawIntensity = calculateWeatherIntensity(
                    wmoCode,
                    weatherGetter.windSpeed,
                    weatherGetter.precipitation,
                    weatherGetter.cloudCover,
                    weatherGetter.visibility
                )
            }

            val visualIntensity = rawIntensity * 0.2f
            val weatherMatrix = getWeatherMatrix(wmoCode, isNight, visualIntensity)
            baseWeatherMatrix.postConcat(weatherMatrix)
        }

        // Night Shift Factor for Background
        val nightFactor = backgroundManager.computeNightShiftFactor(clockManager.getCurrentTime(), dayTimeGetter)
        if (nightFactor > 0f) {
            val nightShiftMatrix = ColorMatrix()
            val rScale = 1.0f
            val gScale = 1.0f - (0.55f * nightFactor)
            val bScale = 1.0f - (0.80f * nightFactor)
            nightShiftMatrix.setScale(rScale, gScale, bScale, 1f)
            baseWeatherMatrix.postConcat(nightShiftMatrix)
        }

        val combinedMatrix = ColorMatrix(baseWeatherMatrix)
        val dimMatrix = ColorMatrix()
        dimMatrix.setScale(brightness, brightness, brightness, 1f)
        combinedMatrix.postConcat(dimMatrix)

        dynamicBackgroundView.setColorFilter(combinedMatrix)

        if (isEditMode && isAdvancedGraphicsEnabled && isGraphicsEditBlurEnabled && hasCustomImageBackground) {
            val editModeMatrix = ColorMatrix(baseWeatherMatrix)
            val editModeDim = ColorMatrix()
            val blendedBrightness = 1.0f - (finalDimFactor * 0.3f)
            val finalEditScale = 0.35f * blendedBrightness
            editModeDim.setScale(finalEditScale, finalEditScale, finalEditScale, 1.0f)
            editModeMatrix.postConcat(editModeDim)
            editModeBlurLayer.colorFilter = android.graphics.ColorMatrixColorFilter(editModeMatrix)
        } else {
            editModeBlurLayer.clearColorFilter()
        }

        if (!isScaleAnimating) {
            val zoom = calculateZoom(effectiveDim)
            dynamicBackgroundView.scaleX = zoom
            dynamicBackgroundView.scaleY = zoom
        }
    }

    private fun updateBackgroundProgress(stage: BackgroundProgressPlugin.Stage, messageOverride: String? = null) {
        BackgroundProgressPlugin.Companion.currentStage = stage
        BackgroundProgressPlugin.Companion.customMessage = messageOverride

        runOnUiThread {
            BackgroundProgressPlugin.onGlobalStateChanged?.invoke()
        }
    }

    private fun setupSideSheet() {
        val fontRecyclerView = sideSheet.findViewById<RecyclerView>(R.id.font_recycler_view)
        fontRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        fontRecyclerView.isNestedScrollingEnabled = false
        sideSheetBehavior.addCallback(object : SideSheetCallback() {
            override fun onStateChanged(sheet: View, newState: Int) {
                if (newState == SideSheetBehavior.STATE_HIDDEN) {
                    resetEditModeTimeout()
                    customizationSheetManager.hide()
                } else {
                    stopHideUiTimer()
                }
            }

            override fun onSlide(sheet: View, slideOffset: Float) {}
        })
        sideSheet.bringToFront()
    }

    private fun showDeleteConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun startUpdates() {
        clockManager.startUpdates()
        if (!hasCustomImageBackground) gradientManager.startUpdates()
        if (getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE).getBoolean("burn_in_protection", false)) burnInProtectionManager.start()
        else burnInProtectionManager.stop()
    }

    private fun stopUpdates() {
        clockManager.stopUpdates()
        gradientManager.stopUpdates()
        handler.removeCallbacks(editModeTimeoutRunnable)
    }

    private fun animateCornerRadius(view: View, fromRadius: Float, toRadius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val animator = ValueAnimator.ofFloat(fromRadius, toRadius)
            animator.duration = animationDuration-100L

            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Float

                view.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, value)
                    }
                }
                view.clipToOutline = value > 0f
            }
            animator.start()
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        smartChipManager.setEditMode(isEditMode) { clickedView ->
            customizationSheetManager.showForView(clickedView)
            resetEditModeTimeout()
        }
        widgetMover.setEditMode(isEditMode)
        val targetRadius = dpToPx(36f)
        if (isEditMode) {
            if (::nowPlayingExpandableManager.isInitialized && nowPlayingExpandableManager.isExpanded) {
                nowPlayingExpandableManager.collapse()
            }
            settingsButton.visibility = View.VISIBLE
            debugButton.visibility = View.VISIBLE
            backgroundCustomizationTab.visibility = View.VISIBLE
            mainLayout.animate()
                .scaleX(0.90f)
                .scaleY(0.90f)
                .setDuration(animationDuration)
                .setInterpolator(OvershootInterpolator())
                .start()
            animateCornerRadius(mainLayout, 0f, targetRadius)
            if (isAdvancedGraphicsEnabled && isGraphicsEditBlurEnabled && hasCustomImageBackground) {
                editModeBlurLayer.visibility = View.VISIBLE
                editModeBlurLayer.alpha = 1.0f
                updateBackgroundFilters()
            }
            settingsButton.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()
            debugButton.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()
            backgroundCustomizationTab.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()
            timeText.setBackgroundResource(R.drawable.editable_border)
            dateText.setBackgroundResource(R.drawable.editable_border)
            lastfmLayout.setBackgroundResource(R.drawable.editable_border)
            chipContainer.setBackgroundResource(R.drawable.editable_border)
            lastfmLayout.animate().cancel()
            lastfmLayout.clearAnimation()
            lastfmLayout.visibility = View.VISIBLE
            lastfmLayout.alpha = 1f
            if (nowPlayingTextView.text.isNullOrEmpty()) {
                nowPlayingTextView.text = getString(R.string.now_playing_placeholder)
            }
            if (!isDemoMode) {
                handler.removeCallbacks(editModeTimeoutRunnable)
                handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
            }
        } else {
            exitEditMode()
        }
    }

    private fun resetEditModeTimeout() {
        if (!isDemoMode && !isCropModeActive) {
            handler.removeCallbacks(editModeTimeoutRunnable)
            handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
        }
    }

    private fun stopHideUiTimer() {
        handler.removeCallbacks(editModeTimeoutRunnable)
    }

    private fun exitEditMode() {
        isEditMode = false
        smartChipManager.setEditMode(false) { }
        widgetMover.setEditMode(false)
        mainLayout.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(animationDuration)
            .setInterpolator(OvershootInterpolator())
            .start()
        val startRadius = dpToPx(36f)
        animateCornerRadius(mainLayout, startRadius, 0f)
        if (editModeBlurLayer.visibility == View.VISIBLE) {
            editModeBlurLayer.animate()
                .alpha(0f)
                .setDuration(animationDuration)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isEditMode) {
                            editModeBlurLayer.visibility = View.GONE
                        }
                    }
                })
                .start()
        }
        settingsButton.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .start()
        debugButton.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .start()
        backgroundCustomizationTab.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            timeText.background = null
        } else
            timeText.setBackgroundDrawable(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            dateText.background = null
        } else
            dateText.setBackgroundDrawable(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            lastfmLayout.background = null
        } else
            lastfmLayout.setBackgroundDrawable(null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            chipContainer.background = null
        } else {
            chipContainer.setBackgroundDrawable(null)
        }
        val isMusicPlaying = !nowPlayingTextView.text.isNullOrEmpty() &&
                lastTrackInfo != null &&
                lastfmLayout.alpha > 0

        if (!isMusicPlaying) {
            lastfmLayout.animate()
                .alpha(0f)
                .setDuration(400)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isEditMode) {
                            lastfmLayout.visibility = View.GONE
                        }
                    }
                })
                .start()
        } else {
            lastfmLayout.animate()
                .alpha(1f)
                .setDuration(200)
                .setListener(null)
                .start()
        }

        settingsButton.visibility = View.GONE
        debugButton.visibility = View.GONE
        backgroundCustomizationTab.visibility = View.GONE
        handler.removeCallbacks(editModeTimeoutRunnable)
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        updateSensorRegistration()
        if (::tutorialManager.isInitialized) {
            tutorialManager.checkAndUpdatePermissionState()
        }
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val disableAnimations = prefs.getBoolean("power_saver_disable_animations", true)
        if (::dynamicBackgroundView.isInitialized) {
            dynamicBackgroundView.onResume()
            dynamicBackgroundView.areAnimationsPaused = isPowerSavingMode && disableAnimations
        }
        setupWindowFlags()
        val disableWeather = isPowerSavingMode && prefs.getBoolean("power_saver_disable_weather", true)
        locationManager.loadCoordinates { lat, lon ->
            dayTimeGetter.fetch(lat, lon) {
                if (!hasCustomImageBackground) gradientManager.updateGradient()
                if (isNightShiftEnabled) fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, isNightShiftEnabled)
                if (dynamicBackgroundView.visibility == View.VISIBLE) updateBackgroundFilters()
                updateSensorRegistration()
                if (dayTimeGetter.isDay()) {
                    applyNightDimMode(NightDimState.NORMAL)
                } else {
                    reEvaluateSmartNightDimming()
                }
            }
            if (!disableWeather && ::weatherGetter.isInitialized) {
                weatherGetter.startUpdates(lat, lon)
            }
        }
        if (isEditMode && !isLaunchingFilePicker) exitEditMode()
        isLaunchingFilePicker = false
        if (isDemoMode) {
            isDemoMode = false
            clockManager.toggleDebugMode(false)
            gradientManager.toggleDebugMode(false)
        }

        enableAdditionalLogging = prefs.getBoolean("additional_logging", false)
        isAdvancedGraphicsEnabled = prefs.getBoolean("advanced_graphics", false)
        isGraphicsTransitionsEnabled = prefs.getBoolean("graphics_enable_transitions", true)
        isGraphicsTurbulenceEnabled = prefs.getBoolean("graphics_enable_turbulence", true)
        isGraphicsEditBlurEnabled = prefs.getBoolean("graphics_enable_edit_blur", true)
        graphicsRenderScale = prefs.getInt("graphics_render_scale", 100)
        graphicsWeatherScale = prefs.getInt("graphics_weather_scale", 40)
        if (::dynamicBackgroundView.isInitialized) {
            val targetScale = if (isAdvancedGraphicsEnabled) graphicsRenderScale / 100f else 0.5f
            dynamicBackgroundView.setRenderScale(targetScale)
            val targetWeatherScale = if (isAdvancedGraphicsEnabled) graphicsWeatherScale / 100f else 0.4f
            dynamicBackgroundView.weatherResolutionScale = targetWeatherScale
        }

        val enableSmartPixels = prefs.getBoolean("power_saver_enable_smart_pixels", true)
        val shouldStartSmartPixels = prefs.getBoolean("smart_pixels_enabled", false) || (isPowerSavingMode && enableSmartPixels)
        if (shouldStartSmartPixels) smartPixelManager.start() else smartPixelManager.stop()
        getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        smartChipManager.updateAllChips()
        startUpdates()
        val showPerformance = prefs.getBoolean("show_performance_overlay", false)
        togglePerformanceOverlay(showPerformance)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun restoreGradientBackground() {
        dynamicBackgroundView.visibility = View.VISIBLE
        backgroundImageView.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                backgroundImageView.setRenderEffect(null)
            } catch (_: Throwable) {}
        }
        fontManager.clearDynamicColors()
        setCustomBackground(false)
        editModeBlurLayer.setImageDrawable(null)
        editModeBlurLayer.visibility = View.GONE
        gradientManager.startUpdates()
    }

    fun restoreUserBackground(savedUriStr: String?) {
        if (savedUriStr != null) {
            try {
                val uri = Uri.parse(savedUriStr)
                val blur = backgroundManager.getBlurIntensity()
                applyImageBackground(uri, blur) {
                    setCustomBackground(true)
                }
                if (::backgroundSheetManager.isInitialized) {
                    backgroundSheetManager.updateCropButtonVisibility(true)
                }
            } catch (e: Exception) {
                Logger.e("MainActivity"){"Failed to restore user background"}
                restoreGradientBackground()
            }
        } else {
            restoreGradientBackground()
        }
    }

    override fun onPause() {
        super.onPause()
        isResumed = false
        updateSensorRegistration()
        if (::dynamicBackgroundView.isInitialized) {
            dynamicBackgroundView.onPause()
        }
        burnInProtectionManager.stop()
        smartPixelManager.stop()
        stopUpdates()
        stopPerformanceUpdates()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::tutorialManager.isInitialized) {
            tutorialManager.checkAndUpdatePermissionState()
        }
        if (requestCode == permissionRequestCode) {
            loadCoordinatesAndFetchData()
            val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasCoarse && !hasFine) {
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean("location_permission_rationale_shown", true).apply()
                showManualLocationOptionDialog()
            }
        }
    }

    override fun onPowerSaveModeChanged(isEnabled: Boolean) {
        val modeChanged = isPowerSavingMode != isEnabled
        isPowerSavingMode = isEnabled

        Logger.d("MainActivity") { "Power Save Mode changed to: $isEnabled. Adapting UI." }

        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val disableAnimations = prefs.getBoolean("power_saver_disable_animations", true)
        val disableWeather = prefs.getBoolean("power_saver_disable_weather", true)
        val lockBrightness = prefs.getBoolean("power_saver_lock_brightness", true)
        val disableLightSensor = prefs.getBoolean("power_saver_disable_light_sensor", true)
        val enableSmartPixels = prefs.getBoolean("power_saver_enable_smart_pixels", true)
        val limitFps = prefs.getBoolean("power_saver_limit_fps", true)
        val fpsLimitValue = prefs.getInt("power_saver_fps_limit_value", 30)

        if (isEnabled) {
            // Apply granular battery saving rules

            // 1. OpenGL Animations & Framerate
            if (::dynamicBackgroundView.isInitialized) {
                dynamicBackgroundView.areAnimationsPaused = disableAnimations
                dynamicBackgroundView.maxFps = if (limitFps) fpsLimitValue else 0
            }

            // 2. Weather Updates
            if (disableWeather) {
                if (::weatherGetter.isInitialized) {
                    weatherGetter.stopUpdates()
                }
            } else {
                locationManager.loadCoordinates { lat, lon ->
                    if (::weatherGetter.isInitialized) {
                        weatherGetter.startUpdates(lat, lon)
                    }
                }
            }

            // 3. Ambient Light Sensor
            updateSensorRegistration()

            // 4. Brightness Override
            if (lockBrightness) {
                val brightnessPct = prefs.getInt("power_saver_brightness_level", 1) / 100f
                applyBrightnessOverride(brightnessPct.coerceIn(0.01f, 1.0f))
            } else {
                val layoutParams = window.attributes
                val savedBrightness = prefs.getInt("power_saver_brightness_level", 1) / 100f
                if (layoutParams.screenBrightness == savedBrightness.coerceIn(0.01f, 1.0f) || layoutParams.screenBrightness == minPowerSaveBrightness) {
                    applyBrightnessOverride(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                }
            }

            // 5. Smart Pixels
            if (enableSmartPixels) {
                smartPixelManager.start()
            } else {
                if (!prefs.getBoolean("smart_pixels_enabled", false)) {
                    smartPixelManager.stop()
                }
            }

        } else {
            // Restore normal mode
            if (modeChanged) {
                // 1. Graphics animations & framerate
                if (::dynamicBackgroundView.isInitialized) {
                    dynamicBackgroundView.areAnimationsPaused = false
                    dynamicBackgroundView.maxFps = 0
                }

                // 2. Weather
                locationManager.loadCoordinates { lat, lon ->
                    if (::weatherGetter.isInitialized) {
                        weatherGetter.startUpdates(lat, lon)
                    }
                }

                // 3. Sensors & brightness
                updateSensorRegistration()
                val brightnessMode = prefs.getString("brightness_mode", "system") ?: "system"
                if (brightnessMode == "smart_night") {
                    applyNightDimMode(currentNightDimState)
                } else {
                    applyBrightnessOverride(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                }

                // 4. Smart pixels (restore state matching user preference)
                if (prefs.getBoolean("smart_pixels_enabled", false)) {
                    smartPixelManager.start()
                } else {
                    smartPixelManager.stop()
                }
            }
        }

        // Always update background filters (handles dimming changes)
        if (::dynamicBackgroundView.isInitialized && dynamicBackgroundView.visibility == View.VISIBLE) {
            updateBackgroundFilters()
        }
    }

    private var performanceRunnable: Runnable? = null
    private val performanceUpdateInterval = 1000L // 1 second

    private fun togglePerformanceOverlay(enabled: Boolean) {
        if (!::performanceOverlay.isInitialized) return
        dynamicBackgroundView.performanceTracker.isEnabled = enabled
        if (enabled) {
            performanceOverlay.visibility = View.VISIBLE
            startPerformanceUpdates()
        } else {
            performanceOverlay.visibility = View.GONE
            stopPerformanceUpdates()
        }
    }

    private fun startPerformanceUpdates() {
        if (performanceRunnable != null) return
        performanceRunnable = object : Runnable {
            override fun run() {
                if (dynamicBackgroundView.performanceTracker.isEnabled) {
                    val metrics = dynamicBackgroundView.performanceTracker.updateMetrics()
                    performanceOverlay.updateMetrics(metrics)
                    handler.postDelayed(this, performanceUpdateInterval)
                }
            }
        }
        handler.post(performanceRunnable!!)
    }

    private fun stopPerformanceUpdates() {
        performanceRunnable?.let {
            handler.removeCallbacks(it)
            performanceRunnable = null
        }
    }

    private fun applyBrightnessOverride(brightnessValue: Float) {
        val layoutParams = window.attributes
        if (layoutParams.screenBrightness != brightnessValue) {
            layoutParams.screenBrightness = brightnessValue
            window.attributes = layoutParams
        }
    }

    private fun applyNightDimMode(state: NightDimState) {
        if (isPowerSavingMode) return // Let power save mode handle its own brightness

        if (dayTimeGetter.isDay()) {
            applyBrightnessOverride(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            return
        }

        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        if (state == NightDimState.DIMMED) {
            val minBrightPct = prefs.getInt("smart_night_min_brightness", 5) / 100f
            applyBrightnessOverride(minBrightPct.coerceIn(0.01f, 1.0f))
        } else {
            applyBrightnessOverride(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }

    private fun updateSensorRegistration() {
        if (!::sensorManager.isInitialized) return
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val brightnessMode = prefs.getString("brightness_mode", "system") ?: "system"
        val disableLightSensorInPowerSave = prefs.getBoolean("power_saver_disable_light_sensor", true)

        val shouldRegister = isResumed && (
            (isPowerSavingMode && !disableLightSensorInPowerSave) ||
            (!isPowerSavingMode && brightnessMode == "smart_night" && !dayTimeGetter.isDay())
        )

        if (shouldRegister) {
            lightSensor?.let {
                sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else {
            sensorManager.unregisterListener(sensorEventListener)
            cancelPendingDimTransition()
        }
    }

    private fun cancelPendingDimTransition() {
        dimDebounceHandler.removeCallbacks(dimDebounceRunnable)
        pendingNightDimState = null
    }

    private fun reEvaluateSmartNightDimming() {
        val lux = lastSeenLux ?: return
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val threshold = prefs.getInt("smart_night_lux_threshold", 5)
        val hysteresis = 3

        val candidateState = when (currentNightDimState) {
            NightDimState.NORMAL -> {
                if (lux < threshold) NightDimState.DIMMED else NightDimState.NORMAL
            }
            NightDimState.DIMMED -> {
                if (lux >= threshold + hysteresis) NightDimState.NORMAL else NightDimState.DIMMED
            }
        }

        if (candidateState != currentNightDimState) {
            if (candidateState != pendingNightDimState) {
                dimDebounceHandler.removeCallbacks(dimDebounceRunnable)
                pendingNightDimState = candidateState
                dimDebounceHandler.postDelayed(dimDebounceRunnable, 2500L)
            }
        } else {
            if (pendingNightDimState != null) {
                dimDebounceHandler.removeCallbacks(dimDebounceRunnable)
                pendingNightDimState = null
            }
        }
    }
}