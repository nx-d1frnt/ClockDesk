package com.nxd1frnt.clockdesk2.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AlertDialog
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
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
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
import com.bumptech.glide.load.resource.bitmap.CenterCrop
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
import com.nxd1frnt.clockdesk2.utils.LocationManager
import com.nxd1frnt.clockdesk2.utils.Logger
import com.nxd1frnt.clockdesk2.utils.PowerSaveObserver
import com.nxd1frnt.clockdesk2.utils.PowerStateManager
import com.nxd1frnt.clockdesk2.utils.SmartPixelManager
import com.nxd1frnt.clockdesk2.utils.calculateWeatherIntensity
import com.nxd1frnt.clockdesk2.utils.getWeatherMatrix
import com.nxd1frnt.clockdesk2.weathergetter.OpenMeteoAPI
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter
import androidx.viewpager2.widget.ViewPager2
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.button.MaterialButton
import android.content.Context
import android.media.session.MediaSessionManager
import android.content.ComponentName
import com.nxd1frnt.clockdesk2.music.ClockDeskMediaService
import com.nxd1frnt.clockdesk2.ui.dashboard.DashboardTile
import com.nxd1frnt.clockdesk2.ui.dashboard.DashboardAdapter
import com.nxd1frnt.clockdesk2.ui.dashboard.DashboardManager
import java.util.Calendar
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

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

    // State Variables
    private var musicManager: MusicPluginManager? = null
    private var currentMusicState: PluginState = PluginState.Idle
    private var lastTrackInfo: String? = null
    private var wasMusicBackgroundApplied = false
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
    private val PICK_ZIP_REQUEST = 402
    private val minPowerSaveBrightness = 0.01f

    private var lightSensor: Sensor? = null
    private lateinit var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener
    private var pendingRestoreRunnable: Runnable? = null
    private lateinit var entranceAnimationManager: EntranceAnimationManager

    private lateinit var viewPager: ViewPager2
    private lateinit var mainPage: View
    private lateinit var dashboardPage: View
    private lateinit var dashboardRecyclerView: RecyclerView
    private lateinit var dashboardGreeting: TextView
    private lateinit var dashboardSubtitle: TextView
    private lateinit var btnScenarioLightsOff: MaterialButton
    private lateinit var btnScenarioMediaStop: MaterialButton

    private var dashboardManager: DashboardManager? = null
    private var dashboardAdapter: DashboardAdapter? = null
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
                if (!isPowerSavingMode) {
                    return
                }

                val lux = event.values[0]
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

        viewPager = super.findViewById(R.id.view_pager)
        val inflater = LayoutInflater.from(this)
        dashboardPage = inflater.inflate(R.layout.dashboard_page, null).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        mainPage = inflater.inflate(R.layout.content_main, null).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        viewPager.adapter = MainPagerAdapter(dashboardPage, mainPage)
        viewPager.currentItem = 1

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
        setupDashboard()
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

        dashboardRecyclerView = findViewById(R.id.dashboard_recycler_view)
        dashboardGreeting = findViewById(R.id.dashboard_greeting)
        dashboardSubtitle = findViewById(R.id.dashboard_subtitle)
        btnScenarioLightsOff = findViewById(R.id.btn_scenario_lights_off)
        btnScenarioMediaStop = findViewById(R.id.btn_scenario_media_stop)

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
                if (weatherGetter.temperature != null) {
                    weatherText.text = "${weatherGetter.temperature}°C"
                }

                val code = weatherGetter.weatherCode ?: 0
                val wind = weatherGetter.windSpeed ?: 0.0
                val isNight = !dayTimeGetter.isDay()
                val precip = weatherGetter.precipitation
                val clouds = weatherGetter.cloudCover
                val vis = weatherGetter.visibility

                if (backgroundManager.isWeatherEffectsEnabled() && !backgroundManager.isManualWeatherEnabled()) {
                    dynamicBackgroundView.updateFromOpenMeteoSmart(
                        code, wind, isNight,
                        precip, clouds, vis
                    )
                }

                if (hasCustomImageBackground) {
                    updateBackgroundFilters()
                }
                
                dashboardManager?.pushWeatherState()
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
            }
        }
        chipContainer.setOnClickListener {
            if (isEditMode) {
                customizationSheetManager.showForView(it)
                resetEditModeTimeout()
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

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            exitEditMode()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::viewPager.isInitialized && viewPager.currentItem == 0) {
                    viewPager.currentItem = 1
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
                "automatic_battery_saver_mode", "battery_saver_trigger", "battery_saver_mode",
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
        stopUpdates()
        musicManager?.destroy()
        dashboardManager?.destroy()
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
                dashboardManager?.pushMusicState(state)
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

    private fun handleMusicStateUpdate(state: PluginState) {
        currentMusicState = state
        if (isEditMode) {
            if (state is PluginState.Playing) {
                val track = state.track
                val trackInfoText = "${track.artist} - ${track.title}"
                nowPlayingTextView.text = trackInfoText
                val isTextDifferent = trackInfoText != lastTrackInfo
                lastTrackInfo = trackInfoText
                val hasNewArt = !wasMusicBackgroundApplied &&
                        (track.artworkBitmap != null || !track.artworkUrl.isNullOrEmpty())
                if (isTextDifferent || hasNewArt) {
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

        when (state) {
            is PluginState.Playing -> {
                pendingRestoreRunnable?.let { handler.removeCallbacks(it) }
                pendingRestoreRunnable = null

                val track = state.track
                val trackInfoText = "${track.artist} - ${track.title}"
                val isTextDifferent = trackInfoText != lastTrackInfo

                val hasNewArt = !wasMusicBackgroundApplied &&
                        (track.artworkBitmap != null || !track.artworkUrl.isNullOrEmpty())
                if (isTextDifferent || hasNewArt) {
                    handleBackgroundUpdate(track)
                }
                if (isTextDifferent) {
                    lastTrackInfo = trackInfoText

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
        lastfmLayout.animate().cancel()

        if (lastfmLayout.visibility == View.VISIBLE) {
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
        }

        if (wasMusicBackgroundApplied) {
            restoreUserBackground(backgroundManager.getSavedBackgroundUri())
            wasMusicBackgroundApplied = false
        }
        lastTrackInfo = null
    }

    private fun handleBackgroundUpdate(track: MusicTrack) {
        val blurIntensity = backgroundManager.getBlurIntensity()
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val musicBgEnabled = prefs.getBoolean("lastfm_albumart_background", true)

        if (!musicBgEnabled) {
            if (wasMusicBackgroundApplied) {
                restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                wasMusicBackgroundApplied = false
            }
            return
        }
        if (track.artworkBitmap != null) {
            Logger.d("MainActivity"){"Applying bitmap album art background"}
            applyBitmapBackground(track.artworkBitmap, blurIntensity)
            wasMusicBackgroundApplied = true
        }
        else if (!track.artworkUrl.isNullOrEmpty()) {
            Logger.d("MainActivity"){"Applying URL album art background: ${track.artworkUrl}"}
            applyImageBackground(Uri.parse(track.artworkUrl), blurIntensity)
            wasMusicBackgroundApplied = true
        }
        else {
            val savedUri = backgroundManager.getSavedBackgroundUri()
            if (savedUri != null) {
                if (wasMusicBackgroundApplied) {
                    restoreUserBackground(savedUri)
                    wasMusicBackgroundApplied = false
                }
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
            if (!rationaleShown) showLocationRationaleDialog()
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
            dynamicBackgroundView.forceWeather(type, intensity, 5.0f, isNight)
        } else {
            val code = weatherGetter.weatherCode ?: 0
            val wind = weatherGetter.windSpeed ?: 0.0
            val precip = weatherGetter.precipitation
            val clouds = weatherGetter.cloudCover
            val vis = weatherGetter.visibility
            dynamicBackgroundView.updateFromOpenMeteoSmart(
                code, wind, isNight,
                precip, clouds, vis
            )
        }
    }

    private fun showLocationRationaleDialog() {
        AlertDialog.Builder(this)
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
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNeutralButton(getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean("location_permission_rationale_shown", true).apply()
            }
            .show()
    }

    private fun loadCoordinatesAndFetchData() {
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
        if (requestCode == PICK_ZIP_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val fileName = getFileName(uri) ?: "imported_plugin.zip"
            val success = dashboardManager?.importPluginFromZip(uri, fileName) ?: false
            if (success) {
                Toast.makeText(this, "Widget imported successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to import ZIP widget. Make sure it contains layout.json and logic.js", Toast.LENGTH_LONG).show()
            }
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
        
        if (lastBackgroundSource == model && lastBlurIntensity == blurIntensity) {
            onComplete?.invoke()
            return
        }
        
        val isSourceChanged = lastBackgroundSource != model
        
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

                                var noiseColor = Color.WHITE
                                if (fontManager.getDynamicScheme() == null) {
                                    noiseColor = getColor(R.color.md_theme_primary)
                                } else {
                                    noiseColor = fontManager.getDynamicScheme()!!.primary
                                }

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

//    private fun loadBackgroundInternal(model: Any, blurIntensity: Int, onComplete: (() -> Unit)? = null) {
//        if (isFinishing) return
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed) return
//        try {
//            val targetMode = backgroundManager.getDimMode()
//            val targetIntensity = backgroundManager.getDimIntensity()
//            val effectiveIntensity = getEffectiveDimIntensity(targetMode, targetIntensity)
//            val targetZoom = calculateZoom(effectiveIntensity)
//
//            gradientManager.stopUpdates()
//
//            val gradientDrawable = GradientDrawable(
//                GradientDrawable.Orientation.TOP_BOTTOM,
//                intArrayOf(Color.parseColor("#0F141A"), Color.parseColor("#171E28"))
//            )
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
//                backgroundLayout.background = gradientDrawable
//            } else {
//                @Suppress("DEPRECATION")
//                backgroundLayout.setBackgroundDrawable(gradientDrawable)
//            }
//
//            backgroundImageView.visibility = View.VISIBLE
//            backgroundImageView.scaleX = targetZoom
//            backgroundImageView.scaleY = targetZoom
//            backgroundImageView.animate()
//                .scaleX(targetZoom + 0.4f)
//                .scaleY(targetZoom + 0.4f)
//                .alpha(0f)
//                .setDuration(700)
//                .setListener(null)
//                .start()
//
//            val usePlatformBlur = blurIntensity > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
//            val metrics = resources.displayMetrics
//            val maxDim = 1080
//
//            val blurScaleFactor = if (blurIntensity <= 0) 1.0f else {
//                val normalized = blurIntensity.coerceIn(0, 100) / 100f
//                1.0f - (normalized * 0.75f)
//            }
//
//            val targetW = (minOf(metrics.widthPixels, maxDim) * blurScaleFactor).toInt().coerceAtLeast(64)
//            val targetH = (minOf(metrics.heightPixels, maxDim) * blurScaleFactor).toInt().coerceAtLeast(64)
//
//            val req = RequestOptions()
//                .transform(CenterCrop())
//                .override(targetW, targetH)
//                .downsample(DownsampleStrategy.CENTER_INSIDE)
//                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
//
//            val finalReq = if (usePlatformBlur) {
//                req
//            } else if (blurIntensity > 0) {
//                req.transform(
//                    CenterCrop(),
//                    BlurTransformation(this, blurIntensity, 1) {
//                        if (!isDestroyed && !isFinishing) {
//                            updateBackgroundProgress(BackgroundProgressPlugin.Stage.BLURRING)
//                        }
//                    }
//                )
//            } else {
//                req
//            }
//
//            val mainTarget = object : CustomTarget<Drawable>() {
//                override fun onResourceReady(
//                    resource: Drawable,
//                    transition: Transition<in Drawable>?
//                ) {
//                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.EXTRACTING_COLORS)
//                    val bitmap = (resource as? BitmapDrawable)?.bitmap
//
//                    if (bitmap != null) {
//                        Thread {
//                            ColorExtractor.extractColor(bitmap) { seedColor ->
//                                handler.post {
//                                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.APPLYING_THEME)
//
//                                    fontManager.setDynamicScheme(seedColor)
//                                    fontManager.setDynamicColorFromSeed(fontManager.getDynamicScheme().secondary)
//
//                                    var noiseColor = Color.WHITE
//                                    if (fontManager.getDynamicScheme() == null) {
//                                        noiseColor = getColor(R.color.md_theme_primary)
//                                    } else {
//                                        noiseColor = fontManager.getDynamicScheme()!!.primary
//                                    }
//
//                                    if (isAdvancedGraphicsEnabled) {
//                                        turbulenceOverlay.playAnimation(noiseColor) {}
//                                    }
//
//                                    backgroundImageView.setImageDrawable(resource)
//
//                                    val bgOffsetX = backgroundManager.getBgOffsetX()
//                                    val bgOffsetY = backgroundManager.getBgOffsetY()
//                                    val bgScale   = backgroundManager.getBgScale()
//
//                                    if (bgScale != 1f || bgOffsetX != 0f || bgOffsetY != 0f) {
//                                        backgroundImageView.scaleType = ImageView.ScaleType.MATRIX
//                                        backgroundImageView.post {
//                                            cropController.applyStoredTransform(bgOffsetX, bgOffsetY, bgScale)
//                                        }
//                                    } else {
//                                        backgroundImageView.scaleType = ImageView.ScaleType.CENTER_CROP
//                                    }
//
//                                    if (usePlatformBlur) {
//                                        try {
//                                            val radiusPx = (blurIntensity * blurScaleFactor).coerceAtLeast(1f)
//                                            val renderEffect = RenderEffect.createBlurEffect(
//                                                radiusPx,
//                                                radiusPx,
//                                                Shader.TileMode.CLAMP
//                                            )
//                                            backgroundImageView.setRenderEffect(renderEffect)
//                                        } catch (e: Throwable) { /* ignore */ }
//                                    } else {
//                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                                            backgroundImageView.setRenderEffect(null)
//                                        }
//                                    }
//
//                                    backgroundImageView.visibility = View.VISIBLE
//                                    val currentTargetMode = backgroundManager.getDimMode()
//                                    val currentTargetIntensity = backgroundManager.getDimIntensity()
//                                    val currentEffectiveIntensity = getEffectiveDimIntensity(currentTargetMode, currentTargetIntensity)
//                                    val finalZoom = calculateZoom(currentEffectiveIntensity)
//
//                                    backgroundImageView.scaleX = finalZoom + 0.4f
//                                    backgroundImageView.scaleY = finalZoom + 0.4f
//
//                                    fontManager.applyNightShiftTransition(
//                                        clockManager.getCurrentTime(),
//                                        dayTimeGetter,
//                                        true
//                                    )
//
//                                    onComplete?.invoke()
//
//                                    backgroundImageView.animate()
//                                        .scaleX(finalZoom)
//                                        .scaleY(finalZoom)
//                                        .alpha(1.0f)
//                                        .setDuration(700)
//                                        .setListener(object : AnimatorListenerAdapter() {
//                                            override fun onAnimationEnd(animation: Animator) {
//                                                updateBackgroundFilters()
//                                                handler.postDelayed({
//                                                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE)
//                                                }, 500)
//                                            }
//                                        }).start()
//                                }
//                            }
//                        }.start()
//                    } else {
//                        backgroundImageView.setImageDrawable(resource)
//                        backgroundImageView.visibility = View.VISIBLE
//                        onComplete?.invoke()
//                    }
//                }
//
//                override fun onLoadCleared(placeholder: Drawable?) {
//                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE)
//                }
//
//                override fun onLoadFailed(errorDrawable: Drawable?) {
//                    super.onLoadFailed(errorDrawable)
//                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE, "Failed to load")
//                    Logger.w("MainActivity") {"Glide failed to load background: $model"}
//                    handler.postDelayed({
//                        updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE)
//                    }, 2000)
//
//                    onComplete?.invoke()
//
//                    try {
//                        val savedUri = backgroundManager.getSavedBackgroundUri()
//                        if (model.toString() != savedUri) {
//                            restoreUserBackground(savedUri)
//                        }
//                    } catch (e: Exception) {
//                        Logger.e("MainActivity"){"Failed to restore user background with exception: ${e.message}"}
//                    }
//                }
//
//                override fun onLoadStarted(placeholder: Drawable?) {
//                    super.onLoadStarted(placeholder)
//                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.DOWNLOADING)
//                }
//            }
//
//            GlideApp.with(this)
//                .load(model)
//                .apply(finalReq)
//                .into(mainTarget)
//
//            if (isAdvancedGraphicsEnabled) {
//                applyEditModeBlurLayer(model)
//            } else {
//                editModeBlurLayer.setImageDrawable(null)
//                editModeBlurLayer.visibility = View.GONE
//            }
//
//        } catch (e: Exception) {
//            updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE, "Failed to load")
//            handler.postDelayed({
//                updateBackgroundProgress(BackgroundProgressPlugin.Stage.IDLE)
//            }, 2000)
//            Logger.e("MainActivity"){"loadBackgroundInternal failed with exception: ${e.message}"}
//            onComplete?.invoke()
//        }
//    }
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
            lastfmLayout.post {
                lastfmLayout.visibility = View.VISIBLE
                lastfmLayout.alpha = 1f
                lastfmLayout.setBackgroundResource(R.drawable.editable_border)
            }
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
        locationManager.loadCoordinates { lat, lon ->
            dayTimeGetter.fetch(lat, lon) {
                if (!hasCustomImageBackground) gradientManager.updateGradient()
                if (isNightShiftEnabled) fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, isNightShiftEnabled)
                if (dynamicBackgroundView.visibility == View.VISIBLE) updateBackgroundFilters()
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
        locationManager.onRequestPermissionsResult(requestCode, grantResults) { lat, lon ->
            dayTimeGetter.fetch(lat, lon) {
                if (!hasCustomImageBackground) gradientManager.updateGradient()
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
            if (disableLightSensor) {
                lightSensor?.let { sensorManager.unregisterListener(sensorEventListener, it) }
            } else {
                lightSensor?.let {
                    sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
            }

            // 4. Brightness Override
            if (lockBrightness) {
                val brightnessPct = prefs.getInt("power_saver_brightness_level", 1) / 100f
                val layoutParams = window.attributes
                layoutParams.screenBrightness = brightnessPct.coerceIn(0.01f, 1.0f)
                window.attributes = layoutParams
            } else {
                val layoutParams = window.attributes
                val savedBrightness = prefs.getInt("power_saver_brightness_level", 1) / 100f
                if (layoutParams.screenBrightness == savedBrightness.coerceIn(0.01f, 1.0f) || layoutParams.screenBrightness == minPowerSaveBrightness) {
                    layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = layoutParams
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
                lightSensor?.let {
                    sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
                val layoutParams = window.attributes
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = layoutParams

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

    private fun setupDashboard() {
        dashboardAdapter = DashboardAdapter(
            tiles = emptyList(),
            onTileClick = { tile -> dashboardManager?.handleTileClick(tile) },
            onToggleChange = { tile, state -> dashboardManager?.handleToggleChange(tile, state) },
            onSliderChange = { tile, value -> dashboardManager?.handleSliderChange(tile, value) },
            onMediaControl = { tile, action -> dashboardManager?.evaluateJs(tile.pluginId, "ClockDesk.controlMedia('$action')") }
        ).apply {
            onEditClick = { tile -> showWidgetEditorDialog(tile) }
            onDeleteClick = { tile ->
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Delete Widget")
                    .setMessage("Are you sure you want to delete this widget?")
                    .setPositiveButton("Yes") { _, _ ->
                        dashboardManager?.deletePlugin(tile.pluginId)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        dashboardManager = DashboardManager(this) { tiles ->
            runOnUiThread {
                dashboardAdapter?.tiles = tiles
                dashboardAdapter?.notifyDataSetChanged()
            }
        }
        
        dashboardManager?.adapter = dashboardAdapter
        
        dashboardRecyclerView.adapter = dashboardAdapter
        val gridLayoutManager = GridLayoutManager(this, 4)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return dashboardAdapter?.tiles?.getOrNull(position)?.span ?: 2
            }
        }
        dashboardRecyclerView.layoutManager = gridLayoutManager

        // Set up ItemTouchHelper for drag and drop reordering
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false
                
                dashboardAdapter?.tiles?.let { list ->
                    if (fromPos < list.size && toPos < list.size) {
                        val mutableList = list.toMutableList()
                        val item = mutableList.removeAt(fromPos)
                        mutableList.add(toPos, item)
                        dashboardAdapter?.tiles = mutableList
                        dashboardAdapter?.notifyItemMoved(fromPos, toPos)
                        saveTilesOrder(mutableList)
                    }
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean {
                return dashboardAdapter?.isEditMode == true
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    dashboardRecyclerView.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                dashboardRecyclerView.parent?.requestDisallowInterceptTouchEvent(false)
            }
        })
        itemTouchHelper.attachToRecyclerView(dashboardRecyclerView)

        updateDashboardGreeting()

        btnScenarioLightsOff.setOnClickListener {
            dashboardManager?.triggerMasterScenario("LIGHTS", "TURN_OFF")
        }
        btnScenarioMediaStop.setOnClickListener {
            dashboardManager?.triggerMasterScenario("MEDIA", "TURN_OFF")
            try {
                val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val componentName = ComponentName(this, ClockDeskMediaService::class.java)
                val controllers = mediaSessionManager.getActiveSessions(componentName)
                controllers.forEach { it.transportControls.pause() }
            } catch (e: Exception) {
                Logger.e("MainActivity") { "Failed to stop media sessions: ${e.message}" }
            }
        }

        // Configure edit buttons
        val btnToggleEdit = dashboardPage.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dashboard_toggle_edit)
        val btnAddWidget = dashboardPage.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dashboard_add_widget)
        val btnImportWidget = dashboardPage.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dashboard_import_widget)
        val btnRestoreDefaults = dashboardPage.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_dashboard_restore_defaults)

        btnToggleEdit?.setOnClickListener {
            val adapter = dashboardAdapter ?: return@setOnClickListener
            adapter.isEditMode = !adapter.isEditMode
            adapter.notifyDataSetChanged()
            
            val visibility = if (adapter.isEditMode) View.VISIBLE else View.GONE
            btnAddWidget?.visibility = visibility
            btnImportWidget?.visibility = visibility
            btnRestoreDefaults?.visibility = visibility
            
            if (adapter.isEditMode) {
                btnToggleEdit.setIconResource(R.drawable.ic_check)
            } else {
                btnToggleEdit.setIconResource(R.drawable.pencil_outline)
            }
        }

        btnAddWidget?.setOnClickListener {
            showWidgetTemplatesDialog()
        }

        btnImportWidget?.setOnClickListener {
            launchZipChooser()
        }

        btnRestoreDefaults?.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Restore Defaults")
                .setMessage("Are you sure you want to restore default widgets and delete your custom widgets?")
                .setPositiveButton("Yes") { _, _ ->
                    dashboardManager?.restoreDefaultPlugins()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun saveTilesOrder(tiles: List<DashboardTile>) {
        val orderArray = JSONArray()
        tiles.forEach { orderArray.put(it.id) }
        getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
            .edit()
            .putString("dashboard_tiles_order", orderArray.toString())
            .apply()
    }

    private fun launchZipChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed"))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Select Widget ZIP"), PICK_ZIP_REQUEST)
    }

    private fun showWidgetTemplatesDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_widget_templates, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        dialogView.findViewById<View>(R.id.card_template_weather).setOnClickListener {
            dialog.dismiss()
            val success = dashboardManager?.addBuiltInWidget("weather") ?: false
            if (success) {
                Toast.makeText(this, "Weather Widget restored successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to restore Weather Widget", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<View>(R.id.card_template_music).setOnClickListener {
            dialog.dismiss()
            val success = dashboardManager?.addBuiltInWidget("music") ?: false
            if (success) {
                Toast.makeText(this, "Music Player restored successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to restore Music Player", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<View>(R.id.card_template_switch).setOnClickListener {
            dialog.dismiss()
            showWidgetTemplateForm("SWITCH")
        }

        dialogView.findViewById<View>(R.id.card_template_slider).setOnClickListener {
            dialog.dismiss()
            showWidgetTemplateForm("SLIDER")
        }

        dialogView.findViewById<View>(R.id.card_template_monitor).setOnClickListener {
            dialog.dismiss()
            showWidgetTemplateForm("MONITOR")
        }

        dialogView.findViewById<View>(R.id.card_template_button).setOnClickListener {
            dialog.dismiss()
            showWidgetTemplateForm("BUTTON")
        }

        dialogView.findViewById<View>(R.id.card_template_zip).setOnClickListener {
            dialog.dismiss()
            launchZipChooser()
        }

        dialogView.findViewById<View>(R.id.card_template_advanced).setOnClickListener {
            dialog.dismiss()
            showWidgetEditorDialog(null)
        }

        dialog.show()
    }

    private fun showWidgetTemplateForm(templateType: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_widget_template_form, null)
        val textHeader = dialogView.findViewById<TextView>(R.id.text_form_header)
        val editTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.form_widget_title)
        val editSubtitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.form_widget_subtitle)
        val spinnerSpan = dialogView.findViewById<Spinner>(R.id.form_widget_span)
        val spinnerIcon = dialogView.findViewById<Spinner>(R.id.form_widget_icon_spinner)
        
        val layoutUrl = dialogView.findViewById<View>(R.id.layout_section_url)
        val editUrl = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.form_widget_url)
        
        val layoutMethod = dialogView.findViewById<View>(R.id.layout_section_method)
        val spinnerMethod = dialogView.findViewById<Spinner>(R.id.form_widget_method)
        
        val layoutJsonPath = dialogView.findViewById<View>(R.id.layout_section_json_path)
        val editJsonPath = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.form_widget_json_path)
        
        val layoutInterval = dialogView.findViewById<View>(R.id.layout_section_interval)
        val spinnerInterval = dialogView.findViewById<Spinner>(R.id.form_widget_interval)
        
        val layoutCategory = dialogView.findViewById<View>(R.id.layout_section_category)
        val editCategory = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.form_widget_category)

        // Populate spinners
        val spans = arrayOf("1", "2", "3", "4")
        spinnerSpan.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, spans)
        spinnerSpan.setSelection(1) // Default to 2 span

        val icons = arrayOf("ic_widgets_outline", "ic_dim", "ic_info_outline", "music_note", "ic_alarm", "ic_cog", "ic_palette_swatch", "ic_auto_awesome")
        spinnerIcon.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, icons)

        val methods = arrayOf("GET", "POST")
        spinnerMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods)

        val intervals = arrayOf("5 seconds", "30 seconds", "1 minute", "5 minutes", "15 minutes")
        spinnerInterval.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervals)
        spinnerInterval.setSelection(2) // Default to 1 minute

        // Configure visibility based on type
        textHeader.text = "Configure " + when (templateType) {
            "SWITCH" -> "Smart Switch"
            "SLIDER" -> "Smart Slider"
            "MONITOR" -> "Info Monitor"
            "BUTTON" -> "Quick Action Button"
            else -> "Widget"
        }

        // Show/hide sections
        when (templateType) {
            "SWITCH" -> {
                layoutUrl.visibility = View.VISIBLE
                layoutMethod.visibility = View.VISIBLE
                layoutJsonPath.visibility = View.GONE
                layoutInterval.visibility = View.GONE
                layoutCategory.visibility = View.VISIBLE
                editCategory.setText("LIGHTS")
                spinnerIcon.setSelection(icons.indexOf("ic_widgets_outline"))
            }
            "SLIDER" -> {
                layoutUrl.visibility = View.VISIBLE
                layoutMethod.visibility = View.GONE
                layoutJsonPath.visibility = View.GONE
                layoutInterval.visibility = View.GONE
                layoutCategory.visibility = View.VISIBLE
                editCategory.setText("MEDIA")
                spinnerIcon.setSelection(icons.indexOf("ic_dim"))
                editSubtitle.setText("Brightness: 50%")
            }
            "MONITOR" -> {
                layoutUrl.visibility = View.VISIBLE
                layoutMethod.visibility = View.GONE
                layoutJsonPath.visibility = View.VISIBLE
                layoutInterval.visibility = View.VISIBLE
                layoutCategory.visibility = View.VISIBLE
                editCategory.setText("SENSORS")
                spinnerIcon.setSelection(icons.indexOf("ic_info_outline"))
            }
            "BUTTON" -> {
                layoutUrl.visibility = View.VISIBLE
                layoutMethod.visibility = View.VISIBLE
                layoutJsonPath.visibility = View.GONE
                layoutInterval.visibility = View.GONE
                layoutCategory.visibility = View.VISIBLE
                editCategory.setText("TRIGGERS")
                spinnerIcon.setSelection(icons.indexOf("ic_auto_awesome"))
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("New Widget Setup")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val title = editTitle.text.toString().trim()
                val subtitle = editSubtitle.text.toString().trim()
                val span = spinnerSpan.selectedItem.toString().toInt()
                val icon = spinnerIcon.selectedItem.toString()
                val url = editUrl.text.toString().trim()
                val category = editCategory.text.toString().trim()
                
                if (title.isEmpty()) {
                    Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (url.isEmpty()) {
                    Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Generate code dynamically
                generateTemplateWidget(templateType, title, subtitle, span, icon, url, category, dialogView)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateTemplateWidget(
        templateType: String,
        title: String,
        subtitle: String,
        span: Int,
        icon: String,
        url: String,
        category: String,
        dialogView: View
    ) {
        val pluginId = "user_plugin_" + System.currentTimeMillis()
        val tileId = "tile_" + System.currentTimeMillis()
        
        val layoutObj = JSONObject()
        val tilesArray = JSONArray()
        val tileObj = JSONObject().apply {
            put("id", tileId)
            put("title", title)
            put("info", if (subtitle.isNotEmpty()) subtitle else "Ready")
            put("span", span)
            put("icon", icon)
            if (category.isNotEmpty()) put("deviceCategory", category)
        }

        var jsCode = ""

        when (templateType) {
            "SWITCH" -> {
                val method = dialogView.findViewById<Spinner>(R.id.form_widget_method).selectedItem.toString()
                tileObj.put("type", "TOGGLE")
                tileObj.put("state", false)
                tileObj.put("action", "onToggleChanged(state)")
                
                jsCode = """
                    var tileId = "$tileId";
                    var url = "$url";
                    var method = "$method";
                    
                    function init() {
                        if (method === "GET") {
                            ClockDesk.fetch(url)
                                .then(function(res) {
                                    try {
                                        var data = JSON.parse(res);
                                        var isOne = data.state === true || data.status === "on" || data.status === "active";
                                        var update = {};
                                        update[tileId] = { "state": isOne };
                                        ClockDesk.updateState(update);
                                    } catch(e) {}
                                });
                        }
                    }
                    
                    function onToggleChanged(state) {
                        var options = {
                            method: method,
                            headers: { "Content-Type": "application/json" }
                        };
                        var targetUrl = url;
                        if (method === "GET") {
                            targetUrl = url.replace("state", state.toString());
                        } else {
                            options.body = JSON.stringify({ state: state });
                        }
                        
                        var update = {};
                        update[tileId] = { "state": state };
                        ClockDesk.updateState(update);
                        
                        ClockDesk.fetch(targetUrl, options)
                            .catch(function(err) {
                                var revert = {};
                                revert[tileId] = { "state": !state };
                                ClockDesk.updateState(revert);
                            });
                    }
                """.trimIndent()
            }
            "SLIDER" -> {
                tileObj.put("type", "SLIDER")
                tileObj.put("value", 50)
                tileObj.put("action", "onSliderChanged(value)")
                
                jsCode = """
                    var tileId = "$tileId";
                    var url = "$url";
                    
                    function init() {}
                    
                    function onSliderChanged(value) {
                        var targetUrl = url.replace("value", value.toString());
                        var update = {};
                        update[tileId] = { "value": value, "info": "Value: " + Math.round(value) + "%" };
                        ClockDesk.updateState(update);
                        
                        ClockDesk.fetch(targetUrl, { method: "GET" })
                            .catch(function(err) {});
                    }
                """.trimIndent()
            }
            "MONITOR" -> {
                val jsonPath = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.form_widget_json_path).text.toString().trim()
                val intervalStr = dialogView.findViewById<Spinner>(R.id.form_widget_interval).selectedItem.toString()
                val intervalMs = when (intervalStr) {
                    "5 seconds" -> 5000
                    "30 seconds" -> 30000
                    "1 minute" -> 60000
                    "5 minutes" -> 300000
                    "15 minutes" -> 900000
                    else -> 60000
                }
                tileObj.put("type", "INFO")
                
                jsCode = """
                    var tileId = "$tileId";
                    var url = "$url";
                    var jsonPath = "$jsonPath";
                    var intervalMs = $intervalMs;
                    
                    function init() {
                        updateInfo();
                        if (intervalMs > 0) {
                            setInterval(updateInfo, intervalMs);
                        }
                    }
                    
                    function updateInfo() {
                        ClockDesk.fetch(url)
                            .then(function(res) {
                                var valueStr = res;
                                try {
                                    var data = JSON.parse(res);
                                    if (jsonPath) {
                                        var parts = jsonPath.split('.');
                                        var obj = data;
                                        for (var i = 0; i < parts.length; i++) {
                                            if (obj[parts[i]] !== undefined) {
                                                obj = obj[parts[i]];
                                            } else {
                                                obj = null;
                                                break;
                                            }
                                        }
                                        if (obj !== null) {
                                            valueStr = obj.toString();
                                        }
                                    }
                                } catch(e) {}
                                
                                var update = {};
                                update[tileId] = { "info": valueStr };
                                ClockDesk.updateState(update);
                            })
                            .catch(function(err) {
                                var update = {};
                                update[tileId] = { "info": "Err: " + err.message };
                                ClockDesk.updateState(update);
                            });
                    }
                """.trimIndent()
            }
            "BUTTON" -> {
                val method = dialogView.findViewById<Spinner>(R.id.form_widget_method).selectedItem.toString()
                tileObj.put("type", "BUTTON")
                tileObj.put("action", "onButtonClick()")
                
                jsCode = """
                    var tileId = "$tileId";
                    var url = "$url";
                    var method = "$method";
                    var defaultSubtitle = "$subtitle";
                    
                    function init() {}
                    
                    function onButtonClick() {
                        var options = {
                            method: method,
                            headers: { "Content-Type": "application/json" }
                        };
                        
                        var update = {};
                        update[tileId] = { "info": "Sending..." };
                        ClockDesk.updateState(update);
                        
                        ClockDesk.fetch(url, options)
                            .then(function() {
                                var update = {};
                                update[tileId] = { "info": "Success" };
                                ClockDesk.updateState(update);
                                setTimeout(function() {
                                    var reset = {};
                                    reset[tileId] = { "info": defaultSubtitle || "Ready" };
                                    ClockDesk.updateState(reset);
                                }, 1500);
                            })
                            .catch(function(err) {
                                var update = {};
                                update[tileId] = { "info": "Failed" };
                                ClockDesk.updateState(update);
                                setTimeout(function() {
                                    var reset = {};
                                    reset[tileId] = { "info": defaultSubtitle || "Ready" };
                                    ClockDesk.updateState(reset);
                                }, 1500);
                            });
                    }
                """.trimIndent()
            }
        }

        tilesArray.put(tileObj)
        layoutObj.put("tiles", tilesArray)

        dashboardManager?.createOrUpdatePlugin(pluginId, layoutObj.toString(4), jsCode)
        Toast.makeText(this, "Widget created successfully", Toast.LENGTH_SHORT).show()
    }

    private fun showWidgetEditorDialog(tile: DashboardTile?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_widget_editor, null)
        val editTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_widget_title)
        val editSubtitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_widget_subtitle)
        val editIcon = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_widget_icon)
        val editIconUrl = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_widget_icon_url)
        val editCategory = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_widget_category)
        val editJsCode = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_widget_js_code)
        
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinner_widget_type)
        val spinnerSpan = dialogView.findViewById<Spinner>(R.id.spinner_widget_span)

        val typesList = mutableListOf("INFO", "TOGGLE", "SLIDER", "BUTTON")
        if (tile != null && !typesList.contains(tile.type.uppercase())) {
            typesList.add(tile.type.uppercase())
        }
        val types = typesList.toTypedArray()
        val spans = arrayOf("1", "2", "3", "4")

        spinnerType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        spinnerSpan.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, spans)

        val pluginId = tile?.pluginId ?: "user_plugin_" + System.currentTimeMillis()
        if (tile != null) {
            val displayTitle = if (tile.title.isNullOrEmpty()) {
                when (tile.type.uppercase()) {
                    "WEATHER_FORECAST" -> "Weather"
                    "MEDIA_PLAYER" -> "Music Player"
                    else -> ""
                }
            } else {
                tile.title
            }
            editTitle.setText(displayTitle)
            editSubtitle.setText(tile.info)
            editIcon.setText(tile.icon)
            editIconUrl.setText(tile.iconUrl)
            editCategory.setText(tile.deviceCategory)
            
            val typeIndex = types.indexOf(tile.type.uppercase())
            if (typeIndex >= 0) spinnerType.setSelection(typeIndex)
            
            val spanIndex = spans.indexOf(tile.span.toString())
            if (spanIndex >= 0) spinnerSpan.setSelection(spanIndex)

            try {
                val dir = File(filesDir, "plugins/$pluginId")
                if (File(dir, "logic.js").exists()) {
                    editJsCode.setText(File(dir, "logic.js").readText())
                }
            } catch (e: Exception) {
                Logger.e("MainActivity") { "Failed to read logic.js: ${e.message}" }
            }
        } else {
            val defaultJs = """
                // JavaScript Logic for your widget
                var state = {
                    state: false,
                    value: 50
                };

                function init() {
                    ClockDesk.updateState(JSON.stringify(state));
                }

                function onClick() {
                    state.state = !state.state;
                    ClockDesk.updateState(JSON.stringify(state));
                }

                function onSliderChange(value) {
                    state.value = value;
                    ClockDesk.updateState(JSON.stringify(state));
                }
            """.trimIndent()
            editJsCode.setText(defaultJs)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (tile == null) "Add Widget" else "Edit Widget")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val title = editTitle.text.toString().trim()
                val subtitle = editSubtitle.text.toString().trim()
                val icon = editIcon.text.toString().trim()
                val iconUrl = editIconUrl.text.toString().trim()
                val category = editCategory.text.toString().trim()
                val jsCode = editJsCode.text.toString().trim()
                val type = spinnerType.selectedItem.toString()
                val span = spinnerSpan.selectedItem.toString().toInt()

                if (title.isEmpty()) {
                    Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val tileId = tile?.id ?: "tile_" + System.currentTimeMillis()
                
                var existingLayout: JSONObject? = null
                if (tile != null) {
                    try {
                        val file = File(filesDir, "plugins/$pluginId/layout.json")
                        if (file.exists()) {
                            existingLayout = JSONObject(file.readText())
                        }
                    } catch (e: Exception) {
                        Logger.e("MainActivity") { "Failed to read layout.json: ${e.message}" }
                    }
                }

                val layoutObj = existingLayout ?: JSONObject()
                val tilesArray = layoutObj.optJSONArray("tiles") ?: JSONArray()
                
                var tileFound = false
                for (i in 0 until tilesArray.length()) {
                    val tObj = tilesArray.getJSONObject(i)
                    if (tObj.getString("id") == tileId) {
                        tObj.put("title", title)
                        if (subtitle.isNotEmpty()) tObj.put("info", subtitle) else tObj.remove("info")
                        tObj.put("span", span)
                        if (icon.isNotEmpty()) tObj.put("icon", icon) else tObj.remove("icon")
                        if (iconUrl.isNotEmpty()) tObj.put("iconUrl", iconUrl) else tObj.remove("iconUrl")
                        if (category.isNotEmpty()) tObj.put("deviceCategory", category) else tObj.remove("deviceCategory")
                        
                        if (type != "WEATHER_FORECAST" && type != "MEDIA_PLAYER" && type != "NATIVE_APK") {
                            tObj.put("type", type)
                        }
                        tileFound = true
                        break
                    }
                }
                
                if (!tileFound) {
                    val tileObj = JSONObject().apply {
                        put("id", tileId)
                        put("type", type)
                        put("title", title)
                        if (subtitle.isNotEmpty()) put("info", subtitle)
                        put("span", span)
                        if (icon.isNotEmpty()) put("icon", icon)
                        if (iconUrl.isNotEmpty()) put("iconUrl", iconUrl)
                        if (category.isNotEmpty()) put("deviceCategory", category)
                        put("state", tile?.state ?: false)
                        put("value", tile?.value ?: 50f)
                    }
                    tilesArray.put(tileObj)
                    layoutObj.put("tiles", tilesArray)
                }

                dashboardManager?.createOrUpdatePlugin(pluginId, layoutObj.toString(4), jsCode)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateDashboardGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
        dashboardGreeting.text = greeting
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    override fun <T : View> findViewById(id: Int): T {
        val view = super.findViewById<View>(id)
        if (view != null) return view as T
        if (::mainPage.isInitialized) {
            val v = mainPage.findViewById<View>(id)
            if (v != null) return v as T
        }
        if (::dashboardPage.isInitialized) {
            val v = dashboardPage.findViewById<View>(id)
            if (v != null) return v as T
        }
        return super.findViewById(id)
    }

    private class MainPagerAdapter(
        private val dashboardView: View,
        private val mainView: View
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<MainPagerAdapter.PageViewHolder>() {

        class PageViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView)

        override fun getItemCount(): Int = 2

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PageViewHolder {
            val view = if (viewType == 0) dashboardView else mainView
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {}
    }
}